package com.red.sovereign.ui.theme

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * ═══════════════════════════════════════════════════════════════════════
 * Younes Design Tokens — مصدر الحقيقة الواحد للأبعاد والحركة والخلفيات
 * ═══════════════════════════════════════════════════════════════════════
 *
 * لماذا؟ كان كل شاشة تخترع أرقامها: 12/14/16/18/20/24/32 dp متجاورة بلا
 * نظام، وزوايا 12/16/18/20/24/32 عشوائية، ومدد أنيميشن 300/600/1200ms
 * متضاربة. النتيجة: «التطبيق غير متناسق» رغم جمال كل شاشة على حدة.
 * المنافسون (واتساب/تيليجرام/سيجنال) يستخدمون شبكة 4dp ومقياسَي حركة فقط.
 *
 * القواعد:
 *  - كل بُعد من مضاعفات 4dp (عدا xxxs=2dp للفواصل الدقيقة).
 *  - 5 مستويات ارتفاع/حاوية فقط، تُترجم إلى لون + ظل + حدّ.
 *  - حركتان فقط: سريعة (تفاعل) وبطيئة (انتقال شاشة/هالة).
 *  - أي لون/تدرّج جديد يجب أن يمر من هنا ويحرسه ColorContrastTest.
 */

// ─── المسافات: شبكة 4dp ────────────────────────────────────────────────
object YounesSpace {
    val xxxs: Dp = 2.dp
    val xxs: Dp = 4.dp
    val xs: Dp = 8.dp
    val s: Dp = 12.dp
    val m: Dp = 16.dp
    val l: Dp = 20.dp
    val xl: Dp = 24.dp
    val xxl: Dp = 32.dp
    val xxxl: Dp = 48.dp
    val screen: Dp = 20.dp          // حاشية الشاشة القياسية
    val screenWide: Dp = 24.dp      // للأجهزة اللوحية
    val section: Dp = 28.dp         // فاصل بين الأقسام
    val listItem: Dp = 72.dp        // أقل ارتفاع لعنصر قائمة (48dp لمس + حواشٍ)
    val minTouch: Dp = 48.dp        // WCAG 2.5.5 / Material accessibility
}

// ─── الزوايا: سلم واحد ─────────────────────────────────────────────────
object YounesRadius {
    val chip: Dp = 8.dp
    val field: Dp = 12.dp
    val bubble: Dp = 18.dp
    val card: Dp = 20.dp
    val sheet: Dp = 28.dp
    val pill: Dp = 999.dp
}

// ─── الارتفاعات: 5 مستويات فقط ─────────────────────────────────────────
object YounesElevation {
    val level0: Dp = 0.dp
    val level1: Dp = 1.dp
    val level2: Dp = 3.dp
    val level3: Dp = 6.dp
    val level4: Dp = 12.dp
    val level5: Dp = 24.dp
}

// ─── أحجام الأيقونات: 4 مقاسات ─────────────────────────────────────────
object YounesIconSize {
    val small: Dp = 16.dp
    val medium: Dp = 20.dp
    val large: Dp = 24.dp
    val hero: Dp = 32.dp
    val brand: Dp = 72.dp
}

// ─── الحركة: مقياسان + احترام reduceMotion ─────────────────────────────
object YounesMotion {
    const val FAST_MS = 180
    const val STANDARD_MS = 300
    const val SLOW_MS = 600
    const val AURORA_MS = 12_000

    val fastSpring = spring<Float>(
        dampingRatio = Spring.DampingRatioNoBouncy,
        stiffness = Spring.StiffnessMedium
    )
    val gentleSpring = spring<Float>(
        dampingRatio = Spring.DampingRatioLowBouncy,
        stiffness = Spring.StiffnessLow
    )

    /** يحترم إعداد «تقليل الحركة» — يعيد 0 فيُصبح أي أنيميشن فورياً. */
    @Composable
    fun duration(base: Int): Int =
        if (AppThemeState.reduceMotion || AppThemeState.highContrast) 0 else base
}

// ─── التدرّجات: هوية «ذهب الليل» على أوبسيديان كحلي ────────────────────
object YounesBackgrounds {
    /** الأساس الداكن — طبقة مرفوعة لا أسود مسطّح (يحمي تفاصيل OLED). */
    val darkBase: Brush
        get() = Brush.verticalGradient(
            listOf(Color(0xFF101A29), YounesVoid, Color(0xFF0B1320))
        )

    /** الأساس الفاتح — لؤلؤي دافئ مع لمسة ذهبية خفيفة. */
    val lightBase: Brush
        get() = Brush.verticalGradient(
            listOf(Color(0xFFFBF9F4), Color(0xFFF7F8FA), Color(0xFFEFF3F7))
        )

    /** هالة ذهبية علوية — 10% ألفا فقط كي لا تُضعف تباين النص. */
    val goldHalo: Brush
        get() = Brush.radialGradient(
            colors = listOf(YounesPrimary.copy(alpha = 0.10f), Color.Transparent),
            radius = 1100f
        )

    /** هالة كوبالت سفلية — تعطي عمقاً بصرياً بلا تشتيت. */
    val cobaltHalo: Brush
        get() = Brush.radialGradient(
            colors = listOf(YounesCobalt.copy(alpha = 0.08f), Color.Transparent),
            radius = 1200f
        )

    /** هالة بنفسجية جانبية — للمساحات الصوتية والشاشات الاحتفالية. */
    val violetHalo: Brush
        get() = Brush.radialGradient(
            colors = listOf(YounesVioletDeep.copy(alpha = 0.09f), Color.Transparent),
            radius = 900f
        )

    /** تدرّج أزرار أساسي — ذهب سيادي مع حافة أفتح. */
    val primaryButton: Brush
        get() = Brush.horizontalGradient(listOf(YounesPrimary, YounesAccent, YounesPrimaryGlow))

    /** تدرّج الشارة المشفرة — زمرد النجاح مع كوبالت. */
    val secureBadge: Brush
        get() = Brush.horizontalGradient(listOf(YounesSuccess.copy(alpha = 0.18f), YounesCobalt.copy(alpha = 0.12f)))

    /** تدرّج شاشة المكالمة — أعمق من باقي الشاشات (تركيز على الوجه والفيديو). */
    val callSurface: Brush
        get() = Brush.verticalGradient(listOf(Color(0xFF0D1726), YounesVoid, Color(0xFF080E18)))
}
