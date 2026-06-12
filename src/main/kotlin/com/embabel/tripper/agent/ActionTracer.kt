package com.embabel.tripper.agent

import com.embabel.agent.api.common.OperationContext
import com.embabel.tripper.observability.AgentRunTraceService
import org.springframework.stereotype.Component

/**
 * Wraps an agent action with run-trace bookkeeping (start/complete/fail events keyed by the
 * Embabel process id), so @Action methods carry no observability plumbing of their own.
 */
@Component
class ActionTracer(private val agentRunTraceService: AgentRunTraceService) {

    fun <T> traced(
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
