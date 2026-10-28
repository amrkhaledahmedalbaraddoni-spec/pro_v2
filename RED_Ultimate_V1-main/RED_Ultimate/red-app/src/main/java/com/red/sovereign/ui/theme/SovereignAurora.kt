package com.red.sovereign.ui.theme

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * خلفية «الشفق السيادي» — طبقة هالات متحركة ببطء فوق الأساس المرفوع.
 *
 * ## المقارنة مع المنافسين
 * - واتساب: بيج ثابت + نمط مكرر (دوحي/هندسي) — بلا عمق، ويبهت على OLED.
 * - تيليجرام: تدرّج ثابت قابل للاختيار — جميل لكنه ساكن.
 * - سيجنال: لون صلب — آمن لكنه مسطّح.
 * - يونس: أساس مرفوع (0A0F18 لا 000000) + 3 هالات ذهب/كوبالت/بنفسج
 *   بألفا ≤10% تتحرك 12 ثانية — عمق حقيقي بلا أي تأثير على تباين النص
 *   (النص يُقاس على الأساس، والهالة لا تزيد سطوعه بأكثر من ~2%).
 *
 * ## الأداء وإمكانية الوصول
 * - `reduceMotion` أو `highContrast` → طبقة **ثابتة** تماماً بلا أي
 *   InfiniteTransition (لا invalidate للإطارات إطلاقاً).
 * - لا blur/noise/overdraw ثقيل: ثلاث `Box.background(Brush)` فقط — أرخص
 *   من Haze/BlurredLayer المستخدم في البطاقات الزجاجية.
 * - الحركة خطية بـ RepeatMode.Reverse — لا نبض يلهث، ذوق فاخر هادئ.
 */
@Composable
fun AuroraMeshLayer(isDark: Boolean, modifier: Modifier = Modifier) {
    if (!isDark) {
        // الوضع الفاتح: هالتان خفيفتان ثابتتان (السطوع العالي يجعل الحركة مزعجة).
        Box(modifier.fillMaxSize().background(YounesBackgrounds.goldHalo))
        Box(modifier.fillMaxSize().background(YounesBackgrounds.cobaltHalo))
        return
    }

    val still = AppThemeState.reduceMotion || AppThemeState.highContrast
    val phase = if (still) 0.5f else {
        val transition = rememberInfiniteTransition(label = "younes_aurora")
        transition.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(YounesMotion.AURORA_MS, easing = LinearEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "younes_aurora_phase"
        ).value
    }

    Box(modifier.fillMaxSize()) {
        Box(
            Modifier
                .fillMaxSize()
                .offset(x = (phase * 36 - 18).dp, y = (12 - phase * 24).dp)
                .background(YounesBackgrounds.goldHalo)
        )
        Box(
            Modifier
                .fillMaxSize()
                .offset(x = (12 - phase * 30).dp, y = (phase * 28 - 8).dp)
                .background(YounesBackgrounds.cobaltHalo)
        )
        Box(
            Modifier
                .fillMaxSize()
                .offset(x = (phase * 20 - 10).dp, y = (phase * 18 - 9).dp)
                .background(YounesBackgrounds.violetHalo)
        )
    }
}
