package com.red.sovereign.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.red.sovereign.groups.Group

/**
 * ════════════════════════════════════════════════════════════════════════
 *  DashboardPoll — حوارات الاستطلاعات مستخرجة من `RedDashboard.kt`
 * ════════════════════════════════════════════════════════════════════════
 *
 *  المصدر: `RedDashboard.kt` — كتلة `if (showGroupPollDialog)`.
 *
 *  2026-09-26 — الاستطلاعات المتقدمة (P1-C): خيارات الإنشاء الثلاثة
 *  (مؤقت إغلاق تلقائي / تصويت مجهول / اختيار متعدد) تُدمج في
 *  [com.red.sovereign.core.InlinePoll] نفسها فتنتقل E2EE مع الرسالة —
 *  والعملاء القدامى يتجاهلون الحقول الجديدة بأمان (ignoreUnknownKeys).
 *
 *  بطاقة التصويت الحية تعيش في `MessageContent.kt` (`InlinePollCard`)
 *  والأصوات في `ChatPollVoteStore` + `PollsAdvanced` — هذا الملف للإنشاء فقط.
 */

/** نتائج الحوار الغنية: السؤال + الخيارات + المواصفة. */
data class GroupPollDraft(
    val question: String,
    val options: List<String>,
    /** مؤقت الإغلاق التلقائي (epoch millis) — null = بلا مؤقت. */
    val endTime: Long? = null,
    val isAnonymous: Boolean = false,
    val allowMultiple: Boolean = false
)

/** حوار «استطلاع في المجموعة»: السؤال + الخيارات + خيارات متقدمة + زر الإرسال. */
@Composable
internal fun DashboardGroupPollDialog(
    openGroup: Group?,
    question: String,
    onQuestionChange: (String) -> Unit,
    options: List<String>,
    onOptionsChange: (List<String>) -> Unit,
    onDismiss: () -> Unit,
    onSendPoll: (question: String, options: List<String>) -> Unit,
    /** صُندوق الاستطلاع المتقدم — null يبقي الحوار بالشكل القديم (توافق الاستدعاءات). */
    onSendPollAdvanced: ((GroupPollDraft) -> Unit)? = null
) {
    var advancedEnabled by remember { mutableStateOf(false) }
    var anonymous by remember { mutableStateOf(false) }
    var multiple by remember { mutableStateOf(false) }
    var durationHours by remember { mutableLongStateOf(0L) } // 0 = بلا مؤقت

    val durationLabel: (Long) -> String = { h ->
        when {
            h >= 24L && h % 24L == 0L -> "${h / 24L} يوم"
            h >= 24L -> "${h / 24L} يوم و${h % 24L} ساعة"
            else -> "$h ساعة"
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("استطلاع في المجموعة") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    question,
                    { onQuestionChange(it.take(280)) },
                    Modifier.fillMaxWidth(),
                    label = { Text("السؤال") },
                    maxLines = 3
                )
                options.forEachIndexed { index, value ->
                    OutlinedTextField(
                        value = value,
                        onValueChange = { next ->
                            onOptionsChange(options.toMutableList().also { it[index] = next.take(80) })
                        },
                        Modifier.fillMaxWidth(),
                        label = { Text("الخيار ${index + 1}") },
                        singleLine = true
                    )
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        { if (options.size < 6) onOptionsChange(options + "") },
                        Modifier.weight(1f),
                        enabled = options.size < 6
                    ) { Text("+ خيار") }
                    OutlinedButton(
                        { if (options.size > 2) onOptionsChange(options.dropLast(1)) },
                        Modifier.weight(1f),
                        enabled = options.size > 2
                    ) { Text("- خيار") }
                }
                // ─── صندوق الخيارات المتقدمة (اختياري) ───
                FilterChip(
                    selected = advancedEnabled,
                    onClick = { advancedEnabled = !advancedEnabled },
                    label = { Text("خيارات متقدمة", fontWeight = if (advancedEnabled) FontWeight.Bold else FontWeight.Normal) }
                )
                if (advancedEnabled) {
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("تصويت مجهول (إجماليات فقط)")
                        Switch(checked = anonymous, onCheckedChange = { anonymous = it })
                    }
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("السماح باختيار متعدد")
                        Switch(checked = multiple, onCheckedChange = { multiple = it })
                    }
                    Text("إغلاق تلقائي بعد:", fontWeight = FontWeight.SemiBold)
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf(0L to "بلا", 1L to "1 ساعة", 24L to "يوم", 168L to "أسبوع").forEach { (h, label) ->
                            FilterChip(
                                selected = durationHours == h,
                                onClick = { durationHours = h },
                                label = { Text(label) }
                            )
                        }
                    }
                    if (durationHours > 0L) {
                        Text(
                            "سيُغلق تلقائيًا بعد $durationLabel(durationHours)",
                            style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                            color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        },
        confirmButton = {
            val validPoll = question.isNotBlank() && options.count { it.trim().length >= 2 } >= 2
            Button(
                enabled = validPoll && openGroup != null,
                onClick = {
                    val q = question.trim()
                    val opts = options.map { it.trim() }.filter { it.length >= 2 }
                    val advanced = advancedEnabled && (anonymous || multiple || durationHours > 0L)
                    if (advanced && onSendPollAdvanced != null) {
                        onSendPollAdvanced(
                            GroupPollDraft(
                                question = q,
                                options = opts,
                                endTime = durationHours.takeIf { it > 0L }?.let { System.currentTimeMillis() + it * 3_600_000L },
                                isAnonymous = anonymous,
                                allowMultiple = multiple
                            )
                        )
                    } else {
                        onSendPoll(q, opts)
                    }
                }
            ) { Text("إرسال الاستطلاع") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("إلغاء") } }
    )
}
