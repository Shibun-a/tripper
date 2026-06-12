package io.github.shibuna.tripsmith.agent

import com.embabel.agent.api.common.PromptRunner
import com.embabel.common.ai.model.LlmOptions

/**
 * Language-based model routing. When the user picks Chinese the agent runs on the configured
 * domestic (Moonshot) models, which are directly reachable from a domestic network; everything
 * else uses the configured overseas defaults.
 */
internal fun isChinese(brief: JourneyTravelBrief): Boolean =
    brief.language.contains("chinese", ignoreCase = true) || brief.language.contains("中文")

/** When the user picked Chinese, run this prompt on the configured domestic model. */
internal fun PromptRunner.withLanguageModel(brief: JourneyTravelBrief, model: String): PromptRunner =
    if (isChinese(brief)) withLlm(LlmOptions.withModel(model)) else this

/** Best-effort display name for a configured LLM, for trace summaries. */
internal fun modelName(options: LlmOptions): String? =
    options.model ?: options.role ?: options.criteria.toString()
