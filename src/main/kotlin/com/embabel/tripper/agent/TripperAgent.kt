/*
 * Copyright 2024-2025 Embabel Software, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.embabel.tripper.agent

import com.embabel.agent.api.annotation.*
import com.embabel.agent.api.common.Actor
import com.embabel.agent.api.common.OperationContext
import com.embabel.agent.api.common.PromptRunner
import com.embabel.agent.api.common.SomeOf
import com.embabel.agent.api.common.create
import com.embabel.agent.core.CoreToolGroups
import com.embabel.agent.core.last
import com.embabel.agent.domain.library.InternetResource
import com.embabel.agent.prompt.ResponseFormat
import com.embabel.agent.prompt.element.ToolCallControl
import com.embabel.agent.prompt.persona.Persona
import com.embabel.agent.prompt.persona.RoleGoalBackstory
import com.embabel.common.ai.model.LlmOptions
import com.embabel.common.util.StringTransformer
import com.embabel.tripper.BraveImageSearchService
import com.embabel.tripper.config.ToolsConfig
import com.embabel.tripper.observability.AgentRunTraceService
import com.embabel.tripper.rag.TravelKnowledgeContext
import com.embabel.tripper.rag.TravelKnowledgeService
import com.embabel.tripper.safety.ContentSafetyService
import com.embabel.tripper.safety.PlanHtmlSanitizer
import com.embabel.tripper.safety.ToolSafetyService
import com.embabel.tripper.util.ImageChecker
import com.embabel.tripper.verification.ItineraryDay
import com.embabel.tripper.verification.ItineraryLink
import com.embabel.tripper.verification.ItineraryStay
import com.embabel.tripper.verification.ItineraryVerificationRequest
import com.embabel.tripper.verification.ItineraryVerificationService
import org.slf4j.LoggerFactory
import org.springframework.boot.context.properties.ConfigurationProperties
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import java.util.concurrent.CancellationException
import java.util.concurrent.atomic.AtomicInteger

@ConfigurationProperties("embabel.tripper")
data class TripperConfig(
    val wordCount: Int = 700,
    val imageWidth: Int = 800,
    val planner: Actor<Persona>,
    val researcher: Actor<RoleGoalBackstory>,
    // Cap tool calls per LLM step to bound token cost and latency (each tool result is fed
    // back into the model context). Override via embabel.tripper.tool-call-control.tool-calls.
    val toolCallControl: ToolCallControl = ToolCallControl(4),
    val thinkerLlm: LlmOptions,
    val maxConcurrency: Int = 12,
    // Points of interest scale with trip length (pointsOfInterestPerDay * days), since research
    // fans out one parallel LLM call per POI. maxPointsOfInterest is a hard ceiling so very long
    // trips cannot blow up cost. Tune both via embabel.tripper.* .
    val pointsOfInterestPerDay: Int = 2,
    val maxPointsOfInterest: Int = 10,
    // When the user picks Chinese, the agent runs on these domestic (Moonshot/Kimi) models
    // instead of the overseas defaults above — domestic models are directly reachable (no EOF).
    val cnThinkerModel: String = "moonshot-v1-128k",
    // moonshot-v1-128k (non-thinking) for the planner. The plan is generated in two calls — a
    // short structured metadata object plus a plain-text HTML body — so the model never has to
    // emit HTML inside JSON (which Moonshot does unreliably) and we avoid k2.5's streaming format.
    val cnPlannerModel: String = "moonshot-v1-128k",
    val cnResearcherModel: String = "moonshot-v1-32k",
    // Per-POI research is summarized (truncated) before it is handed to the planner, so the
    // proposal prompt stays small — cheaper, faster, less likely to overflow or be ignored.
    val researchSummaryCharacters: Int = 500,
    // Rough upper bound shown in the cost-confirmation prompt (USD). Measured end-to-end runs land
    // around $0.45 (Chinese/Kimi) to $0.52 (English/Claude); 0.6 is an honest ceiling. Tune here
    // rather than hardcoding it in the confirmation text.
    val estimatedMaxCostUsd: Double = 0.6,
    // Model used by the LLM-as-judge in the evaluation harness (see PlanJudgeAgent). Ideally set to
    // a model DIFFERENT from the planner to reduce self-preference bias; using the strongest model
    // available matters more than differing, so the default mirrors the planner.
    val judgeModel: String = "claude-sonnet-4-5",
)

private const val WEATHER_TOOLS = "weather"

/**
 * Overall flow:
 * 1. Lookup travelers based on a travel brief. Brief may be about exploring a location or a journey.
 * 2. Find points of interest based on travel brief, travelers and mapping data.
 * 3. Research each point of interest to gather detailed information.
 */
@Agent(description = "Make a detailed travel plan")
class TripperAgent(
    private val config: TripperConfig,
    private val braveImageSearch: BraveImageSearchService,
    private val travelKnowledgeService: TravelKnowledgeService,
    private val itineraryVerificationService: ItineraryVerificationService,
    private val agentRunTraceService: AgentRunTraceService,
    private val contentSafetyService: ContentSafetyService,
    private val planHtmlSanitizer: PlanHtmlSanitizer,
    private val toolSafetyService: ToolSafetyService,
) {

    private val logger = LoggerFactory.getLogger(TripperAgent::class.java)

    /**
     * This object being bound to the blackboard represents acceptance
     * of the cost of calculating the plan
     */
    object AcceptanceOfCost

    @Action
    fun confirmExpensiveOperation(
        travelBrief: JourneyTravelBrief,
        travelers: Travelers,
        context: OperationContext
    ): AcceptanceOfCost {
        return tracedAction(
            context = context,
            actionName = "confirmExpensiveOperation",
            inputSummary = "travelers=${travelers.travelers.size}, route=${travelBrief.from}->${travelBrief.to}, dailyBudget=${travelBrief.dailyBudget}",
            outputSummary = { "accepted" },
        ) {
            // Confirmation is needed if we came through the MCP route
            val confirmationNeeded = context.last<TravelersAndBrief>() != null
            if (!confirmationNeeded) {
                // Take it as a given
                return@tracedAction AcceptanceOfCost
            }
            // Otherwise, explicitly ask the user for confirmation. The ceiling is config-driven
            // (estimatedMaxCostUsd) and the wording follows the user's chosen language.
            val names = travelers.travelers.joinToString(separator = " and ") { it.name }
            val estimate = "%.2f".format(config.estimatedMaxCostUsd)
            val message = if (isChinese(travelBrief)) {
                "继续吗?为 $names 生成旅行计划预计最多花费约 \$$estimate。"
            } else {
                "Go ahead? Building a travel plan for $names will cost up to about \$$estimate"
            }
            confirm(AcceptanceOfCost, message)
        }
    }


    @Action
    fun retrieveTravelKnowledge(
        travelBrief: JourneyTravelBrief,
        travelers: Travelers,
        context: OperationContext,
    ): TravelKnowledgeContext {
        return tracedAction(
            context = context,
            actionName = "retrieveTravelKnowledge",
            inputSummary = "briefCharacters=${travelBrief.brief.length}, travelers=${travelers.travelers.size}",
            outputSummary = { "hits=${it.hits.size}" },
        ) {
            val query = buildString {
                append("${travelBrief.from} ${travelBrief.to} ${travelBrief.transportPreference} ")
                append("${travelBrief.departureDate} ${travelBrief.returnDate} ")
                append(travelBrief.brief)
                append(' ')
                append(travelers.travelers.joinToString(" ") { "${it.name} ${it.about}" })
            }
            travelKnowledgeService.retrieveForQuery(query, 6)
        }
    }

    @Action
    fun findPointsOfInterest(
        travelBrief: JourneyTravelBrief,
        travelers: Travelers,
        knowledgeContext: TravelKnowledgeContext,
        context: OperationContext,
    ): ItineraryIdeas {
        val toolNames = listOf(CoreToolGroups.WEB, CoreToolGroups.MAPS, CoreToolGroups.MATH, WEATHER_TOOLS)
        val thinkerLlm = if (isChinese(travelBrief)) LlmOptions.withModel(config.cnThinkerModel) else config.thinkerLlm
        val tripDays = (ChronoUnit.DAYS.between(travelBrief.departureDate, travelBrief.returnDate) + 1)
            .coerceAtLeast(1)
        val maxPois = (tripDays * config.pointsOfInterestPerDay)
            .coerceAtMost(config.maxPointsOfInterest.toLong())
            .toInt()
        val prompt = """
                ${toolSafetyService.promptPolicy("findPointsOfInterest", toolNames)}

                ${languageInstruction(travelBrief)}

                Consider the following travel brief for a journey from ${travelBrief.from} to ${travelBrief.to}.
                ${travelBrief.contribution()}
                Find at most $maxPois points of interest that are relevant to the travel brief and travelers.
                Use mapping tools to consider appropriate order and put a rough date
                range for each point of interest.
                Consider likely weather
                
                Consider this user-provided travel knowledge when relevant:
                ${knowledgeContext.contribution()}
            """.trimIndent()
        return tracedAction(
            context = context,
            actionName = "findPointsOfInterest",
            inputSummary = "route=${travelBrief.from}->${travelBrief.to}, knowledgeHits=${knowledgeContext.hits.size}",
            modelName = modelName(thinkerLlm),
            promptCharacters = prompt.length,
            toolNames = toolNames,
            outputSummary = { "pointsOfInterest=${it.pointsOfInterest.size}" },
            completionCharacters = { it.pointsOfInterest.sumOf { poi -> poi.name.length + poi.description.length } },
        ) {
            context.ai()
                .withLlm(thinkerLlm)
                .withPromptElements(
                    config.planner,
                    travelers,
                    config.toolCallControl,
                ).withTools(
                    CoreToolGroups.WEB,
                    CoreToolGroups.MAPS,
                    CoreToolGroups.MATH,
                    WEATHER_TOOLS,
                )
                .create(
                    prompt = prompt,
                )
        }
    }

    @Action
    fun researchPointsOfInterest(
        travelBrief: JourneyTravelBrief,
        travelers: Travelers,
        knowledgeContext: TravelKnowledgeContext,
        itineraryIdeas: ItineraryIdeas,
        confirmation: AcceptanceOfCost,
        context: OperationContext,
    ): PointOfInterestFindings {
        val toolNames = listOf(CoreToolGroups.WEB, CoreToolGroups.BROWSER_AUTOMATION, WEATHER_TOOLS, "braveImageSearch")
        val estimatedPromptCharacters = itineraryIdeas.pointsOfInterest.sumOf {
            520 + travelBrief.brief.length + it.name.length + it.description.length +
                    it.location.length + knowledgeContext.contribution().length
        }
        return tracedAction(
            context = context,
            actionName = "researchPointsOfInterest",
            inputSummary = "pointsOfInterest=${itineraryIdeas.pointsOfInterest.size}, maxConcurrency=${config.maxConcurrency}",
            modelName = "researcher",
            promptCharacters = estimatedPromptCharacters,
            toolNames = toolNames,
            outputSummary = { "researched=${it.pointsOfInterest.size}" },
            completionCharacters = { it.pointsOfInterest.sumOf { finding -> finding.research.length } },
        ) {
            logger.info(
                "Researching {} points of interest: {}",
                itineraryIdeas.pointsOfInterest.size,
                itineraryIdeas.pointsOfInterest.sortedBy { it.name }.joinToString { it.name },
            )
            val promptRunner = config.researcher.promptRunner(context)
                .withLanguageModel(travelBrief, config.cnResearcherModel)
                .withPromptElements(travelers, config.toolCallControl)
                .withTools(
                    CoreToolGroups.WEB,
                    CoreToolGroups.BROWSER_AUTOMATION,
                    WEATHER_TOOLS,
                )
                .withToolObject(braveImageSearch)
            val researchFailures = AtomicInteger()
            val poiFindings = context.parallelMap(
                itineraryIdeas.pointsOfInterest,
                maxConcurrency = config.maxConcurrency,
            ) { poi ->
                try {
                    val rpi = promptRunner.create<ResearchedPointOfInterest>(
                        prompt = """
                ${toolSafetyService.promptPolicy("researchPointsOfInterest", toolNames)}

                ${languageInstruction(travelBrief)}

                Research the following point of interest.
                Consider interesting stories about art and culture and famous people.
                Your audience: ${travelBrief.brief}
                Dates to consider: ${travelBrief.departureDate} to ${travelBrief.returnDate}
                If any particularly important events are happening here during this time, mention them
                and list specific dates.
                Also consider likely weather.
                <point-of-interest-to-research>
                ${poi.name}
                ${poi.description}
                ${poi.location}
                Date: from ${poi.fromDate} to: ${poi.toDate}
                </point-of-interest-to-research>
                Use the image search tool to find images of the point of interest.
                
                User-provided travel knowledge that may be relevant:
                ${knowledgeContext.contribution()}
            """.trimIndent(),
                    )
                    // Force the correct POI even if the model dropped it from its JSON.
                    rpi.copy(pointOfInterest = poi)
                } catch (ex: CancellationException) {
                    // Never swallow cancellation: parallelMap must keep its cancel semantics.
                    throw ex
                } catch (ex: RuntimeException) {
                    researchFailures.incrementAndGet()
                    logger.warn(
                        "Falling back to brief-only research for point of interest {} after research failure: {}",
                        poi.name,
                        ex.message,
                    )
                    ResearchedPointOfInterest(
                        pointOfInterest = poi,
                        research = """
                            Live research for this point was unavailable after retries.
                            Use the point name, description, location, dates, traveler preferences,
                            and general travel knowledge only. Avoid unsupported claims about events,
                            opening hours, prices, or weather.
                        """.trimIndent(),
                    )
                }
            }
            // Every POI failing points at a systemic problem (auth, quota, connectivity), not
            // flaky research. Fail fast rather than paying the planner to write from nothing.
            if (itineraryIdeas.pointsOfInterest.isNotEmpty() &&
                researchFailures.get() == itineraryIdeas.pointsOfInterest.size
            ) {
                throw IllegalStateException(
                    "Research failed for all ${itineraryIdeas.pointsOfInterest.size} points of interest",
                )
            }
            PointOfInterestFindings(
                pointsOfInterest = poiFindings,
            )
        }
    }

    /**
     * Use a good LLM to build a plan based on research.
     */
    @Action
    fun proposeTravelPlan(
        travelBrief: JourneyTravelBrief,
        travelers: Travelers,
        knowledgeContext: TravelKnowledgeContext,
        poiFindings: PointOfInterestFindings,
        context: OperationContext,
    ): ProposedTravelPlan {
        // No tools here: the planner only writes from the research already gathered.
        val toolNames = emptyList<String>()
        // Shared, compressed point-of-interest research used by both planner calls.
        val poiSummary = poiFindings.pointsOfInterest.joinToString("\n\n") { finding ->
            val research = finding.research.take(config.researchSummaryCharacters)
            val links = finding.links.take(2).joinToString("; ") { "${it.summary}: ${it.url}" }
            val images = finding.imageLinks.take(2).joinToString("; ") { it.url }
            """
                ${finding.pointOfInterest.name} (${finding.pointOfInterest.location})
                $research
                Links: $links
                Image URLs (embed only these as images): $images
            """.trimIndent()
        }

        val structurePrompt = """
                ${languageInstruction(travelBrief)}

                From the travel brief and researched points of interest below, produce the plan's
                STRUCTURE ONLY (no prose, no HTML):
                - a brief, catchy title (no dates)
                - days: one entry for EVERY date from ${travelBrief.departureDate} to ${travelBrief.returnDate},
                  each with locationAndCountry in Google Maps friendly Latin form (e.g. Dijon,+France).
                  Repeat the same location for consecutive days in the same town. Minimize travel time.
                - imageLinks / videoLinks / pageLinks: ONLY links the researchers provided below
                - countriesVisited

                <brief>${travelBrief.contribution()}</brief>

                Points of interest:
                $poiSummary
            """.trimIndent()

        val htmlPromptPrefix = """
                ${languageInstruction(travelBrief)}

                Write a detailed, engaging travel itinerary in HTML, ${config.wordCount} words or less.
                Use exactly this day-by-day route (do not change locations or dates):
            """.trimIndent()

        return tracedAction(
            context = context,
            actionName = "proposeTravelPlan",
            inputSummary = "poiFindings=${poiFindings.pointsOfInterest.size}, knowledgeHits=${knowledgeContext.hits.size}",
            modelName = "planner",
            promptCharacters = structurePrompt.length,
            toolNames = toolNames,
            outputSummary = { "title=${it.title}, days=${it.days.size}, links=${it.pageLinks.size + it.imageLinks.size + it.videoLinks.size}" },
            completionCharacters = { it.plan.length },
        ) {
            val runner = config.planner.promptRunner(context)
                .withLanguageModel(travelBrief, config.cnPlannerModel)
                .withPromptElements(travelers, config.toolCallControl)

            // Call 1: small, JSON-friendly structured metadata (no HTML inside JSON).
            val meta = try {
                runner.create<ProposedTravelPlanMeta>(prompt = structurePrompt)
            } catch (ex: RuntimeException) {
                logger.warn(
                    "Falling back to deterministic plan structure after planner metadata failure: {}",
                    ex.message,
                )
                fallbackPlanMeta(travelBrief, poiFindings)
            }
            // Guarantee full date coverage in code so a model that omits dates can't trip DATE_GAP.
            val days = completeDays(
                meta.days, travelBrief.departureDate, travelBrief.returnDate, travelBrief.to,
            )

            // Call 2: the long HTML body as plain text (no JSON escaping to get wrong).
            val planHtml = try {
                runner.withPromptElements(ResponseFormat.HTML).generateText(
                    """
                $htmlPromptPrefix
                ${days.joinToString("\n") { "${it.date}: ${it.locationAndCountry}" }}

                Start headings at <h4>, use paragraphs and unordered lists. Recount at least one
                interesting story about a famous person associated with an area. Embed images only
                from the researcher-provided URLs below, max width ${config.imageWidth}px, each with
                an informative caption and alt text. If user knowledge influences a recommendation,
                cite it inline using [KB:<citationId>] exactly as provided.

                User-provided travel knowledge:
                ${knowledgeContext.contribution()}

                Points of interest research:
                $poiSummary
                """.trimIndent()
                )
            } catch (ex: RuntimeException) {
                logger.warn(
                    "Falling back to deterministic HTML plan after planner text failure: {}",
                    ex.message,
                )
                fallbackPlanHtml(travelBrief, days, poiFindings, knowledgeContext)
            }

            ProposedTravelPlan(
                title = meta.title,
                plan = planHtml,
                days = days,
                imageLinks = meta.imageLinks,
                videoLinks = meta.videoLinks,
                pageLinks = meta.pageLinks,
                countriesVisited = meta.countriesVisited,
            )
        }
    }

    @Action
    fun verifyAndRepairTravelPlan(
        travelBrief: JourneyTravelBrief,
        travelers: Travelers,
        knowledgeContext: TravelKnowledgeContext,
        poiFindings: PointOfInterestFindings,
        proposedPlan: ProposedTravelPlan,
        context: OperationContext,
    ): VerifiedTravelPlanProposal {
        // No tools here: like proposeTravelPlan, the repair only rewrites from existing research,
        // and the two-call split (structure JSON + plain-text HTML) keeps it reliable on domestic
        // OpenAI-compatible models that produce HTML-in-JSON unreliably.
        val toolNames = emptyList<String>()
        return tracedAction(
            context = context,
            actionName = "verifyAndRepairTravelPlan",
            inputSummary = "days=${proposedPlan.days.size}, links=${proposedPlan.pageLinks.size + proposedPlan.imageLinks.size + proposedPlan.videoLinks.size}",
            modelName = "planner",
            promptCharacters = proposedPlan.plan.length,
            toolNames = toolNames,
            outputSummary = { "status=${it.verificationResult.status}, repaired=${it.isRepaired()}, errors=${it.verificationResult.errorCount}" },
            completionCharacters = { it.proposal.plan.length },
        ) {
            val verificationRequest = verificationRequest(
                brief = travelBrief,
                plan = proposedPlan,
                stays = emptyList(),
            )
            val verification = itineraryVerificationService.verifyProposal(verificationRequest)
            if (!verification.isHasErrors()) {
                return@tracedAction VerifiedTravelPlanProposal(proposedPlan, verification)
            }

            logger.warn(
                "Repairing travel plan after verifier found {} error(s): {}",
                verification.errorCount,
                verification.issues.joinToString { "${it.category}:${it.message}" },
            )

            val repairPoiSummary = poiFindings.pointsOfInterest.joinToString("\n") {
                """
                ${it.pointOfInterest.name}
                ${it.research}
                ${it.links.joinToString { link -> "${link.url}: ${link.summary}" }}
            """.trimIndent()
            }

            // Context shared by both repair calls. Keeping it identical across the structure and
            // HTML passes ensures they describe the same repaired trip.
            val repairContext = """
                ${languageInstruction(travelBrief)}

                The itinerary verifier found blocking issues in the proposed travel plan. Repair it
                before it is shown to the user. Keep the user's trip intent, travelers, destination,
                date range and style; preserve still-valid recommendations and citations.

                <brief>${travelBrief.contribution()}</brief>

                User-provided travel knowledge:
                ${knowledgeContext.contribution()}

                Structured verifier issues:
                ${verification.contribution()}

                Original days:
                ${proposedPlan.days.joinToString("\n") { "${it.date}: ${it.locationAndCountry}" }}

                Relevant point-of-interest research:
                $repairPoiSummary
            """.trimIndent()

            val runner = config.planner.promptRunner(context)
                .withLanguageModel(travelBrief, config.cnPlannerModel)
                .withPromptElements(travelers, config.toolCallControl)

            // Call 1: repaired STRUCTURE only (no HTML inside JSON) — same split as proposeTravelPlan.
            val repairedMeta = runner.create<ProposedTravelPlanMeta>(
                prompt = """
                $repairContext

                Produce the repaired plan's STRUCTURE ONLY (no prose, no HTML):
                - a brief, catchy title (no dates)
                - days: one entry for EVERY date from ${travelBrief.departureDate} to ${travelBrief.returnDate},
                  each with a non-empty locationAndCountry in Google Maps friendly Latin form (e.g. Dijon,+France).
                  Repeat the same location for consecutive days in the same town. Minimize travel time.
                - imageLinks / videoLinks / pageLinks: drop invalid URLs, keep only valid ones
                - countriesVisited
            """.trimIndent()
            )
            // Guarantee full date coverage in code so a model that omits dates can't re-trip DATE_GAP.
            val repairedDays = completeDays(
                repairedMeta.days, travelBrief.departureDate, travelBrief.returnDate, travelBrief.to,
            )

            // Call 2: repaired HTML body as plain text (no JSON escaping to get wrong).
            val repairedHtml = runner.withPromptElements(ResponseFormat.HTML).generateText(
                """
                $repairContext

                Rewrite the travel itinerary in HTML to fix the issues above, ${config.wordCount} words or less.
                Use exactly this day-by-day route (do not change locations or dates):
                ${repairedDays.joinToString("\n") { "${it.date}: ${it.locationAndCountry}" }}

                Start headings at <h4>, use paragraphs and unordered lists. Remove or replace invalid URLs.
                If user knowledge influences a recommendation, cite it inline using [KB:<citationId>] exactly as provided.

                Original plan to repair (do not restate verbatim):
                ${proposedPlan.plan}
            """.trimIndent()
            )

            val repairedPlan = ProposedTravelPlan(
                title = repairedMeta.title,
                plan = repairedHtml,
                days = repairedDays,
                imageLinks = repairedMeta.imageLinks,
                videoLinks = repairedMeta.videoLinks,
                pageLinks = repairedMeta.pageLinks,
                countriesVisited = repairedMeta.countriesVisited,
            )

            val repairedVerification = itineraryVerificationService.verifyProposal(
                verificationRequest(
                    brief = travelBrief,
                    plan = repairedPlan,
                    stays = emptyList(),
                ),
                true,
                1,
            )
            VerifiedTravelPlanProposal(repairedPlan, repairedVerification)
        }
    }

    @Action
    fun findPlacesToSleep(
        brief: JourneyTravelBrief,
        verifiedProposal: VerifiedTravelPlanProposal,
        travelers: Travelers,
        knowledgeContext: TravelKnowledgeContext,
        context: OperationContext,
    ): TravelPlan {
        val plan = verifiedProposal.proposal
        val stays = consecutiveStays(plan.days)
        val dailyAccommodationBudget = brief.dailyBudget / 2.0
        val estimatedPromptCharacters = stays.sumOf { stay ->
            360 + stay.stayingAt().length + stay.days.size * 12
        }
        val toolNames = listOf(ToolsConfig.AIRBNB, CoreToolGroups.MATH)

        return tracedAction(
            context = context,
            actionName = "findPlacesToSleep",
            inputSummary = "stays=${stays.size}, dailyAccommodationBudget=$dailyAccommodationBudget",
            modelName = "researcher",
            promptCharacters = estimatedPromptCharacters,
            toolNames = toolNames,
            outputSummary = { "stays=${it.stays.size}, verification=${it.verificationResult.status}" },
            completionCharacters = { it.stays.sumOf { stay -> stay.airbnbUrl?.length ?: 0 } },
        ) {
            // Build Airbnb search URLs deterministically in code (like journeyMapUrl) instead of via
            // an MCP tool, so the plan still completes when the airbnb tool group is unavailable.
            val foundStays = stays.map { stay ->
                logger.info("Building Airbnb search URL for stay at: {}", stay.locationAndCountry())
                val encodedLocation = java.net.URLEncoder.encode(stay.stayingAt(), Charsets.UTF_8.name())
                val checkIn = stay.days.minOf { it.date }
                val checkOut = stay.days.maxOf { it.date }.plusDays(1)
                val priceMax = dailyAccommodationBudget.toInt()
                val adults = travelers.travelers.size.coerceAtLeast(1)
                val airbnbUrl = "https://www.airbnb.com/s/$encodedLocation/homes" +
                    "?checkin=$checkIn&checkout=$checkOut&price_max=$priceMax&adults=$adults"
                stay.copy(
                    airbnbUrl = airbnbUrl,
                )
            }

            val finalVerification = itineraryVerificationService.verifyTravelPlan(
                verificationRequest(
                    brief = brief,
                    plan = plan,
                    stays = foundStays,
                ),
                verifiedProposal.isRepaired(),
                verifiedProposal.repairAttempts(),
            )

            TravelPlan(
                brief = brief,
                proposal = plan,
                stays = foundStays,
                travelers = travelers,
                knowledgeContext = knowledgeContext,
                verificationResult = finalVerification,
            )
        }
    }

    @AchievesGoal(
        description = "Create a detailed travel plan based on a given travel brief",
        export = Export(
            name = "makeTravelPlan",
            remote = true,
            startingInputTypes = [TravelersAndBrief::class],
        ),
    )
    @Action
    fun postProcessHtml(
        plan: TravelPlan,
        context: OperationContext,
    ): TravelPlan {
        return tracedAction(
            context = context,
            actionName = "postProcessHtml",
            inputSummary = "htmlCharacters=${plan.proposal.plan.length}, imageLinks=${plan.proposal.imageLinks.size}",
            outputSummary = { "htmlCharacters=${it.proposal.plan.length}" },
            completionCharacters = { it.proposal.plan.length },
        ) {
            val oldPlan = plan.proposal.plan
            plan.copy(
                proposal = plan.proposal.copy(
                    plan = StringTransformer.transform(
                        oldPlan, listOf(
                            stripCodeFence,
                            // Whitelist pass before styleImages so the class attribute it adds survives.
                            sanitizeHtml,
                            styleImages,
                            removeUnsafeLinks,
                            ImageChecker.removeInvalidImageLinks,
                        )
                    ),
                    pageLinks = safeResources(plan.proposal.pageLinks),
                    imageLinks = safeResources(plan.proposal.imageLinks),
                    videoLinks = safeResources(plan.proposal.videoLinks),
                ),
                stays = plan.stays.map { stay ->
                    stay.copy(airbnbUrl = contentSafetyService.safeUrlOrNull(stay.airbnbUrl))
                },
            )
        }
    }

    // Some models (e.g. via generateText) wrap the HTML body in a ```html ... ``` markdown fence;
    // strip it so the literal backticks do not show in the rendered plan.
    private val stripCodeFence = StringTransformer { html ->
        html.trim()
            .replace(Regex("^```[a-zA-Z]*\\s*"), "")
            .replace(Regex("\\s*```$"), "")
            .trim()
    }

    // Whitelist-sanitize LLM HTML before display: only safe structural tags/attributes and
    // http(s) URLs survive. This is the output-side counterpart to the input-side RAG checks.
    private val sanitizeHtml = StringTransformer { html -> planHtmlSanitizer.sanitize(html) }

    private val styleImages = StringTransformer { html ->
        html.replace(
            "<img",
            "<img class=\"styled-image-thick\""
        )
    }

    private val removeUnsafeLinks = StringTransformer { html ->
        contentSafetyService.sanitizeHtmlLinks(html)
    }

    private fun safeResources(resources: List<InternetResource>): List<InternetResource> =
        resources
            .filter { contentSafetyService.isSafeHttpUrl(it.url) }
            .map { InternetResource(it.url, it.summary) }

    private fun <T> tracedAction(
        context: OperationContext,
        actionName: String,
        inputSummary: String,
        modelName: String? = null,
        promptCharacters: Int? = null,
        toolNames: List<String> = emptyList(),
        outputSummary: (T) -> String,
        completionCharacters: (T) -> Int? = { null },
        block: () -> T,
    ): T {
        val runId = context.agentProcess.id
        val eventId = agentRunTraceService.startAction(
            runId,
            actionName,
            inputSummary,
            modelName,
            promptCharacters,
            toolNames,
        )
        return try {
            val result = block()
            agentRunTraceService.completeAction(
                runId,
                eventId,
                outputSummary(result),
                completionCharacters(result),
            )
            result
        } catch (ex: RuntimeException) {
            agentRunTraceService.failAction(runId, eventId, ex.message ?: ex::class.simpleName)
            throw ex
        } catch (ex: Error) {
            agentRunTraceService.failAction(runId, eventId, ex.message ?: ex::class.simpleName)
            throw ex
        }
    }

    /**
     * Instruction so the LLM writes natural-language content in the user's chosen UI language,
     * while keeping machine-consumed fields (locationAndCountry, place names, URLs) in Latin form
     * so the verifier coordinate catalog, Airbnb URLs and Google Maps links keep working.
     */
    private fun languageInstruction(brief: JourneyTravelBrief): String =
        """
        Write all natural-language content (titles, headings, descriptions and prose) in ${brief.language}.
        IMPORTANT: keep place names and the "locationAndCountry" field in Google Maps friendly Latin form
        (for example Barcelona,+Spain); do NOT translate location values, URLs or citation ids.
        """.trimIndent()

    private fun fallbackPlanMeta(
        brief: JourneyTravelBrief,
        poiFindings: PointOfInterestFindings,
    ): ProposedTravelPlanMeta {
        val days = fallbackJourneyDays(brief, poiFindings)
        return ProposedTravelPlanMeta(
            title = if (isChinese(brief)) "${brief.from}至${brief.to}轻松行程" else "${brief.from} to ${brief.to} itinerary",
            days = days,
            imageLinks = safeResources(poiFindings.pointsOfInterest.flatMap { it.imageLinks }).take(6),
            videoLinks = safeResources(poiFindings.pointsOfInterest.flatMap { it.videoLinks }).take(6),
            pageLinks = safeResources(poiFindings.pointsOfInterest.flatMap { it.links }).take(8),
            countriesVisited = countriesFrom(days),
        )
    }

    private fun fallbackPlanHtml(
        brief: JourneyTravelBrief,
        days: List<Day>,
        poiFindings: PointOfInterestFindings,
        knowledgeContext: TravelKnowledgeContext,
    ): String {
        val dayItems = days.joinToString("\n") {
            "<li><strong>${it.date}</strong>: ${html(it.locationAndCountry.replace("+", " "))}</li>"
        }
        val poiItems = poiFindings.pointsOfInterest.joinToString("\n") {
            "<li><strong>${html(it.pointOfInterest.name)}</strong>: ${html(it.pointOfInterest.description)}</li>"
        }
        val knowledgeNote = if (knowledgeContext.hits.isEmpty()) ""
        else "<p>${html("Included ${knowledgeContext.hits.size} user knowledge source(s) where relevant.")}</p>"

        return if (isChinese(brief)) {
            """
            <h4>${html(brief.from)}至${html(brief.to)}${days.size}天轻松行程</h4>
            <p>实时长文生成暂时不可用，以下为基于已完成路线结构、旅行需求和可用研究结果生成的保守行程。</p>
            <h4>每日安排</h4>
            <ul>
            $dayItems
            </ul>
            <h4>重点体验</h4>
            <ul>
            $poiItems
            </ul>
            <p>跨城交通以${transportLabelZh(brief.transportPreference)}为主；出发前请再次核对班次、开放时间、天气和当地交通。</p>
            $knowledgeNote
            """.trimIndent()
        } else {
            """
            <h4>${html(brief.from)} to ${html(brief.to)} ${days.size}-day relaxed itinerary</h4>
            <p>Live long-form generation was temporarily unavailable, so this conservative itinerary was assembled from the route, brief, and available research.</p>
            <h4>Daily route</h4>
            <ul>
            $dayItems
            </ul>
            <h4>Focus experiences</h4>
            <ul>
            $poiItems
            </ul>
            <p>Plan intercity travel by ${html(brief.transportPreference)}. Recheck schedules, opening hours, weather, and local transport before departure.</p>
            $knowledgeNote
            """.trimIndent()
        }
    }

    /**
     * Derive each fallback day's location from the researched points of interest: their
     * locations are Latin-form values produced by earlier, successful pipeline steps, so the
     * fallback stays compatible with the verifier catalog and map links for any route without
     * a transliteration table. Uncovered dates carry the previous location forward.
     */
    private fun fallbackJourneyDays(
        brief: JourneyTravelBrief,
        poiFindings: PointOfInterestFindings,
    ): List<Day> {
        val pois = poiFindings.pointsOfInterest
            .map { it.pointOfInterest }
            .filter { it.location.isNotBlank() }
            .sortedBy { it.fromDate }
        if (pois.isEmpty()) {
            return fallbackJourneyDaysFromBrief(brief)
        }
        val result = mutableListOf<Day>()
        var lastLocation = pois.first().location
        var cursor = brief.departureDate
        while (!cursor.isAfter(brief.returnDate)) {
            val covering = pois.firstOrNull { !cursor.isBefore(it.fromDate) && !cursor.isAfter(it.toDate) }
            if (covering != null) {
                lastLocation = covering.location
            }
            result.add(Day(cursor, lastLocation))
            cursor = cursor.plusDays(1)
        }
        return result
    }

    /**
     * Last resort with no researched POIs at all: split the trip between the user's own
     * origin and destination wording, normalized to the '+'-separated location form. The
     * verifier may then report route estimates as unavailable (INFO), which is honest.
     */
    private fun fallbackJourneyDaysFromBrief(brief: JourneyTravelBrief): List<Day> {
        val totalDays = (ChronoUnit.DAYS.between(brief.departureDate, brief.returnDate) + 1).coerceAtLeast(1)
        val daysAtOrigin = if (totalDays >= 6) 3 else (totalDays / 2).coerceAtLeast(1)
        val result = mutableListOf<Day>()
        var cursor = brief.departureDate
        var index = 0L
        while (!cursor.isAfter(brief.returnDate)) {
            val location = if (index < daysAtOrigin) brief.from else brief.to
            result.add(Day(cursor, location.trim().replace(Regex("\\s+"), "+")))
            cursor = cursor.plusDays(1)
            index += 1
        }
        return result
    }

    /** Countries parsed from 'City,+Country' day locations; days without a country part are skipped. */
    private fun countriesFrom(days: List<Day>): List<String> =
        days.mapNotNull { day ->
            day.locationAndCountry
                .substringAfterLast(',', "")
                .replace("+", " ")
                .trim()
                .ifBlank { null }
        }.distinct()

    /** The form's transport values are our own closed set, so a fixed label map is safe here. */
    private fun transportLabelZh(preference: String): String = when (preference.trim().lowercase()) {
        "driving" -> "自驾"
        "train" -> "火车"
        "flying" -> "飞机"
        "cycling" -> "骑行"
        else -> preference
    }

    private fun html(value: String): String =
        value
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")

    /**
     * Ensure every date from start to end has a day, filling gaps with the previous day's
     * location. Models (especially OpenAI-compatible domestic ones) sometimes omit dates, which
     * would otherwise trip the verifier's DATE_GAP check and force an avoidable repair pass.
     */
    private fun completeDays(
        days: List<Day>,
        start: LocalDate,
        end: LocalDate,
        fallbackLocation: String,
    ): List<Day> {
        val byDate = days.associateBy { it.date }
        var lastLocation = days.firstOrNull()?.locationAndCountry?.takeIf { it.isNotBlank() } ?: fallbackLocation
        val result = mutableListOf<Day>()
        var cursor = start
        while (!cursor.isAfter(end)) {
            val existing = byDate[cursor]
            if (existing != null && existing.locationAndCountry.isNotBlank()) {
                lastLocation = existing.locationAndCountry
                result.add(existing)
            } else {
                result.add(Day(cursor, lastLocation))
            }
            cursor = cursor.plusDays(1)
        }
        return result
    }

    private fun isChinese(brief: JourneyTravelBrief): Boolean =
        brief.language.contains("chinese", ignoreCase = true) || brief.language.contains("中文")

    /** When the user picked Chinese, run this prompt on the configured domestic (Moonshot) model. */
    private fun PromptRunner.withLanguageModel(brief: JourneyTravelBrief, model: String): PromptRunner =
        if (isChinese(brief)) withLlm(LlmOptions.withModel(model)) else this

    private fun modelName(options: LlmOptions): String? =
        options.model ?: options.role ?: options.criteria.toString()

    private fun verificationRequest(
        brief: JourneyTravelBrief,
        plan: ProposedTravelPlan,
        stays: List<Stay>,
    ): ItineraryVerificationRequest {
        val pageLinks = plan.pageLinks.map {
            ItineraryLink("pageLinks", it.url, it.summary)
        }
        val imageLinks = plan.imageLinks.map {
            ItineraryLink("imageLinks", it.url, it.summary)
        }
        val videoLinks = plan.videoLinks.map {
            ItineraryLink("videoLinks", it.url, it.summary)
        }
        val stayModels = stays.map { stay ->
            ItineraryStay(
                stay.days.map { ItineraryDay(it.date, it.locationAndCountry) },
                stay.airbnbUrl,
            )
        }
        return ItineraryVerificationRequest(
            brief.from,
            brief.to,
            brief.transportPreference,
            brief.departureDate,
            brief.returnDate,
            brief.dailyBudget,
            plan.title,
            plan.plan,
            plan.days.map { ItineraryDay(it.date, it.locationAndCountry) },
            pageLinks + imageLinks + videoLinks,
            stayModels,
        )
    }

}

/**
 * Used for an LLM return
 */
private data class AirbnbResultsLlmReturn(
    val searchUrl: String,
)

/**
 * Extending SomeOf causes both Travelers and JourneyTravelBrief to be bound to the blackboard
 */
data class TravelersAndBrief(
    val travelers: Travelers,
    val brief: JourneyTravelBrief,
) : SomeOf
