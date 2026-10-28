package com.red.sovereign.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.sp
import com.red.sovereign.settings.LanguageManager
import com.red.sovereign.ui.theme.PlexArabicFamily
import com.red.sovereign.ui.theme.YounesIconSize
import com.red.sovereign.ui.theme.YounesIcons
import com.red.sovereign.ui.theme.YounesRadius
import com.red.sovereign.ui.theme.YounesSpace

/**
 * اختيار لغة التطبيق — يستبدل الإعداد المقفل «RTL والعربية».
 * ثلاث خيارات: حسب النظام / العربية / English. التغيير فوري عبر
 * [LanguageManager.apply] مع إعادة إنشاء النشاط.
 */
@Composable
fun LanguageSelector(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var selected by remember { mutableStateOf(LanguageManager.current(context)) }

    Card(modifier = modifier.fillMaxWidth(), shape = androidx.compose.foundation.shape.RoundedCornerShape(YounesRadius.card)) {
        Column(Modifier.padding(YounesSpace.m)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(YounesSpace.xs)) {
                Icon(
                    YounesIcons.Language,
                    null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(YounesIconSize.large)
                )
                Column {
                    Text("لغة التطبيق", fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold, fontFamily = PlexArabicFamily)
                    Text(
                        "تُطبَّق فوراً على الموارد والإشعارات",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = PlexArabicFamily
                    )
                }
            }
            Row(
                Modifier.fillMaxWidth().padding(top = YounesSpace.s),
                horizontalArrangement = Arrangement.spacedBy(YounesSpace.xs)
            ) {
                LanguageManager.options.forEach { (tag, label) ->
                    FilterChip(
                        selected = selected == tag,
                        onClick = {
                            selected = tag
                            LanguageManager.apply(context, tag)
                        },
                        label = { Text(label, fontSize = 12.sp, fontFamily = PlexArabicFamily) }
                    )
                }
            }
        }
    }
}
