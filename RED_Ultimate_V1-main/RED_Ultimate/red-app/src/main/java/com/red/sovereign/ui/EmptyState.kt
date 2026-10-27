package com.red.sovereign.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.red.sovereign.ui.theme.AqyalGold

/**
 * 2026-09-27: استُخرج من RedDashboard.kt المؤرشف (كان المزوّد الوحيد المشترك
 * لهذا التوقيع في الحزمة com.red.sovereign.ui) — نفس الشكل والسلوك حرفيًا،
 * بلا تغيير بصري، لت Zero-reference في FeedScreens وبقية الشاشات.
 */
@Composable
fun EmptyState(icon: ImageVector, title: String, detail: String) = Column(
    Modifier.fillMaxWidth().padding(30.dp),
    horizontalAlignment = Alignment.CenterHorizontally
) {
    Icon(icon, null, tint = AqyalGold, modifier = Modifier.size(62.dp))
    Text(title, fontSize = 20.sp, fontWeight = FontWeight.Bold)
    Text(detail, textAlign = androidx.compose.ui.text.style.TextAlign.Center, color = Color.Gray, modifier = Modifier.padding(top = 8.dp))
}
