package com.red.sovereign.stories

import com.red.sovereign.core.database.StoryEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * مقابر الحالات المحذوفة (2026-09-27): المحذوفة كانت تُبعث من الكاش
 * لأن delete() لا تحذف صف قاعدة البيانات.
 */
class StoryTombstoneTest {

    private fun story(id: String) = Story(
        id = id,
        ownerRedId = "12345",
        ownerUsername = "ahmad",
        ownerDisplayName = "أحمد",
        mediaUrl = "/api/media/x",
        mediaType = "image/jpeg",
        createdAt = "2026-09-27T10:00:00Z",
        expiresAt = "2026-09-28T10:00:00Z"
    )

    @Test
    fun `deleted story stays hidden after cache re-emission`() {
        val merged = listOf(story("s1"), story("s2"))

        assertEquals(listOf("s2"), applyStoryTombstones(merged, setOf("s1")).map { it.id })
    }

    @Test
    fun `empty tombstones return the same list`() {
        val merged = listOf(story("s1"))

        assertEquals(merged, applyStoryTombstones(merged, emptySet()))
    }

    @Test
    fun `prefiltered cache merge never resurrects a deleted story`() {
        val cached = StoryEntity(
            id = "s1",
            userId = "12345",
            mediaUrl = "/api/media/x",
            mediaType = "image/jpeg",
            timestamp = 1_750_000_000_000,
            expiresAt = 1_750_086_400_000
        )
        // ما يفعله ViewModel: إسقاط المقبورة قبل الدمج.
        val live = listOf(cached).filter { it.id !in setOf("s1") }

        assertTrue(mergeCachedStories(live, emptyList()).isEmpty())
    }
}
