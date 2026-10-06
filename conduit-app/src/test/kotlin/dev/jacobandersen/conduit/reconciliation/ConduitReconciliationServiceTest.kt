package dev.jacobandersen.conduit.reconciliation

import dev.jacobandersen.conduit.config.ReconciliationProperties
import dev.jacobandersen.conduit.syndication.service.SyndicationReconciliationService
import dev.jacobandersen.content.client.ChangedPostsPage
import dev.jacobandersen.content.client.ContentReadClient
import dev.jacobandersen.content.client.PostDto
import dev.jacobandersen.mf24j.Mf2Object
import dev.jacobandersen.mf24j.Mf2Value
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.beans.factory.ObjectProvider

class ConduitReconciliationServiceTest {
    private val client: ContentReadClient = mock()
    private val provider: ObjectProvider<ContentReadClient> = mock()
    private val reconciliation: SyndicationReconciliationService = mock()
    private val cursorService: ReconciliationCursorService = mock()
    private val properties = ReconciliationProperties(pageSize = 100, maxPages = 10)

    private val service =
        ConduitReconciliationService(provider, reconciliation, cursorService, properties)

    private fun post(
        slug: String,
        status: String = "PUBLISHED",
        visibility: String = "PUBLIC",
    ): PostDto =
        PostDto(
            id = slug,
            slug = slug,
            url = "https://example.com/2024/01/01/$slug",
            h = "h-entry",
            status = status,
            visibility = visibility,
            deleted = false,
            categories = emptyList(),
            version = 1,
            post = Mf2Object(type = listOf("h-entry"), properties = mapOf("content" to listOf(Mf2Value.String(slug)))),
        )

    @Test
    fun `reconciles every changed post`() {
        whenever(provider.ifAvailable).thenReturn(client)
        val publicPost = post("public")
        val draft = post("draft", status = "DRAFT")
        whenever(client.changedSince(null, 100)).thenReturn(ChangedPostsPage(listOf(publicPost, draft), null))
        whenever(cursorService.get(ConduitReconciliationService.CURSOR_ID)).thenReturn(null)

        service.reconcile()

        verify(reconciliation).reconcile(eq(publicPost))
        verify(reconciliation).reconcile(eq(draft))
    }

    @Test
    fun `advances the persisted cursor across pages`() {
        whenever(provider.ifAvailable).thenReturn(client)
        whenever(cursorService.get(ConduitReconciliationService.CURSOR_ID)).thenReturn(null)
        whenever(client.changedSince(null, 100))
            .thenReturn(ChangedPostsPage(listOf(post("one")), nextCursor = "100"))
        whenever(client.changedSince("100", 100))
            .thenReturn(ChangedPostsPage(listOf(post("two")), nextCursor = null))

        service.reconcile()

        verify(cursorService).set(ConduitReconciliationService.CURSOR_ID, "100")
        verify(reconciliation, times(2)).reconcile(any<PostDto>())
    }

    @Test
    fun `does nothing when the content read client is not configured`() {
        whenever(provider.ifAvailable).thenReturn(null)

        service.reconcile()

        verify(client, never()).changedSince(any(), any())
        verify(reconciliation, never()).reconcile(any<PostDto>())
    }
}
