package io.github.shibuna.tripsmith.config

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
 * the account does not have only fails if that model is actually called. Pricing is published CNY
 * list price converted at a single FX constant (see below), used for cost reporting only — edit the
 * rate or the per-model CNY figures to match your tier.
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

    // Moonshot publishes prices in CNY per 1M tokens. We keep the published CNY list prices below
    // and convert with one explicit FX constant, so cost reporting is self-documenting and you can
    // recalibrate by editing a single number (the rate, or a model's CNY figure) for your tier.
    private val cnyPerUsd = 7.2

    private fun usd(cnyPerMillion: Double): Double = cnyPerMillion / cnyPerUsd

    private fun kimi(model: String, inputCnyPerM: Double, outputCnyPerM: Double): Llm =
        factory().openAiCompatibleLlm(
            model,
            PricingModel.usdPer1MTokens(usd(inputCnyPerM), usd(outputCnyPerM)),
            provider,
            knowledgeCutoff,
        )

    // CNY/1M list prices (input, output). Update these to your actual contracted rates.
    @Bean
    fun moonshotV1128k(): Llm = kimi("moonshot-v1-128k", 60.0, 60.0)

    @Bean
    fun moonshotV132k(): Llm = kimi("moonshot-v1-32k", 24.0, 24.0)

    @Bean
    fun moonshotV18k(): Llm = kimi("moonshot-v1-8k", 12.0, 12.0)

    @Bean
    fun kimiK2(): Llm = kimi("kimi-k2", 4.0, 16.0)

    @Bean
    fun kimiK25(): Llm = kimi("kimi-k2.5", 4.0, 16.0)
}
