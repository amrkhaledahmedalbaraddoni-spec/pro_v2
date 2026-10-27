package com.red.server.admin.controller

import com.red.server.admin.service.AdminService
import com.red.server.media.MediaService
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.data.mongodb.core.query.Query
import org.springframework.data.mongodb.core.query.Update
import org.springframework.http.HttpStatus
import org.springframework.security.core.Authentication
import java.util.UUID

/**
 * عقد `AdminStoriesController` — إشراف الأدمن على الحالات (القصص).
 * يُغطي: (1) الإحصاءات تعيد أرقامًا صريحة، (2) الإخفاء على حالة نشطة فقط
 * مع تدقيق إلزامي، (3) الحذف يزيل الوسائط ويسجل في التدقيق،
 * (4) غياب الحالة يعيد 404 ولا يكتب تدقيقًا.
 */
class AdminStoriesControllerTest {
    private val mongo: MongoTemplate = mock()
    private val media: MediaService = mock()
    private val adminService: AdminService = mock()
    private val controller = AdminStoriesController(mongo, media, adminService)

    private val auth: Authentication = mock<Authentication>().also {
        whenever(it.name).thenReturn(UUID.randomUUID().toString())
        whenever(it.principal).thenReturn("admin-tester")
    }

    @Test
    fun `overview returns explicit counters`() {
        whenever(mongo.count(any<Query>(), eq(StoryDocumentClass))).thenReturn(7L)
        whenever(mongo.count(any<Query>(), eq(StoryViewClass))).thenReturn(3L)

        val body = controller.overview().body!!

        assertEquals(7L, body["activeStories"])
        assertEquals(3L, body["viewsLast24h"])
    }

    @Test
    fun `hide returns 404 when nothing matched and skips audit`() {
        whenever(mongo.updateFirst(any<Query>(), any<Update>(), eq(StoryDocumentClass)))
            .thenReturn(com.mongodb.client.result.UpdateResult.acknowledged(0L, 0L, null))

        val response = controller.hide("missing-id", auth)

        assertEquals(HttpStatus.NOT_FOUND, response.statusCode)
        verify(adminService, never()).recordAudit(
            anyOrNull(), anyOrNull(), anyOrNull(), anyOrNull(), anyOrNull(),
            anyOrNull(), anyOrNull(), anyOrNull(), anyOrNull(), anyOrNull(), anyOrNull()
        )
    }

    @Test
    fun `hide audits with MODERATION severity on success`() {
        whenever(mongo.updateFirst(any<Query>(), any<Update>(), eq(StoryDocumentClass)))
            .thenReturn(com.mongodb.client.result.UpdateResult.acknowledged(1L, 1L, null))

        val response = controller.hide("story-1", auth)

        assertEquals(HttpStatus.OK, response.statusCode)
        verify(adminService).recordAudit(
            anyOrNull(), eq("admin-tester"), eq("STORY_HIDDEN"), eq("MODERATION"),
            eq("STORY"), eq("story-1"), anyOrNull(), anyOrNull(), anyOrNull(), anyOrNull(), eq("WARNING")
        )
    }

    @Test
    fun `unhide returns 404 when story was not hidden`() {
        whenever(mongo.updateFirst(any<Query>(), any<Update>(), eq(StoryDocumentClass)))
            .thenReturn(com.mongodb.client.result.UpdateResult.acknowledged(0L, 0L, null))

        val response = controller.unhide("story-1", auth)

        assertEquals(HttpStatus.NOT_FOUND, response.statusCode)
        assertTrue((response.body as Map<*, *>)["error"] == "STORY_NOT_HIDDEN")
    }

    @Test
    fun `delete removes media and views then audits`() {
        val story = com.red.server.stories.StoryDocument(
            id = "story-9",
            ownerId = UUID.randomUUID().toString(),
            ownerRedId = "1234567890",
            ownerUsername = "owner",
            ownerDisplayName = "Owner",
            mediaKey = "users/x/stories/m.jpg",
            mediaType = "image/jpeg",
            caption = null,
            expiresAt = java.time.Instant.now().plusSeconds(3600)
        )
        whenever(mongo.findOne(any<Query>(), eq(StoryDocumentClass))).thenReturn(story)
        whenever(mongo.updateFirst(any<Query>(), any<Update>(), eq(StoryDocumentClass)))
            .thenReturn(com.mongodb.client.result.UpdateResult.acknowledged(1L, 1L, null))

        val response = controller.delete("story-9", auth)

        assertEquals(HttpStatus.OK, response.statusCode)
        verify(media).delete("users/x/stories/m.jpg")
        verify(mongo).remove(any<Query>(), eq(StoryViewClass))
        verify(adminService).recordAudit(
            anyOrNull(), eq("admin-tester"), eq("STORY_DELETED"), eq("MODERATION"),
            eq("STORY"), eq("story-9"), anyOrNull(), anyOrNull(), anyOrNull(), anyOrNull(), eq("WARNING")
        )
    }

    @Test
    fun `delete with blank media key never touches media service`() {
        val story = com.red.server.stories.StoryDocument(
            id = "story-text",
            ownerId = UUID.randomUUID().toString(),
            ownerRedId = "1234567890",
            ownerUsername = "owner",
            ownerDisplayName = "Owner",
            mediaKey = "",
            mediaType = "TEXT",
            caption = "نص فقط",
            expiresAt = java.time.Instant.now().plusSeconds(3600)
        )
        whenever(mongo.findOne(any<Query>(), eq(StoryDocumentClass))).thenReturn(story)
        whenever(mongo.updateFirst(any<Query>(), any<Update>(), eq(StoryDocumentClass)))
            .thenReturn(com.mongodb.client.result.UpdateResult.acknowledged(1L, 1L, null))

        controller.delete("story-text", auth)

        verify(media, never()).delete(any<String>())
    }

    private companion object {
        val StoryDocumentClass = com.red.server.stories.StoryDocument::class.java
        val StoryViewClass = com.red.server.stories.StoryView::class.java
    }
}
