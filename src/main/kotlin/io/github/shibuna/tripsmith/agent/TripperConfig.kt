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
package io.github.shibuna.tripsmith.agent

import com.embabel.agent.api.common.Actor
import com.embabel.agent.prompt.element.ToolCallControl
import com.embabel.agent.prompt.persona.Persona
import com.embabel.agent.prompt.persona.RoleGoalBackstory
import com.embabel.common.ai.model.LlmOptions
import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties("tripsmith")
data class TripperConfig(
    val wordCount: Int = 700,
    val imageWidth: Int = 800,
    val planner: Actor<Persona>,
    val researcher: Actor<RoleGoalBackstory>,
    // Cap tool calls per LLM step to bound token cost and latency (each tool result is fed
    // back into the model context). Override via tripsmith.tool-call-control.tool-calls.
    val toolCallControl: ToolCallControl = ToolCallControl(4),
    val thinkerLlm: LlmOptions,
    val maxConcurrency: Int = 12,
    // Points of interest scale with trip length (pointsOfInterestPerDay * days), since research
    // fans out one parallel LLM call per POI. maxPointsOfInterest is a hard ceiling so very long
    // trips cannot blow up cost. Tune both via tripsmith.* .
    val pointsOfInterestPerDay: Int = 2,
    val maxPointsOfInterest: Int = 10,
    // Server-side cap on trip length: each day adds POI research fan-out and planner prompt
    // size, so an oversized date range is a cost (and abuse) concern, not just a UX one.
    val maxTripDays: Int = 30,
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
