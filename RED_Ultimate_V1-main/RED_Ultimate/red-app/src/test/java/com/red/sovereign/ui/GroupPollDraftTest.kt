package com.red.sovereign.ui

import com.red.sovereign.core.InlinePoll
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * حارس مسودة الاستطلاع المتقدم [GroupPollDraft] — يثبت سلسلة الإنشاء:
 * المسودة تحمل الخيارات الثلاثة (مجهول/متعدد/مؤقت) وتتحول إلى InlinePoll
 * بنفس القيم تماماً (هذا ما يُرسل عبر E2EE في المجموعات والشات الخاص).
 */
class GroupPollDraftTest {

    private fun draft(advanced: Boolean = true) = GroupPollDraft(
        question = "ما أفضل ميزة؟",
        options = listOf("خصوصية", "سرعة"),
        endTime = if (advanced) 1_000_000L else null,
        isAnonymous = advanced,
        allowMultiple = advanced
    )

    @Test
    fun `المسودة المتقدمة تتحول لInlinePoll بنفس القيم`() {
        val d = draft()
        val poll = InlinePoll(
            question = d.question,
            options = d.options,
            pollId = "poll-1",
            endTime = d.endTime,
            isAnonymous = d.isAnonymous,
            allowMultiple = d.allowMultiple
        )
        assertEquals(d.question, poll.question)
        assertEquals(d.options, poll.options)
        assertEquals(d.endTime, poll.endTime)
        assertEquals(d.isAnonymous, poll.isAnonymous)
        assertEquals(d.allowMultiple, poll.allowMultiple)
        assertTrue(poll.isExpired(2_000_000L))
        assertFalse(poll.isExpired(500_000L))
    }

    @Test
    fun `المسودة البسيطة بلا مؤقت وبلا خيارات متقدمة`() {
        val d = draft(advanced = false)
        assertNull(d.endTime)
        assertFalse(d.isAnonymous)
        assertFalse(d.allowMultiple)
    }
}
