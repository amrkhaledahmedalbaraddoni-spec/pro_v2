package com.red.sovereign.ui.theme

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * حارس ألوان أسماء المرسلين — يثبت عقد المصدر المركزي [senderNameColor]:
 * ثبات اللون لكل معرف (نفس الشخص = نفس اللون عبر الشاشات والجلسات)، وضمن
 * حدود اللوحة مهما كان الفهرس، وتوزيع لا ينهار على معرفات فارغة/طرفية.
 * أي انحراف في التواقيع يكسر البناء هنا بدل أن يضيع بصمتاً في الواجهات.
 */
class NameColorsTest {

    @Test
    fun `نفس المعرف يعطي نفس اللون دائما`() {
        val id = "red-abc-123"
        val first = senderNameColor(senderNameColorIndex(id))
        repeat(20) {
            assertEquals(first, senderNameColor(senderNameColorIndex(id)))
        }
    }

    @Test
    fun `الفهرس يبقى ضمن حدود اللوحة لكل المدخلات`() {
        val ids = listOf("", "a", "x_younes", "RED-UPPER-CASE-9999", "مستخدم-عربي-🚀", "🚀".repeat(50))
        ids.forEach { id ->
            val idx = senderNameColorIndex(id)
            assertTrue("فهرس خارج اللوحة للمعرف: $id", idx in 0..6)
        }
    }

    @Test
    fun `المعرفات القصيرة المختلفة تحمل فهارس صحيحة`() {
        // عقد التوقيع: senderNameColorIndex يقبل أي String بلا استثناءات.
        assertEquals(senderNameColorIndex(""), senderNameColorIndex(""))
        assertTrue(senderNameColorIndex("u1") in 0..6)
        assertTrue(senderNameColorIndex("u2") in 0..6)
    }
}
