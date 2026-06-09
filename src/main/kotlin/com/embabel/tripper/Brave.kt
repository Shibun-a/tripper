package com.embabel.tripper

import com.fasterxml.jackson.annotation.JsonPropertyDescription
import com.fasterxml.jackson.annotation.JsonSubTypes
import com.fasterxml.jackson.annotation.JsonTypeInfo
import org.springframework.ai.tool.annotation.Tool
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Service
import org.springframework.web.client.RestClient
import org.springframework.web.util.UriComponentsBuilder
import java.time.Instant

data class WebSearchRequest(
    val query: String,
    val count: Int = 10,
    @field:JsonPropertyDescription("Offset for pagination, defaults to 0, goes up by 1 for page size")
    val offset: Int = 0,
)

abstract class BraveSearchService(
    val name: String,
    val description: String,
    @field:Value("\${BRAVE_API_KEY}")
    protected val apiKey: String,
    private val baseUrl: String,
    protected val restClient: RestClient,
) {

    /**
     * Build the absolute request URI. baseUrl is a full https URL, so it must be parsed with
     * fromUriString rather than passed to uriBuilder.path() (which collapses "https://" to
     * "https:/" and produces an "unsupported URI").
     */
    protected fun requestUri(request: WebSearchRequest) =
        UriComponentsBuilder.fromUriString(baseUrl)
            .queryParam("q", request.query)
            .queryParam("count", request.count)
            .queryParam("offset", request.offset)
            .build()
            .toUri()

    fun search(request: WebSearchRequest): BraveSearchResults {
        val rawResponse = restClient.get()
            .uri(requestUri(request))
            .header("X-Subscription-Token", apiKey)
            .header("Accept", "application/json")
            .retrieve()
            .body(BraveResponse::class.java) ?: run {
            throw RuntimeException("No response body")
        }
        return rawResponse.toBraveSearchResults(request)
    }

    fun searchRaw(request: WebSearchRequest): String {
        return restClient.get()
            .uri(requestUri(request))
            .header("X-Subscription-Token", apiKey)
            .header("Accept", "application/json")
            .retrieve()
            .body(String::class.java) ?: run {
            throw RuntimeException("No response body")
        }
    }
}

@ConditionalOnProperty("BRAVE_API_KEY")
@Service
class BraveWebSearchService(
    @Value("\${BRAVE_API_KEY}") apiKey: String,
    restClient: RestClient
) : BraveSearchService(
    name = "Brave web search",
    description = "Search the web with Brave",
    apiKey = apiKey,
    baseUrl = "https://api.search.brave.com/res/v1/web/search",
    restClient = restClient,
)

@ConditionalOnProperty("BRAVE_API_KEY")
@Service
class BraveNewsSearchService(
    @Value("\${BRAVE_API_KEY}") apiKey: String,
    restClient: RestClient
) : BraveSearchService(
    name = "Brave news search",
    description = "Search for news with Brave",
    apiKey = apiKey,
    baseUrl = "https://api.search.brave.com/res/v1/news/search",
    restClient = restClient,
)

@ConditionalOnProperty("BRAVE_API_KEY")
@Service
class BraveImageSearchService(
    @Value("\${BRAVE_API_KEY}") apiKey: String,
    restClient: RestClient
) : BraveSearchService(
    name = "Brave news search",
    description = "Search for news with Brave",
    apiKey = apiKey,
    baseUrl = "https://api.search.brave.com/res/v1/images/search",
    restClient = restClient,
) {

    @Tool(description = "Brave image search. Returns up to 3 direct image URLs, each with a caption.")
    fun searchImages(request: WebSearchRequest): String {
        // Cap at 3 results and return only the direct image URL + caption instead of the full
        // raw JSON. Feeding the entire Brave payload back into the LLM context was the main
        // driver of runaway token cost during point-of-interest research.
        val response = restClient.get()
            .uri(requestUri(request.copy(count = 3)))
            .header("X-Subscription-Token", apiKey)
            .header("Accept", "application/json")
            .retrieve()
            .body(BraveImageSearchResponse::class.java)
        val images = response?.results.orEmpty()
            .mapNotNull { result ->
                val url = result.properties?.url ?: result.thumbnail?.src
                if (url.isNullOrBlank()) null
                else if (result.title.isNullOrBlank()) url else "$url | ${result.title}"
            }
            .take(3)
        return if (images.isEmpty()) "No images found" else images.joinToString("\n")
    }
}

internal data class BraveImageSearchResponse(
    val results: List<BraveImageResult> = emptyList(),
)

internal data class BraveImageResult(
    val title: String? = null,
    val url: String? = null,
    val thumbnail: BraveImageThumbnail? = null,
    val properties: BraveImageProperties? = null,
)

internal data class BraveImageThumbnail(val src: String? = null)

internal data class BraveImageProperties(val url: String? = null)

@ConditionalOnProperty("BRAVE_API_KEY")
@Service
class BraveVideoSearchService(
    @Value("\${BRAVE_API_KEY}") apiKey: String,
    restClient: RestClient
) : BraveSearchService(
    name = "Brave video search",
    description = "Search for videos with Brave",
    apiKey = apiKey,
    baseUrl = "https://api.search.brave.com/res/v1/videos/search",
    restClient = restClient,
)

data class BraveSearchResults(
    val request: WebSearchRequest,
    val query: Query,
    val results: List<BraveSearchResult>,
    val timestamp: Instant = Instant.now(),
    val id: String? = null,
) {

    val name: String
        get() = "Brave search results for query: ${query.original}"
}


data class BraveSearchResult(
    val title: String,
    val url: String,
    val description: String?,
)

data class Query(
    val original: String
)

@JsonTypeInfo(use = JsonTypeInfo.Id.DEDUCTION)
@JsonSubTypes(
    JsonSubTypes.Type(value = BraveWebSearchResponse::class),
    JsonSubTypes.Type(value = BraveNewsSearchResponse::class),
)
internal interface BraveResponse {
    val query: Query
    fun toBraveSearchResults(request: WebSearchRequest): BraveSearchResults
}

internal data class BraveWebSearchResponse(
    val web: WebResults,
    override val query: Query
) : BraveResponse {

    override fun toBraveSearchResults(request: WebSearchRequest): BraveSearchResults {
        return BraveSearchResults(
            request = request,
            query = query,
            results = web.results,
        )
    }
}

internal data class WebResults(
    val results: List<BraveSearchResult>
)

internal data class BraveNewsSearchResponse(
    val results: List<BraveSearchResult>,
    override val query: Query
) : BraveResponse {

    override fun toBraveSearchResults(request: WebSearchRequest): BraveSearchResults {
        return BraveSearchResults(
            request = request,
            query = query,
            results = results,
        )
    }
}

