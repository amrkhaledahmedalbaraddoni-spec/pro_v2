package com.red.sovereign.social

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * عقد الربط الحي للموجز (FeedApi/FeedViewModel — تحقق 2026-09-27):
 * الدوال الخالصة التي يعتمد عليها العرض والتصويت والتنزيل.
 * (منطق ViewModel نفسه يحتاج Android؛ هنا نُثبّت العقد الذي لا يجوز
 * أن تكسره التعديلات الأخيرة.)
 */
class FeedHelpersTest {

    @Test
    fun `poll expiry accepts both server timestamp formats`() {
        assertTrue(isPollExpired("2000-01-01T00:00:00Z", nowMs = 1_800_000_000_000))
        assertFalse(isPollExpired("2999-01-01T00:00:00Z", nowMs = 1_800_000_000_000))
        assertTrue(isPollExpired("1000", nowMs = 2000))
        assertFalse(isPollExpired("9999", nowMs = 2000))
    }

    @Test
    fun `unparsable expiry never blocks voting`() {
        assertFalse(isPollExpired("garbage"))
        assertFalse(isPollExpired(null))
        assertFalse(isPollExpired("  "))
    }

    @Test
    fun `media path builds the authenticated route`() {
        assertEquals("/api/media/abc", postMediaPath("abc"))
        assertEquals("/api/media/abc", postMediaPath("/api/media/abc"))
    }

    @Test
    fun `cache extension stays safe for the private downloader`() {
        assertEquals("jpg", mediaCacheExtension("image/jpeg"))
        assertEquals("mp4", mediaCacheExtension("video/mp4"))
        assertEquals("bin", mediaCacheExtension(""))
        assertEquals("bin", mediaCacheExtension("weird"))
    }
}
