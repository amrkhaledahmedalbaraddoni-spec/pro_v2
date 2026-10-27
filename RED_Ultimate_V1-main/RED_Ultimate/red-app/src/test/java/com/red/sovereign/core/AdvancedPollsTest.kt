package com.red.sovereign.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * حارس الاستطلاعات المتقدمة (P1-C): التوافق الرجعي لـInlinePoll الموسعة،
 * الإقفال الزمني، وعقد التحقق fail-fast لـpollVoteOptions في RichMessage.
 */
class AdvancedPollsTest {

    private fun poll(endTime: Long? = null, anon: Boolean = false, multi: Boolean = false) =
        InlinePoll(
            question = "س؟",
            options = listOf("أ", "ب", "ج"),
            pollId = "p1",
            endTime = endTime,
            isAnonymous = anon,
            allowMultiple = multi
        )

    @Test
    fun `الحقول الجديدة قيمها الافتراضية تحفظ توافق العملاء القدامى`() {
        val legacy = InlinePoll(question = "س", options = listOf("أ", "ب"), pollId = "p")
        assertNull(legacy.endTime)
        assertFalse(legacy.isAnonymous)
        assertFalse(legacy.allowMultiple)
        assertFalse(legacy.isExpired())
    }

    @Test
    fun `الإقفال الزمني يعمل بالاتجاهين`() {
        val now = System.currentTimeMillis()
        assertTrue(poll(endTime = now - 1000).isExpired(now))
        assertFalse(poll(endTime = now + 60_000).isExpired(now))
    }

    @Test
    fun `التشفير يمرر الحقول الثلاثة بالكامل`() {
        val original = poll(endTime = 1234567890L, anon = true, multi = true)
        val decoded = RichMessage.decode(RichMessage.encode(RichMessage(text = "", poll = original)))
        val restored = decoded!!.poll!!
        assertEquals(1234567890L, restored.endTime)
        assertTrue(restored.isAnonymous)
        assertTrue(restored.allowMultiple)
    }

    @Test
    fun `عميل قديم يرسل بلا حقول جديدة فيفكه العميل الجديد بلا كسر`() {
        // محاكاة JSON عميل قديم (بلا endTime/isAnonymous/allowMultiple/pollVoteOptions).
        val legacyJson = """{"version":1,"action":"MESSAGE","text":"","poll":{"question":"س","options":["أ","ب"],"pollId":"p"}}"""
        val decoded = RichMessage.decode(legacyJson.toByteArray(Charsets.UTF_8))
        val restored = decoded!!.poll!!
        assertNull(restored.endTime)
        assertFalse(restored.isAnonymous)
        assertFalse(restored.allowMultiple)
    }

    @Test
    fun `عميل جديد يبث مواصفة متقدمة وعميل قديم يتجاهل الحقول`() {
        // JSON الجديد يحمل الحقول — العميل القديم بـignoreUnknownKeys يقبلها متجاهلاً إياها.
        val rich = RichMessage(text = "", poll = poll(anon = true, multi = true, endTime = 99L))
        val json = String(RichMessage.encode(rich), Charsets.UTF_8)
        assertTrue(json.contains("endTime"))
        assertTrue(json.contains("isAnonymous"))
        assertTrue(json.contains("allowMultiple"))
        // وفكه بالسيريليزر الجديد يعيد كل شيء.
        val back = RichMessage.decode(json.toByteArray(Charsets.UTF_8))!!.poll!!
        assertEquals(99L, back.endTime)
    }

    @Test
    fun `عقد التصويت المتعدد يقبل الحالة الكاملة الصالحة`() {
        val vote = RichMessage(action = "POLL_VOTE", pollVoteOf = "p1", pollVoteOption = 1, pollVoteOptions = listOf(1, 3))
        assertEquals(listOf(1, 3), vote.pollVoteOptions)
    }

    @Test
    fun `عقد التصويت المتعدد يرفع الفهارس خارج النطاق`() {
        try {
            RichMessage(action = "POLL_VOTE", pollVoteOf = "p1", pollVoteOptions = listOf(1, 99))
            org.junit.Assert.fail("كان يجب الرفض")
        } catch (expected: IllegalArgumentException) {
            // fail-fast مطلوب
        }
    }

    @Test
    fun `عقد التصويت المتعدد يرفع المجموعة الفارغة`() {
        try {
            RichMessage(action = "POLL_VOTE", pollVoteOf = "p1", pollVoteOptions = emptyList())
            org.junit.Assert.fail("كان يجب الرفض")
        } catch (expected: IllegalArgumentException) {
            // فارغة = استخدم null للسحب
        }
    }

    @Test
    fun `التصويت الأحادي القديم يبقى صالحا بلا pollVoteOptions`() {
        val vote = RichMessage(action = "POLL_VOTE", pollVoteOf = "p1", pollVoteOption = 2)
        assertNull(vote.pollVoteOptions)
    }

    @Test
    fun `العقد يرفع POLL_VOTE بلا pollVoteOf كما كان`() {
        try {
            RichMessage(action = "POLL_VOTE", pollVoteOption = 1)
            org.junit.Assert.fail("كان يجب الرفض")
        } catch (expected: IllegalArgumentException) {
        }
    }
}
