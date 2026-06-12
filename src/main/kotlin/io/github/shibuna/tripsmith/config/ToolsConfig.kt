package io.github.shibuna.tripsmith.config

import com.embabel.agent.core.ToolGroup
import com.embabel.agent.core.ToolGroupDescription
import com.embabel.agent.core.ToolGroupPermission
import com.embabel.agent.tools.mcp.McpToolGroup
import io.modelcontextprotocol.client.McpSyncClient
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.client.JdkClientHttpRequestFactory
import org.springframework.web.client.RestClient
import java.net.http.HttpClient
import java.time.Duration

@Configuration
class ToolsConfig(
    private val mcpSyncClients: List<McpSyncClient>,
) {

    @Bean
    fun restClient(): RestClient {
        // Bounded, no-redirect HTTP client. Knowledge imports validate the resolved host before
        // fetching (UrlImportGuard); following redirects would bypass that check, and missing
        // timeouts would let a slow host pin request threads.
        val httpClient = HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.NEVER)
            .connectTimeout(Duration.ofSeconds(5))
            .build()
        val requestFactory = JdkClientHttpRequestFactory(httpClient)
        requestFactory.setReadTimeout(Duration.ofSeconds(10))
        return RestClient.builder().requestFactory(requestFactory).build()
    }

    @Bean
    fun mcpAirbnbToolsGroup(): ToolGroup {
        return McpToolGroup(
            description = ToolGroupDescription(description = "Airbnb tools", role = AIRBNB),
            name = "openbnb-airbnb",
            provider = "Docker",
            permissions = setOf(
                ToolGroupPermission.INTERNET_ACCESS
            ),
            clients = mcpSyncClients,
            filter = {
                it.toolDefinition.name().contains("airbnb")
            },
        )
    }

    companion object {
        const val AIRBNB = "airbnb"
    }
}