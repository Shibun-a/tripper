package io.github.shibuna.tripsmith.eval

import com.embabel.agent.core.AgentPlatform
import com.embabel.agent.core.AgentProcessStatusCode
import com.embabel.agent.core.Budget
import com.embabel.agent.core.ProcessOptions
import io.github.shibuna.tripsmith.agent.JourneyTravelBrief
import io.github.shibuna.tripsmith.agent.TravelPlan
import io.github.shibuna.tripsmith.agent.Traveler
import io.github.shibuna.tripsmith.agent.Travelers
import io.github.shibuna.tripsmith.verification.ItineraryDay
import io.github.shibuna.tripsmith.verification.ItineraryLink
import io.github.shibuna.tripsmith.verification.ItineraryStay
import io.github.shibuna.tripsmith.verification.ItineraryVerificationRequest
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

/**
 * Real, agent-backed [EvalPlanCandidateFactory]: runs the actual TripperAgent for each eval case and
 * turns the resulting plan into an [EvalPlanCandidate] with REAL cost and latency. This is what makes
 * the existing deterministic metrics trustworthy — unlike the synthetic factory, a real agent can
 * actually miss dates, emit bad links or forget citations, so the verifier scores mean something.
 *
 * Needs the Spring context (LLM + MCP tools), so it is only used by the gated agent-evaluation tier,
 * never by the free `TravelEvaluationCli` main. See plan: 修补评测漏洞.
 */
@Component
class AgentEvalPlanCandidateFactory(
    private val agentPlatform: AgentPlatform,
) : EvalPlanCandidateFactory {

    private val logger = LoggerFactory.getLogger(AgentEvalPlanCandidateFactory::class.java)

    override fun create(evalCase: TravelEvalCase): EvalPlanCandidate {
        val agent = agentPlatform.agents().firstOrNull {
            it.name.contains("tripper", ignoreCase = true) ||
                it.description.contains("Make a detailed travel plan", ignoreCase = true)
        } ?: error("TripperAgent is not deployed on the agent platform")

        val brief = JourneyTravelBrief(
            from = evalCase.from(),
            to = evalCase.to(),
            transportPreference = evalCase.transportPreference(),
            brief = briefText(evalCase),
            departureDate = evalCase.departure(),
            returnDate = evalCase.returns(),
            dailyBudget = evalCase.dailyBudget(),
            language = "English",
        )
        val travelers = Travelers(
            evalCase.travelers().mapIndexed { i, description ->
                Traveler(
                    name = description.substringBefore(" ").ifBlank { "Traveler ${i + 1}" },
                    about = description,
                )
            }
        )

        // Same generous budget the web UI uses for a real plan.
        val options = ProcessOptions.DEFAULT.withBudget(
            Budget(Budget.DEFAULT_COST_LIMIT, Budget.DEFAULT_ACTION_LIMIT, Budget.DEFAULT_TOKEN_LIMIT * 3)
        )

        val start = System.currentTimeMillis()
        val process = agentPlatform.start(
            agentPlatform.createAgentProcessFrom(agent, options, brief, travelers)
        ).get()
        val latencyMs = System.currentTimeMillis() - start
        val costUsd = process.cost() ?: 0.0

        if (process.status != AgentProcessStatusCode.COMPLETED) {
            // Record the failure honestly (empty itinerary -> zero date coverage) instead of aborting
            // the whole batch, so one bad case does not hide as a crash.
            logger.warn("Eval case {} did not complete: status={}", evalCase.id(), process.status)
            return EvalPlanCandidate(failedRequest(evalCase), latencyMs, costUsd, 0, 0)
        }

        val plan = process.resultOfType(TravelPlan::class.java)
        // Tool-call success is not yet surfaced per run, so report 0/0 (harness treats as 1.0). TODO.
        return EvalPlanCandidate(toVerificationRequest(plan), latencyMs, costUsd, 0, 0)
    }

    private fun briefText(c: TravelEvalCase): String = buildString {
        append("A ").append(c.transportPreference()).append(" trip from ")
        append(c.from()).append(" to ").append(c.to()).append(". ")
        if (c.interests().isNotEmpty()) {
            append("Interests: ").append(c.interests().joinToString(", ")).append(". ")
        }
        if (c.constraints().isNotEmpty()) {
            append("Constraints: ").append(c.constraints().joinToString(", ")).append(". ")
        }
    }.trim()

    /** Mirror of TripperAgent.verificationRequest (private there) for a finished [TravelPlan]. */
    private fun toVerificationRequest(plan: TravelPlan): ItineraryVerificationRequest {
        val proposal = plan.proposal
        val links = buildList {
            proposal.pageLinks.forEach { add(ItineraryLink("pageLinks", it.url, it.summary)) }
            proposal.imageLinks.forEach { add(ItineraryLink("imageLinks", it.url, it.summary)) }
            proposal.videoLinks.forEach { add(ItineraryLink("videoLinks", it.url, it.summary)) }
        }
        val stays = plan.stays.map { stay ->
            ItineraryStay(stay.days.map { ItineraryDay(it.date, it.locationAndCountry) }, stay.airbnbUrl)
        }
        return ItineraryVerificationRequest(
            plan.brief.from,
            plan.brief.to,
            plan.brief.transportPreference,
            plan.brief.departureDate,
            plan.brief.returnDate,
            plan.brief.dailyBudget,
            proposal.title,
            proposal.plan,
            proposal.days.map { ItineraryDay(it.date, it.locationAndCountry) },
            links,
            stays,
        )
    }

    private fun failedRequest(c: TravelEvalCase): ItineraryVerificationRequest =
        ItineraryVerificationRequest(
            c.from(), c.to(), c.transportPreference(),
            c.departure(), c.returns(), c.dailyBudget(),
            c.title(), "", emptyList(), emptyList(), emptyList(),
        )
}
