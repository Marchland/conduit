package dev.jacobandersen.conduit.websub

import dev.jacobandersen.conduit.config.WebsubProperties
import dev.jacobandersen.conduit.util.HttpUtil
import dev.jacobandersen.conduit.util.StringUtil.excerpt
import org.springframework.http.MediaType
import org.springframework.http.client.ClientHttpRequestFactory
import org.springframework.http.client.JdkClientHttpRequestFactory
import org.springframework.stereotype.Service
import org.springframework.util.LinkedMultiValueMap
import org.springframework.web.client.RestClient
import org.springframework.web.client.RestClientException
import org.springframework.web.client.RestClientResponseException
import java.net.http.HttpClient
import java.time.Duration

sealed interface PublishResult {
    data class Success(
        val statusCode: Int,
    ) : PublishResult

    data class Failure(
        val statusCode: Int?,
        val message: String,
        val retryable: Boolean,
    ) : PublishResult
}

@Service
class WebsubHttpClient(
    private val config: WebsubProperties,
) {
    private val client: RestClient =
        RestClient
            .builder()
            .requestFactory(requestFactory(config))
            .requestInterceptor(WebsubHttpLoggingInterceptor())
            .build()

    internal fun publish(
        hubUrl: String,
        topicUrl: String,
    ): PublishResult {
        val payload = LinkedMultiValueMap<String, String>()
        payload.add("hub.mode", "publish")
        payload.add("hub.url", topicUrl)

        return try {
            val statusCode =
                client
                    .post()
                    .uri(hubUrl)
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(payload)
                    .retrieve()
                    .toBodilessEntity()
                    .statusCode
                    .value()

            if (statusCode in 200..299) {
                PublishResult.Success(statusCode)
            } else {
                PublishResult.Failure(statusCode, "HTTP $statusCode", HttpUtil.isTransientStatus(statusCode))
            }
        } catch (e: RestClientResponseException) {
            val statusCode = e.statusCode.value()
            PublishResult.Failure(statusCode, describeHttpError(statusCode, e.responseBodyAsString), HttpUtil.isTransientStatus(statusCode))
        } catch (e: RestClientException) {
            PublishResult.Failure(null, e.message ?: e::class.simpleName ?: "Request failed", true)
        }
    }

    private fun describeHttpError(
        statusCode: Int,
        responseBody: String?,
    ): String {
        val body = responseBody?.takeIf { it.isNotBlank() }?.excerpt(MAX_ERROR_BODY_LENGTH)
        return if (body != null) "HTTP $statusCode: $body" else "HTTP $statusCode"
    }

    private fun requestFactory(config: WebsubProperties): ClientHttpRequestFactory {
        val httpClient =
            HttpClient
                .newBuilder()
                .connectTimeout(Duration.ofSeconds(config.connectTimeoutSeconds))
                .build()
        return JdkClientHttpRequestFactory(httpClient).apply {
            setReadTimeout(Duration.ofSeconds(config.readTimeoutSeconds))
        }
    }

    companion object {
        const val MAX_ERROR_BODY_LENGTH = 2000
    }
}
