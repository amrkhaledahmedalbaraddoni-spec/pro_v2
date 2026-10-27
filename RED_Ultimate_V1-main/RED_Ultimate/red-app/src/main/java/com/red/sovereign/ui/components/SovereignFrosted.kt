package com.red.sovereign.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.red.sovereign.ui.theme.AppThemeState
import com.red.sovereign.ui.theme.GlassPerformance
import com.red.sovereign.ui.theme.SovereignColors
import com.red.sovereign.ui.theme.SovereignGlass
import com.red.sovereign.ui.theme.SovereignGlassStyle
import com.red.sovereign.ui.theme.SovereignGlassTier
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.HazeStyle
import dev.chrisbanes.haze.HazeTint
import dev.chrisbanes.haze.hazeEffect
import dev.chrisbanes.haze.hazeSource

/**
 * الزجاج المثلج الحقيقي — Haze 1.x (ضبابية خلفية مُسرّعة عتاديًا).
 *
 * القاعدة (2026): البلور للطبقة العلوية فقط (أشرطة/حوارات)، وطبقة المحتوى
 * تحته موسومة بـ [sovereignHazeSource]. عند تعطيل liquidGlass أو اختيار
 * الأداء الاقتصادي يعود كل شيء تلقائيًا للسطح المعتم — لا شاشة مكسورة.
 *
 * مصدر الحقيقة: كل الأرقام (الأنماط/الشدة/العتامة/الحدود) تُبنى حصريًا من
 * سياسة [SovereignGlass.recipe] والإعدادات الحية في [AppThemeState] — لا
 * أرقام زجاج مكرّرة هنا.
 *
 * LEGENDARY FIX (انهيار دخول مثبت على الجهاز): Haze يتطلب backgroundColor صريحًا
 * لمسار البلور الموسع — غيابه يرمي IllegalArgumentException عند أول رسم بعد
 * الدخول. يُحفظ عبر تمرير خلفية معتمة دائمًا إلى [HazeStyle.backgroundColor].
 */

/** حالة ضباب واحدة لكل شاشة — تُمرَّر للشريط والمحتوى معًا. */
@Composable
fun rememberSovereignHaze(): HazeState = remember { HazeState() }

/** نمط Haze لكل طبقة — يُبنى حصريًا من سياسة [SovereignGlass] والإعدادات الحية. */
fun sovereignHazeStyle(tier: Dp, isDark: Boolean): HazeStyle {
    // البلوري الفائق (تجريبي) يعتمد Haze 2 — هنا يُخفَّض إلى أقرب نمط مدعوم
    // حتى لا يظهر نمط لم يدخل حيّز التنفيذ على هذا المحرك.
    val effectiveStyle = when (AppThemeState.glassStyle) {
        SovereignGlassStyle.CRYSTAL -> SovereignGlassStyle.CLEAR
        else -> AppThemeState.glassStyle
    }
    val recipe = SovereignGlass.recipe(
        style = effectiveStyle,
        intensity = AppThemeState.glassIntensity.value,
        tier = tier,
        isDark = isDark,
        enabled = AppThemeState.liquidGlassEnabled,
        highContrast = AppThemeState.highContrast,
        reduceMotion = AppThemeState.reduceMotion,
        performance = AppThemeState.glassPerformance
    )
    // مزج قوي: صبغة السياسة فوق صبغة الأساس المجربة (GlassBgLiquid) — يحفظ
    // ثبات اللون فوق الأسطح المتغيرة ويمنع شفافية مفاجئة عند alpha منخفض.
    val blendedTint = lerp(
        SovereignColors.GlassBgLiquid.copy(alpha = 0.55f),
        recipe.tint,
        0.35f
    )
    // LEGENDARY FIX: خلفية صريحة دائمًا — بلاها انهيار الدخول المثبت سابقًا.
    val background = if (isDark) {
        SovereignColors.ObsidianDeep.copy(alpha = 0.72f)
    } else {
        Color.White.copy(alpha = 0.72f)
    }
    return HazeStyle(
        backgroundColor = background,
        tint = HazeTint(blendedTint),
        blurRadius = recipe.tier
    )
}

/** وسم محتوى الخلفية كمصدر للضبابية — يُوضع على حاوية المحتوى تحت الشريط. */
fun Modifier.sovereignHazeSource(
    state: HazeState?,
    enabled: Boolean = AppThemeState.liquidGlassEnabled
): Modifier = if (enabled && state != null) hazeSource(state) else this

/**
 * تأثير الضبابية على الشريط نفسه — يُوضع على Surface الشريط.
 *
 * isDark الحقيقي يُشتق من [AppThemeState.isDarkNow] (الوضع الفاتح كان يرسم
 * زجاجًا داكنًا خطأً في الإصدارات السابقة). بلا حالة (null) أو مع تعطيل
 * الزجاج أو الأداء الاقتصادي: يُعاد المُعدِّل كما هو (السطح المعتم الأصلي).
 */
fun Modifier.sovereignHazeEffect(
    state: HazeState?,
    tier: Dp = SovereignGlassTier.NavBar,
    isDark: Boolean = true,
    enabled: Boolean = AppThemeState.liquidGlassEnabled
): Modifier {
    if (!enabled || state == null) return this
    if (AppThemeState.glassPerformance == GlassPerformance.ECO) return this
    val effectiveDark = if (isDark) AppThemeState.isDarkNow else isDark
    return hazeEffect(state = state, style = sovereignHazeStyle(tier, effectiveDark))
}
