package dev.jacobandersen.conduit.syndication.service

import dev.jacobandersen.conduit.config.SyndicationProperties
import dev.jacobandersen.conduit.event.SyndicationEvent
import dev.jacobandersen.conduit.event.SyndicationEventPublisher
import dev.jacobandersen.conduit.event.SyndicationEventType
import dev.jacobandersen.conduit.syndication.SyndicationAction
import dev.jacobandersen.conduit.syndication.SyndicationHttpClient
import dev.jacobandersen.conduit.syndication.SyndicationSendResult
import dev.jacobandersen.conduit.syndication.domain.PostSyndication
import dev.jacobandersen.content.event.ContentPostEvent
import dev.jacobandersen.content.event.ContentPostEventType
import dev.jacobandersen.microformats2.Mf2Object
import dev.jacobandersen.microformats2.Mf2Value
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.given
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import java.time.Instant
import java.util.UUID

class SyndicationReconciliationServiceTest {
    private val postId: UUID = UUID.randomUUID()
    private val sourceUrl = "https://example.com/2024/01/01/s"

    private val properties =
        SyndicationProperties(
            targets =
                listOf(
                    SyndicationProperties.Target(
                        uid = "t1",
                        name = "Target One",
                        endpoint = "https://target.example/endpoint",
                        actions = setOf(SyndicationAction.CREATE, SyndicationAction.DELETE),
                    ),
                ),
        )
    private val postSyndicationService: PostSyndicationService = mock()
    private val httpClient: SyndicationHttpClient = mock()
    private val eventPublisher: SyndicationEventPublisher = mock()

    private val service =
        SyndicationReconciliationService(properties, postSyndicationService, httpClient, eventPublisher)

    private fun event(
        status: String = "PUBLISHED",
        visibility: String = "PUBLIC",
        deleted: Boolean = false,
        targets: List<String> = listOf("t1"),
        previousUrl: String? = null,
    ): ContentPostEvent =
        ContentPostEvent(
            eventType = ContentPostEventType.CREATED,
            id = postId.toString(),
            slug = "s",
            url = sourceUrl,
            previousUrl = previousUrl,
            h = "h-entry",
            type = "note",
            status = status,
            visibility = visibility,
            deleted = deleted,
            categories = emptyList(),
            version = 1,
            post =
                Mf2Object(
                    type = listOf("h-entry"),
                    properties = mapOf("content" to listOf(Mf2Value.String("hello"))),
                ),
            syndicationTargets = targets,
        )

    @Test
    fun `public post with a desired target is syndicated and recorded`() {
        given(postSyndicationService.findByPostId(postId)).willReturn(emptyList())
        given(httpClient.sendCreate(any(), any()))
            .willReturn(SyndicationSendResult.Success(201, "https://target.example/copy/1"))

        service.reconcile(event())

        verify(postSyndicationService).record(postId, "t1")
        verify(postSyndicationService).recordOutcome(postId, "t1", "https://target.example/copy/1")
        verify(eventPublisher).publish(
            SyndicationEvent(
                SyndicationEventType.SYNDICATED,
                postId.toString(),
                "t1",
                "Target One",
                "https://target.example/copy/1",
                sourceUrl,
            ),
        )
    }

    @Test
    fun `already-syndicated public post is left alone`() {
        val record = PostSyndication(UUID.randomUUID(), postId, "t1", "https://target.example/copy/1", Instant.now())
        given(postSyndicationService.findByPostId(postId)).willReturn(listOf(record))

        service.reconcile(event())

        verify(httpClient, org.mockito.kotlin.never()).sendCreate(any(), any())
    }

    @Test
    fun `dropping a target retracts and forgets its copy`() {
        val record = PostSyndication(UUID.randomUUID(), postId, "t1", "https://target.example/copy/1", Instant.now())
        given(postSyndicationService.findByPostId(postId)).willReturn(listOf(record))
        given(httpClient.sendDelete(any(), any())).willReturn(SyndicationSendResult.Success(204, null))

        service.reconcile(event(targets = emptyList()))

        verify(httpClient).sendDelete(any(), eq(sourceUrl))
        verify(postSyndicationService).remove(postId, "t1")
        verify(eventPublisher).publish(
            SyndicationEvent(SyndicationEventType.RETRACTED, postId.toString(), "t1", "Target One", null, sourceUrl),
        )
    }

    @Test
    fun `non-public post retracts copies but retains the desired target`() {
        val record = PostSyndication(UUID.randomUUID(), postId, "t1", "https://target.example/copy/1", Instant.now())
        given(postSyndicationService.findByPostId(postId)).willReturn(listOf(record))
        given(httpClient.sendDelete(any(), any())).willReturn(SyndicationSendResult.Success(204, null))

        service.reconcile(event(status = "DRAFT"))

        verify(httpClient).sendDelete(any(), eq(sourceUrl))
        verify(postSyndicationService).clearOutcome(postId, "t1")
        verify(postSyndicationService, org.mockito.kotlin.never()).remove(any(), any())
    }

    @Test
    fun `a retryable create failure is surfaced for retry`() {
        given(postSyndicationService.findByPostId(postId)).willReturn(emptyList())
        given(httpClient.sendCreate(any(), any())).willReturn(SyndicationSendResult.Failure(503, "HTTP 503", retryable = true))

        assertThrows(SyndicationException::class.java) { service.reconcile(event()) }

        verify(postSyndicationService, never()).recordOutcome(any(), any(), any())
        verify(eventPublisher, never()).publish(any())
    }

    @Test
    fun `a permanent create failure is dropped without retry`() {
        given(postSyndicationService.findByPostId(postId)).willReturn(emptyList())
        given(httpClient.sendCreate(any(), any())).willReturn(SyndicationSendResult.Failure(400, "HTTP 400", retryable = false))

        service.reconcile(event())

        verify(postSyndicationService, never()).recordOutcome(any(), any(), any())
        verify(eventPublisher, never()).publish(any())
    }

    @Test
    fun `a retryable retraction failure keeps the record and does not emit`() {
        val record = PostSyndication(UUID.randomUUID(), postId, "t1", "https://target.example/copy/1", Instant.now())
        given(postSyndicationService.findByPostId(postId)).willReturn(listOf(record))
        given(httpClient.sendDelete(any(), any())).willReturn(SyndicationSendResult.Failure(503, "HTTP 503", retryable = true))

        assertThrows(SyndicationException::class.java) { service.reconcile(event(targets = emptyList())) }

        verify(postSyndicationService, never()).remove(any(), any())
        verify(eventPublisher, never()).publish(any())
    }

    @Test
    fun `a 404 on retraction is treated as already gone`() {
        val record = PostSyndication(UUID.randomUUID(), postId, "t1", "https://target.example/copy/1", Instant.now())
        given(postSyndicationService.findByPostId(postId)).willReturn(listOf(record))
        given(httpClient.sendDelete(any(), any())).willReturn(SyndicationSendResult.Failure(404, "HTTP 404", retryable = false))

        service.reconcile(event(targets = emptyList()))

        verify(postSyndicationService).remove(postId, "t1")
        verify(eventPublisher).publish(any())
    }
}
