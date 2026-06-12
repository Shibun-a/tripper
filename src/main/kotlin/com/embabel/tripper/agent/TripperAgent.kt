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
import com.embabel.agent.api.common.OperationContext
import com.embabel.agent.api.common.SomeOf
import com.embabel.agent.api.common.create
import com.embabel.agent.core.CoreToolGroups
import com.embabel.agent.core.last
import com.embabel.agent.domain.library.InternetResource
import com.embabel.agent.prompt.ResponseFormat
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
import com.embabel.tripper.verification.ItineraryVerificationService
import org.slf4j.LoggerFactory
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import java.util.concurrent.CancellationException
import java.util.concurrent.atomic.AtomicInteger

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
    private val fallbackPlans: FallbackPlans,
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
        val prompt = TripPrompts.findPointsOfInterest(
            toolPolicy = toolSafetyService.promptPolicy("findPointsOfInterest", toolNames),
            brief = travelBrief,
            knowledgeContext = knowledgeContext,
            maxPois = maxPois,
        )
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
            val toolPolicy = toolSafetyService.promptPolicy("researchPointsOfInterest", toolNames)
            val researchFailures = AtomicInteger()
            val poiFindings = context.parallelMap(
                itineraryIdeas.pointsOfInterest,
                maxConcurrency = config.maxConcurrency,
            ) { poi ->
                try {
                    val rpi = promptRunner.create<ResearchedPointOfInterest>(
                        prompt = TripPrompts.researchPointOfInterest(toolPolicy, travelBrief, poi, knowledgeContext),
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
        val poiSummary = TripPrompts.poiSummary(poiFindings, config.researchSummaryCharacters)
        val structurePrompt = TripPrompts.planStructure(travelBrief, poiSummary)

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
                fallbackPlans.planMeta(travelBrief, poiFindings)
            }
            // Guarantee full date coverage in code so a model that omits dates can't trip DATE_GAP.
            val days = completeDays(
                meta.days, travelBrief.departureDate, travelBrief.returnDate, travelBrief.to,
            )

            // Call 2: the long HTML body as plain text (no JSON escaping to get wrong).
            val planHtml = try {
                runner.withPromptElements(ResponseFormat.HTML).generateText(
                    TripPrompts.planHtmlBody(
                        travelBrief, days, poiSummary, knowledgeContext,
                        config.wordCount, config.imageWidth,
                    )
                )
            } catch (ex: RuntimeException) {
                logger.warn(
                    "Falling back to deterministic HTML plan after planner text failure: {}",
                    ex.message,
                )
                fallbackPlans.planHtml(travelBrief, days, poiFindings, knowledgeContext)
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

            // Context shared by both repair calls. Keeping it identical across the structure and
            // HTML passes ensures they describe the same repaired trip.
            val repairContext = TripPrompts.repairContext(
                travelBrief, knowledgeContext, verification, proposedPlan, poiFindings,
            )

            val runner = config.planner.promptRunner(context)
                .withLanguageModel(travelBrief, config.cnPlannerModel)
                .withPromptElements(travelers, config.toolCallControl)

            // Call 1: repaired STRUCTURE only (no HTML inside JSON) — same split as proposeTravelPlan.
            val repairedMeta = runner.create<ProposedTravelPlanMeta>(
                prompt = TripPrompts.repairStructure(repairContext, travelBrief)
            )
            // Guarantee full date coverage in code so a model that omits dates can't re-trip DATE_GAP.
            val repairedDays = completeDays(
                repairedMeta.days, travelBrief.departureDate, travelBrief.returnDate, travelBrief.to,
            )

            // Call 2: repaired HTML body as plain text (no JSON escaping to get wrong).
            val repairedHtml = runner.withPromptElements(ResponseFormat.HTML).generateText(
                TripPrompts.repairHtmlBody(repairContext, repairedDays, proposedPlan, config.wordCount)
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
