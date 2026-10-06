package dev.jacobandersen.conduit.websub

import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.http.HttpRequest
import org.springframework.http.client.ClientHttpRequestExecution
import org.springframework.http.client.ClientHttpRequestInterceptor
import org.springframework.http.client.ClientHttpResponse

private val logger = KotlinLogging.logger {}

class WebsubHttpLoggingInterceptor : ClientHttpRequestInterceptor {
    override fun intercept(
        request: HttpRequest,
        body: ByteArray,
        execution: ClientHttpRequestExecution,
    ): ClientHttpResponse {
        logger.info { "--> WebSub: ${request.method} ${request.uri}" }
        val start = System.currentTimeMillis()
        val response = execution.execute(request, body)
        logger.info { "<-- WebSub (in ${System.currentTimeMillis() - start} ms): ${response.statusCode}" }
        return response
    }
}
