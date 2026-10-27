package com.red.sovereign.ui.theme

import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * حارس سياسة الزجاج السيادي — يثبت عقد [SovereignGlass.recipe]:
 * الأنماط التلقائية، الأداء، حصر الشدة، وصولية التباين، والوضع الفاتح.
 * كل الأرقام هنا هي العقد المعلن — تغييرها دون تحديث الاختبار يكسر البناء.
 */
class SovereignGlassPolicyTest {

    @Test
    fun testGlassDisabledReturnsInactive() {
        val recipe = SovereignGlass.recipe(enabled = false)
        assertFalse(recipe.active)
        assertEquals(0.dp, recipe.tier)
    }

    @Test
    fun testAutoStylePicksRegularForSheets() {
        val recipe = SovereignGlass.recipe(
            style = SovereignGlassStyle.AUTO,
            tier = SovereignGlassTier.Sheet
        )
        assertTrue(recipe.active)
        assertEquals(SovereignGlassStyle.REGULAR, recipe.style)
    }

    @Test
    fun testAutoStylePicksClearForNavBar() {
        val recipe = SovereignGlass.recipe(
            style = SovereignGlassStyle.AUTO,
            tier = SovereignGlassTier.NavBar
        )
        assertTrue(recipe.active)
        assertEquals(SovereignGlassStyle.CLEAR, recipe.style)
    }

    @Test
    fun testHighContrastIncreasesBorderAndOpacity() {
        val recipe = SovereignGlass.recipe(
            style = SovereignGlassStyle.REGULAR,
            highContrast = true
        )
        assertTrue(recipe.active)
        assertEquals(1.5.dp, recipe.borderWidth)
        assertTrue(recipe.tint.alpha >= 0.85f)
    }

    @Test
    fun testEcoPerformanceReturnsSolidFallback() {
        val dark = SovereignGlass.recipe(performance = GlassPerformance.ECO, isDark = true)
        assertFalse("الوضع الاقتصادي يجب أن يعطل البلور", dark.active)
        assertEquals(SovereignColors.ObsidianDeep, dark.fallback)
        val light = SovereignGlass.recipe(performance = GlassPerformance.ECO, isDark = false)
        assertEquals(SovereignColors.LightBase, light.fallback)
    }

    @Test
    fun testBalancedPerformanceScalesBlurByPointSeven() {
        // NavBar = 8dp — BALANCED يضرب في 0.7 فيكون 5.6dp
        val recipe = SovereignGlass.recipe(
            style = SovereignGlassStyle.REGULAR,
            tier = SovereignGlassTier.NavBar,
            performance = GlassPerformance.BALANCED
        )
        assertEquals(5.6f, recipe.tier.value, 0.001f)
    }

    @Test
    fun testIntensityIsClampedToDeclaredBounds() {
        // فوق الحد الأقصى: NavBar 8 × 1.4 = 11.2dp
        val tooHigh = SovereignGlass.recipe(intensity = 5f, tier = SovereignGlassTier.NavBar)
        assertEquals(SovereignGlass.MaxIntensity, tooHigh.tier.value / SovereignGlassTier.NavBar.value, 0.001f)
        // تحت الحد الأدنى: 8 × 0.6 = 4.8dp
        val tooLow = SovereignGlass.recipe(intensity = 0.1f, tier = SovereignGlassTier.NavBar)
        assertEquals(SovereignGlass.MinIntensity, tooLow.tier.value / SovereignGlassTier.NavBar.value, 0.001f)
    }

    @Test
    fun testLightModeAlphasMatchContract() {
        val clearLight = SovereignGlass.recipe(
            style = SovereignGlassStyle.CLEAR,
            isDark = false,
            tier = SovereignGlassTier.NavBar
        )
        // Compose يخزن الألفا 8-bit (128/255 = 0.50196) — التفاوت 0.005 يستوعب التقريب.
        assertEquals(0.50f, clearLight.tint.alpha, 0.005f)
        val regularLightHighContrast = SovereignGlass.recipe(
            style = SovereignGlassStyle.REGULAR,
            isDark = false,
            highContrast = true
        )
        assertEquals(0.90f, regularLightHighContrast.tint.alpha, 0.005f)
    }

    @Test
    fun testEnumParsingIsSafeAndDefaultsToAuto() {
        assertEquals(SovereignGlassStyle.CRYSTAL, SovereignGlassStyle.fromName("crystal"))
        assertEquals(SovereignGlassStyle.REGULAR, SovereignGlassStyle.fromName("REGULAR"))
        assertEquals(SovereignGlassStyle.AUTO, SovereignGlassStyle.fromName("غير معروف"))
        assertEquals(SovereignGlassStyle.AUTO, SovereignGlassStyle.fromName(null))
        assertEquals(GlassIntensityLevel.DEEP, GlassIntensityLevel.fromName("DEEP"))
        assertEquals(GlassIntensityLevel.BALANCED, GlassIntensityLevel.fromName(null))
        assertEquals(GlassPerformance.ECO, GlassPerformance.fromName("eco"))
        assertEquals(GlassPerformance.FULL, GlassPerformance.fromName("غثاء"))
    }

    @Test
    fun testIntensityLevelsCarryPolicyValues() {
        assertEquals(0.85f, GlassIntensityLevel.SUBTLE.value, 0.001f)
        assertEquals(1.0f, GlassIntensityLevel.BALANCED.value, 0.001f)
        assertEquals(1.2f, GlassIntensityLevel.DEEP.value, 0.001f)
    }
}
