package io.github.shibuna.tripsmith.web.support.htmx

import com.embabel.agent.core.AgentPlatform
import com.embabel.agent.core.AgentProcessStatusCode
import io.github.shibuna.tripsmith.agent.TravelPlan
import io.github.shibuna.tripsmith.editing.EditableItineraryDay
import io.github.shibuna.tripsmith.editing.PlanEditingService
import io.github.shibuna.tripsmith.observability.AgentRunStatus
import io.github.shibuna.tripsmith.observability.AgentRunTraceService
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Controller
import org.springframework.ui.Model
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.server.ResponseStatusException

@Controller
class ProcessStatusController(
    private val agentPlatform: AgentPlatform,
    private val agentRunTraceService: AgentRunTraceService,
    private val planEditingService: PlanEditingService,
) {

    private val logger = LoggerFactory.getLogger(ProcessStatusController::class.java)

    /**
     * The HTML page that shows the status of the plan generation.
     */
    @GetMapping("/status/{processId}")
    fun checkPlanStatus(
        @PathVariable processId: String,
        @RequestParam resultModelKey: String,
        @RequestParam successView: String,
        model: Model,
    ): String {
        val agentProcess = agentPlatform.getAgentProcess(processId)
            ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "Process not found")

        return when (agentProcess.status) {
            AgentProcessStatusCode.COMPLETED -> {
                logger.info("Process {} completed successfully", processId)
                agentRunTraceService.completeRun(
                    processId,
                    AgentRunStatus.COMPLETED,
                    agentProcess.cost(),
                    agentProcess.usage().promptTokens,
                    agentProcess.usage().completionTokens,
                    agentProcess.modelsUsed().map { it.name },
                )
                val result = agentProcess.lastResult()
                if (result is TravelPlan) {
                    val session = planEditingService.createSession(
                        processId,
                        result.proposal.title,
                        result.brief.from,
                        result.brief.to,
                        result.brief.transportPreference,
                        result.brief.departureDate,
                        result.brief.returnDate,
                        result.brief.dailyBudget,
                        result.brief.brief,
                        result.proposal.plan,
                        result.proposal.days.map {
                            EditableItineraryDay(it.date, it.locationAndCountry, null)
                        },
                    )
                    model.addAttribute("planEditSession", session)
                }
                model.addAttribute(resultModelKey, result)
                model.addAttribute("agentProcess", agentProcess)
                successView
            }

            AgentProcessStatusCode.FAILED -> {
                logger.error("Process {} failed: {}", processId, agentProcess.failureInfo)
                agentRunTraceService.completeRun(
                    processId,
                    AgentRunStatus.FAILED,
                    agentProcess.cost(),
                    agentProcess.usage().promptTokens,
                    agentProcess.usage().completionTokens,
                    agentProcess.modelsUsed().map { it.name },
                )
                model.addAttribute("error", "Failed to generate travel plan: ${agentProcess.failureInfo}")
                "common/processing-error"
            }

            AgentProcessStatusCode.TERMINATED -> {
                logger.info("Process {} was terminated", processId)
                agentRunTraceService.completeRun(
                    processId,
                    AgentRunStatus.TERMINATED,
                    agentProcess.cost(),
                    agentProcess.usage().promptTokens,
                    agentProcess.usage().completionTokens,
                    agentProcess.modelsUsed().map { it.name },
                )
                model.addAttribute("error", "Process was terminated before completion")
                "common/processing-error"
            }

            else -> {
                model.addAttribute("processId", processId)
                model.addAttribute("pageTitle", "Planning Journey...")
                "common/processing" // Keep showing loading state
            }
        }
    }
}
