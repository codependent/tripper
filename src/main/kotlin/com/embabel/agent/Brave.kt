package com.embabel.agent

import com.fasterxml.jackson.annotation.JsonPropertyDescription
import com.fasterxml.jackson.annotation.JsonSubTypes
import com.fasterxml.jackson.annotation.JsonTypeInfo
import org.slf4j.LoggerFactory
import org.springframework.ai.tool.annotation.Tool
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpMethod
import org.springframework.stereotype.Service
import org.springframework.web.client.RestTemplate
import java.time.Instant
import java.util.concurrent.Semaphore
import java.util.concurrent.TimeUnit
import java.util.concurrent.locks.LockSupport

data class WebSearchRequest(
    val query: String,
    val count: Int = 10,
    @JsonPropertyDescription("Offset for pagination, defaults to 0, goes up by 1 for page size")
    val offset: Int = 0,
)

abstract class BraveSearchService(
    val name: String,
    val description: String,
    @Value("\${BRAVE_API_KEY}")
    private val apiKey: String,
    private val baseUrl: String,
    private val restTemplate: RestTemplate,
    private val pacer: BravePacer,
) {

    private val log = LoggerFactory.getLogger(javaClass)
    private val permit = Semaphore(1, true)
    @Volatile private var readyAtNanos: Long = 0L

    fun search(request: WebSearchRequest): BraveSearchResults = runPaced  {
        log.info("************** Searching for $name: $request")
        val headers = HttpHeaders().apply {
            set("X-Subscription-Token", apiKey)
            set("Accept", "application/json")
        }

        val entity = HttpEntity<String>(headers)

        val rawResponse = restTemplate.exchange(
            "$baseUrl?q={query}&count={count}&offset={offset}",
            HttpMethod.GET,
            entity,
            BraveResponse::class.java,
            mapOf(
                "query" to request.query,
                "count" to request.count,
                "offset" to request.offset,
            ),
        ).body ?: run {
            throw RuntimeException("No response body")
        }
        rawResponse.toBraveSearchResults(request)
    }

    fun searchRaw(request: WebSearchRequest): String = runPaced  {
        log.info("************** Searching RAW for $name: $request")
        val headers = HttpHeaders().apply {
            set("X-Subscription-Token", apiKey)
            set("Accept", "application/json")
        }

        val entity = HttpEntity<String>(headers)

        restTemplate.exchange(
            "$baseUrl?q={query}&count={count}&offset={offset}",
            HttpMethod.GET,
            entity,
            String::class.java,
            mapOf(
                "query" to request.query,
                "count" to request.count,
                "offset" to request.offset,
            ),
        ).body ?: run {
            throw RuntimeException("No response body")
        }
    }

    private fun <T> runPaced(block: () -> T): T = pacer.pace(block)
}

@ConditionalOnProperty("BRAVE_API_KEY")
@Service
class BraveWebSearchService(
    @Value("\${BRAVE_API_KEY}") apiKey: String,
    restTemplate: RestTemplate,
    bravePacer: BravePacer,
) : BraveSearchService(
    name = "Brave web search",
    description = "Search the web with Brave",
    apiKey = apiKey,
    baseUrl = "https://api.search.brave.com/res/v1/web/search",
    restTemplate = restTemplate,
    pacer = bravePacer
)

@ConditionalOnProperty("BRAVE_API_KEY")
@Service
class BraveNewsSearchService(
    @Value("\${BRAVE_API_KEY}") apiKey: String,
    restTemplate: RestTemplate,
    bravePacer: BravePacer,
) : BraveSearchService(
    name = "Brave news search",
    description = "Search for news with Brave",
    apiKey = apiKey,
    baseUrl = "https://api.search.brave.com/res/v1/news/search",
    restTemplate = restTemplate,
    pacer = bravePacer
)

@ConditionalOnProperty("BRAVE_API_KEY")
@Service
class BraveImageSearchService(
    @Value("\${BRAVE_API_KEY}") apiKey: String,
    restTemplate: RestTemplate,
    bravePacer: BravePacer,
) : BraveSearchService(
    name = "Brave news search",
    description = "Search for news with Brave",
    apiKey = apiKey,
    baseUrl = "https://api.search.brave.com/res/v1/images/search",
    restTemplate = restTemplate,
    pacer = bravePacer
) {

    @Tool(description = "Brave image search")
    fun searchImages(request: WebSearchRequest): String {
        val raw = searchRaw(request)
        return raw
    }
}

@ConditionalOnProperty("BRAVE_API_KEY")
@Service
class BraveVideoSearchService(
    @Value("\${BRAVE_API_KEY}") apiKey: String,
    restTemplate: RestTemplate,
    bravePacer: BravePacer
) : BraveSearchService(
    name = "Brave video search",
    description = "Search for videos with Brave",
    apiKey = apiKey,
    baseUrl = "https://api.search.brave.com/res/v1/videos/search",
    restTemplate = restTemplate,
    pacer = bravePacer
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

