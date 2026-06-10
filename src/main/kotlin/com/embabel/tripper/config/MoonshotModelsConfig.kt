package com.embabel.tripper.config

import com.embabel.agent.openai.OpenAiCompatibleModelFactory
import com.embabel.common.ai.model.Llm
import com.embabel.common.ai.model.PricingModel
import io.micrometer.observation.ObservationRegistry
import org.springframework.beans.factory.ObjectProvider
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.client.ClientHttpRequestFactory
import java.time.LocalDate

/**
 * Registers Moonshot (Kimi) models through their OpenAI-compatible endpoint, so the agent can run
 * on a domestic, directly-reachable LLM (no cross-border hop / EOF) when the user picks Chinese.
 *
 * Only active when MOONSHOT_API_KEY is set, so the project still builds/runs without it. The model
 * objects are created lazily and not validated against the API at startup, so registering a name
 * the account does not have only fails if that model is actually called. Pricing is approximate
 * (¥→$) and used for cost reporting only — adjust to your tier.
 */
@Configuration
@ConditionalOnProperty("MOONSHOT_API_KEY")
class MoonshotModelsConfig(
    @Value("\${MOONSHOT_API_KEY}") private val apiKey: String,
    private val observationRegistry: ObservationRegistry,
    private val requestFactory: ObjectProvider<ClientHttpRequestFactory>,
) {

    private val provider = "Moonshot"
    private val knowledgeCutoff: LocalDate = LocalDate.of(2024, 1, 1)

    private fun factory() = OpenAiCompatibleModelFactory(
        "https://api.moonshot.cn",
        apiKey,
        "/v1/chat/completions",
        "/v1/embeddings",
        observationRegistry,
        requestFactory,
    )

    private fun kimi(model: String, inputUsdPerM: Double, outputUsdPerM: Double): Llm =
        factory().openAiCompatibleLlm(
            model,
            PricingModel.usdPer1MTokens(inputUsdPerM, outputUsdPerM),
            provider,
            knowledgeCutoff,
        )

    @Bean
    fun moonshotV1128k(): Llm = kimi("moonshot-v1-128k", 8.3, 8.3)

    @Bean
    fun moonshotV132k(): Llm = kimi("moonshot-v1-32k", 3.3, 3.3)

    @Bean
    fun moonshotV18k(): Llm = kimi("moonshot-v1-8k", 1.7, 1.7)

    @Bean
    fun kimiK2(): Llm = kimi("kimi-k2", 0.6, 2.3)
}
