package com.red.sovereign.settings

import com.red.sovereign.ui.theme.GlassIntensityLevel
import com.red.sovereign.ui.theme.GlassPerformance
import com.red.sovereign.ui.theme.SovereignGlassStyle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * حارس إعدادات الزجاج (2026): كل مفتاح من مفاتيح الزجاج الثلاثة يجب أن
 * يُقرأ ويُكتب معًا، ومجموعات القيم المسموحة يجب أن تطابق تعدادات
 * [SovereignGlassStyle]/[GlassIntensityLevel]/[GlassPerformance] — أي انحراف
 * بين الإعدادات والمحرك يكسر البناء هنا بدل أن يضيع بصمت عند المستخدم.
 */
class GlassSettingsPolicyTest {

    private fun source(): String {
        val candidates = listOf(
            "src/main/java/com/red/sovereign/settings/SettingsViewModel.kt",
            "red-app/src/main/java/com/red/sovereign/settings/SettingsViewModel.kt",
            "../red-app/src/main/java/com/red/sovereign/settings/SettingsViewModel.kt",
            "RED_Ultimate_V1-main/RED_Ultimate/red-app/src/main/java/com/red/sovereign/settings/SettingsViewModel.kt"
        )
        val file = candidates.map { File(it) }.firstOrNull { it.isFile }
        assertTrue("تعذّر العثور على SettingsViewModel.kt", file != null)
        return file!!.readText()
    }

    private fun section(text: String, start: String, end: String): String {
        val from = text.indexOf(start)
        val to = text.indexOf(end)
        assertTrue("تعذّر تحديد المقطع $start", from in 0 until to)
        return text.substring(from, to)
    }

    @Test
    fun `مفاتيح الزجاج الثلاثة تُقرأ وتُكتب معا`() {
        val src = source()
        val read = section(src, "fun SharedPreferences.readSettings", "fun SharedPreferences.writeSettings")
        val write = section(src, "fun SharedPreferences.writeSettings", "class SettingsViewModel")
        listOf("GLASS_STYLE", "GLASS_INTENSITY", "GLASS_PERFORMANCE").forEach { key ->
            assertTrue("المفتاح $key لا يُقرأ", read.contains("Keys.$key"))
            assertTrue("المفتاح $key لا يُكتب", write.contains("Keys.$key"))
        }
    }

    @Test
    fun `مجموعات القيم المسموحة تطابق معرفات التعدادات`() {
        assertEquals(
            SovereignGlassStyle.entries.map { it.id }.toSet(),
            GLASS_STYLES
        )
        assertEquals(
            GlassIntensityLevel.entries.map { it.id }.toSet(),
            GLASS_INTENSITIES
        )
        assertEquals(
            GlassPerformance.entries.map { it.id }.toSet(),
            GLASS_PERFORMANCES
        )
    }

    @Test
    fun `المحفوظ يبقى ضمن القيم المسموحة بعد التطهير`() {
        // القيم غير المعروفة تعود للافتراضي عبر fromName — عقد الـsetters.
        assertEquals(SovereignGlassStyle.AUTO.id, SovereignGlassStyle.fromName("HACKED").id)
        assertEquals(GlassIntensityLevel.BALANCED.id, GlassIntensityLevel.fromName("").id)
        assertEquals(GlassPerformance.FULL.id, GlassPerformance.fromName("x_x").id)
    }

    @Test
    fun `الافتراضيات تتطابق بين التخزين والتعدادات`() {
        assertEquals(SovereignGlassStyle.AUTO.id, YounesSettings().glassStyle)
        assertEquals(GlassIntensityLevel.BALANCED.id, YounesSettings().glassIntensity)
        assertEquals(GlassPerformance.FULL.id, YounesSettings().glassPerformance)
    }
}
