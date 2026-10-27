package com.red.sovereign.ui

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.red.sovereign.settings.SettingsRuntime

/**
 * مقاسات شاشة يونس — هاتف ضيق، عادي، لوحي.
 * لا ثيم فاتح. فقط مسافات وعرض الفقاعة حتى لا تنكسر الواجهة على 5 بوصات أو جهاز عريض.
 */
data class WindowLayout(
    val widthDp: Int,
    val heightDp: Int,
    val isCompactWidth: Boolean,
    val isCompactHeight: Boolean,
    val isWide: Boolean,
    val pagePadding: Dp,
    val bubbleMax: Dp,
    val compactChrome: Boolean,
    val isPhone: Boolean = widthDp < 600,
    val isFoldable: Boolean = widthDp in 600..839,
    val isTablet: Boolean = widthDp >= 840,
    val isLandscape: Boolean = widthDp > heightDp,
    val contentMaxWidth: Dp = if (widthDp >= 840) 760.dp else if (widthDp >= 600) 640.dp else Dp.Unspecified,
    val gridColumns: Int = if (widthDp >= 1000) 4 else if (widthDp >= 720) 3 else if (widthDp >= 500) 2 else 1,
    val dialogMaxWidth: Dp = if (widthDp >= 840) 560.dp else if (widthDp >= 600) 480.dp else 340.dp,
    val cardSpacing: Dp = if (widthDp >= 840) 16.dp else if (widthDp < 360) 8.dp else 12.dp,
    val masterPaneWidth: Dp = if (widthDp >= 1000) 400.dp else 340.dp,
    val isDualPaneSupported: Boolean = widthDp >= 600
) {
    companion object {
        @Composable
        fun current(): WindowLayout {
            val config = LocalConfiguration.current
            val w = config.screenWidthDp
            val h = config.screenHeightDp
            val compactW = w < 360
            val compactH = h < 640
            val wide = w >= 600
            val isTablet = w >= 840
            val userCompact = SettingsRuntime.current.compactMode
            return WindowLayout(
                widthDp = w,
                heightDp = h,
                isCompactWidth = compactW,
                isCompactHeight = compactH,
                isWide = wide,
                pagePadding = when {
                    isTablet -> 32.dp
                    wide -> 24.dp
                    compactW -> 10.dp
                    else -> 14.dp
                },
                bubbleMax = (w * 0.82f).dp.coerceIn(220.dp, if (wide) 460.dp else 340.dp),
                compactChrome = userCompact || compactH || compactW,
                isPhone = w < 600,
                isFoldable = w in 600..839,
                isTablet = isTablet,
                isLandscape = w > h,
                contentMaxWidth = if (w >= 840) 760.dp else if (w >= 600) 640.dp else Dp.Unspecified,
                gridColumns = if (w >= 1000) 4 else if (w >= 720) 3 else if (w >= 500) 2 else 1,
                dialogMaxWidth = if (w >= 840) 560.dp else if (w >= 600) 480.dp else 340.dp,
                cardSpacing = if (w >= 840) 16.dp else if (compactW) 8.dp else 12.dp,
                masterPaneWidth = if (w >= 1000) 400.dp else 340.dp,
                isDualPaneSupported = w >= 600
            )
        }
    }
}

/**
 * قيود العرض المتكيفة لمنع استطالة الأسطر والمحتوى بشكل مشوّه على الشاشات العريضة واللوحيات
 */
fun Modifier.adaptiveContainer(
    layout: WindowLayout,
    alignment: Alignment.Horizontal = Alignment.CenterHorizontally
): Modifier {
    return if (layout.contentMaxWidth != Dp.Unspecified) {
        this
            .fillMaxWidth()
            .wrapContentWidth(alignment)
            .widthIn(max = layout.contentMaxWidth)
    } else {
        this.fillMaxWidth()
    }
}

/**
 * تطبيق هوامش الصفحة المتكيفة تلقائياً
 */
fun Modifier.adaptivePagePadding(layout: WindowLayout): Modifier {
    return this.padding(horizontal = layout.pagePadding)
}

