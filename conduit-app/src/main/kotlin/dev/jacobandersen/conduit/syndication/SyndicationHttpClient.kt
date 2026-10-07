package dev.jacobandersen.conduit.syndication

import dev.jacobandersen.conduit.config.SyndicationProperties
import dev.jacobandersen.conduit.util.HttpUtil
import dev.jacobandersen.conduit.util.StringUtil.excerpt
import dev.jacobandersen.microformats2.Mf2Object
import org.springframework.http.MediaType
import org.springframework.http.client.ClientHttpRequestFactory
import org.springframework.http.client.JdkClientHttpRequestFactory
import org.springframework.stereotype.Service
import org.springframework.util.LinkedMultiValueMap
import org.springframework.web.client.RestClient
import org.springframework.web.client.RestClientException
import org.springframework.web.client.RestClientResponseException
import tools.jackson.databind.ObjectMapper
import java.net.http.HttpClient
import java.time.Duration

sealed interface SyndicationSendResult {
    data class Success(
        val statusCode: Int,
        val location: String?,
    ) : SyndicationSendResult

    data class Failure(
        val statusCode: Int?,
        val message: String,
        val retryable: Boolean,
    ) : SyndicationSendResult
}

/**
 * Sends posts to downstream micropub syndication targets and retracts copies.
 * Create posts the mapped mf2 as JSON; delete posts `action=delete&url=...`.
 */
@Service
class SyndicationHttpClient(
    private val objectMapper: ObjectMapper,
) {
    private val client: RestClient =
        RestClient
            .builder()
            .requestFactory(requestFactory())
            .requestInterceptor(SyndicationHttpLoggingInterceptor())
            .build()

    fun sendCreate(
        target: SyndicationProperties.Target,
        obj: Mf2Object,
    ): SyndicationSendResult = send(target, MediaType.APPLICATION_JSON, objectMapper.writeValueAsString(obj))

    fun sendDelete(
        target: SyndicationProperties.Target,
        sourceUrl: String,
    ): SyndicationSendResult {
        val payload = LinkedMultiValueMap<String, String>()
        payload.add("action", "delete")
        payload.add("url", sourceUrl)
        return send(target, MediaType.APPLICATION_FORM_URLENCODED, payload)
    }

    private fun send(
        target: SyndicationProperties.Target,
        contentType: MediaType,
        body: Any,
    ): SyndicationSendResult =
        try {
            val response =
                client
                    .post()
                    .uri(target.endpoint)
                    .contentType(contentType)
                    .headers { headers -> target.token?.let { headers.setBearerAuth(it) } }
                    .body(body)
                    .retrieve()
                    .toBodilessEntity()

            SyndicationSendResult.Success(
                statusCode = response.statusCode.value(),
                location = response.headers.location?.toString(),
            )
        } catch (e: RestClientResponseException) {
            val statusCode = e.statusCode.value()
            SyndicationSendResult.Failure(
                statusCode = statusCode,
                message = describeHttpError(statusCode, e.responseBodyAsString),
                retryable = HttpUtil.isTransientStatus(statusCode),
            )
        } catch (e: RestClientException) {
            SyndicationSendResult.Failure(null, e.message ?: e::class.simpleName ?: "Request failed", retryable = true)
        }

    private fun describeHttpError(
        statusCode: Int,
        responseBody: String?,
    ): String {
        val body = responseBody?.takeIf { it.isNotBlank() }?.excerpt(MAX_ERROR_BODY_LENGTH)
        return if (body != null) "HTTP $statusCode: $body" else "HTTP $statusCode"
    }

    private fun requestFactory(): ClientHttpRequestFactory {
        val httpClient =
            HttpClient
                .newBuilder()
                .connectTimeout(Duration.ofSeconds(CONNECT_TIMEOUT_SECONDS))
                .build()
        return JdkClientHttpRequestFactory(httpClient).apply {
            setReadTimeout(Duration.ofSeconds(READ_TIMEOUT_SECONDS))
        }
    }

    companion object {
        private const val CONNECT_TIMEOUT_SECONDS = 10L
        private const val READ_TIMEOUT_SECONDS = 10L
        private const val MAX_ERROR_BODY_LENGTH = 2000
    }
}
