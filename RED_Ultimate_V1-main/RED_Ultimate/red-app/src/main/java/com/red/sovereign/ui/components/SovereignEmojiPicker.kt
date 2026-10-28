package com.red.sovereign.ui.components

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.red.sovereign.ui.theme.PlexArabicFamily
import com.red.sovereign.ui.theme.YounesIconSize
import com.red.sovereign.ui.theme.YounesRadius
import com.red.sovereign.ui.theme.YounesSpace

/**
 * ═══════════════════════════════════════════════════════════════════════
 * منتقي الإيموجي السيادي — مصدر واحد لكل شاشات الدردشة
 * ═══════════════════════════════════════════════════════════════════════
 *
 * كان هناك ثلاث نسخ مكرّرة من EmojiPicker (ChatsScreen، MessageContent،
 * DashboardIdentifiers) — كلها صف أفقي واحد بلا بحث ولا مستخدَم حديثاً ولا
 * درجات لون البشرة، بقوائم مختلفة. واتساب/تيليجرام/سيجنال تقدّم شبكة كاملة
 * + بحث + «الأكثر استخداماً» + متغيرات البشرة.
 *
 * الجديد:
 *  - 8 تصنيفات وأكثر من 180 رمزاً (بما فيها 🇾🇪 و🕌 للهوية المحلية).
 *  - بحث عربي/إنكليزي بالكلمات المفتاحية.
 *  - «المستخدمة حديثاً» محفوظة في SharedPreferences (آمنة، محلية، بلا شبكة).
 *  - 5 درجات لون بشرة للرموز الداعمة (Fitzpatrick) — تُطبَّق على كل الشبكة.
 *  - شبكة LazyVerticalGrid بارتفاع ثابت (لا تمرير لانهائي داخل الـ BottomSheet).
 *  - أهداف لمس ≥44dp وألوان من الثيم (لا ألوان صلبة محلية).
 */
object SovereignEmojiData {
    val categories: List<Pair<String, List<String>>> = listOf(
        "الوجوه" to listOf(
            "😀", "😃", "😄", "😁", "😆", "😅", "😂", "🤣", "🙂", "🙃", "😉", "😊",
            "😇", "🥰", "😍", "🤩", "😘", "😗", "😚", "😙", "😋", "😛", "😜", "🤪",
            "😝", "🤑", "🤗", "🤭", "🤫", "🤔", "🤐", "🤨", "😐", "😑", "😶", "😏",
            "😒", "🙄", "😬", "😮‍💨", "🤥", "😌", "😔", "😪", "🤤", "😴", "😷", "🤒",
            "🤕", "🤢", "🤮", "🥵", "🥶", "😵", "🤯", "🤠", "🥳", "😎", "🤓", "🧐",
            "😕", "😟", "🙁", "😮", "😯", "😲", "😳", "🥺", "😦", "😧", "😨", "😰",
            "😥", "😢", "😭", "😱", "😖", "😣", "😞", "😓", "😩", "😫", "🥱", "😤",
            "😡", "😠", "🤬", "😈", "👿", "💀", "👻", "👽", "🤖"
        ),
        "الإيماءات" to listOf(
            "👍", "👎", "👌", "✌️", "🤞", "🤟", "🤘", "🤙", "👈", "👉", "👆", "👇",
            "☝️", "✋", "🤚", "🖐️", "🖖", "👋", "🤝", "🙏", "💪", "🦾", "👏", "🙌",
            "🫶", "🤲", "👐", "🤗", "🤭", "🫡", "👀", "👁️", "🧠", "🫀", "👄", "👶",
            "🧑", "👨", "👩", "🧓", "👴", "👵", "🕺", "💃", "🧍", "🧎", "🏃", "🤸"
        ),
        "الحب" to listOf(
            "❤️", "🧡", "💛", "💚", "💙", "💜", "🖤", "🤍", "🤎", "💔", "❣️", "💕",
            "💞", "💓", "💗", "💖", "💘", "💝", "💟", "♥️", "💯", "💢", "💥", "✨",
            "⭐", "🌟", "🌠", "⚡", "🔥", "🎉", "🎊", "🎈", "🎁", "🏆", "🥇", "👑"
        ),
        "الطبيعة" to listOf(
            "🌙", "☀️", "⛅", "🌤️", "🌥️", "🌦️", "🌧️", "⛈️", "❄️", "☃️", "🌈", "🔥",
            "💧", "🌊", "🌹", "🌸", "🌺", "🌻", "🌼", "🌷", "🌱", "🌿", "🍀", "🌳",
            "🌴", "🌵", "🌾", "🌿", "🍃", "🐪", "🦅", "🐝", "🦋", "🐱", "🐶", "🐦",
            "🌍", "🌎", "🌏", "⛰️", "🏜️", "🏝️"
        ),
        "الطعام" to listOf(
            "☕", "🍵", "🥤", "🧃", "🍼", "🍺", "🍷", "🥂", "🍞", "🥐", "🥖", "🧀",
            "🍚", "🍜", "🍲", "🍛", "🍗", "🍖", "🥗", "🥙", "🌯", "🌮", "🍕", "🍔",
            "🍟", "🍩", "🍰", "🎂", "🍎", "🍊", "🍋", "🍉", "🍇", "🍓", "🍒", "🍯",
            "🥜", "🌶️", "🧄", "🧅"
        ),
        "الأنشطة" to listOf(
            "⚽", "🏀", "🏈", "⚾", "🎾", "🏐", "🏓", "🏸", "🥊", "🎯", "🎮", "🎲",
            "🎨", "🎭", "🎬", "🎤", "🎧", "🎼", "🎹", "🥁", "🎸", "🎺", "🎻", "🏆",
            "🥇", "🥈", "🥉", "🏅", "🎳", "🎱", "🧩", "♟️"
        ),
        "السفر" to listOf(
            "🚗", "🚕", "🚙", "🚌", "🚎", "🏎️", "🚓", "🚑", "🚒", "🚚", "🛵", "🏍️",
            "🚲", "✈️", "🚁", "🚀", "🛸", "🚢", "⛵", "🗺️", "🧳", "🚦", "🏠", "🏢",
            "🏥", "🏫", "🕌", "⛺", "🏖️", "🗼", "🌋", "🛣️"
        ),
        "الأشياء" to listOf(
            "💡", "📱", "💻", "⌚", "📷", "🎥", "🎙️", "📺", "📻", "🔒", "🔑", "🗝️",
            "📌", "📎", "📁", "📄", "📚", "✏️", "🖊️", "📝", "📊", "📈", "🔔", "🔕",
            "🎁", "🏷️", "💰", "💳", "🧰", "⚙️", "🔧", "🔨", "🧲", "🔋", "💊", "🧬",
            "✅", "❌", "⚠️", "❗", "❓", "➕", "➖", "♻️", "🔴", "🟢", "🟡", "🔵", "🇾🇪"
        )
    )

    /** الرموز التي تقبل مُعدِّل لون البشرة (Fitzpatrick). */
    private val toneable = setOf(
        "👍", "👎", "👌", "✌️", "🤞", "🤟", "🤘", "🤙", "👈", "👉", "👆", "👇",
        "☝️", "✋", "🤚", "🖐️", "🖖", "👋", "🙏", "💪", "👏", "🙌", "🫶", "🤲",
        "👐", "🤝", "🫡", "👶", "🧑", "👨", "👩", "🧓", "👴", "👵", "🧍", "🧎",
        "🏃", "💃", "🕺", "🤸", "💅", "🧖", "🧗", "🏋️"
    )

    val skinTones = listOf("", "🏻", "🏼", "🏽", "🏾", "🏿")

    fun withTone(emoji: String, tone: String): String {
        if (tone.isEmpty() || emoji !in toneable) return emoji
        // أزل variation selector U+FE0F قبل مُعدِّل البشرة (توحيد العرض).
        val base = emoji.replace("\uFE0F", "")
        return base + tone
    }

    /** كلمات مفتاحية للبحث — عربي + إنكليزي. */
    val aliases: Map<String, String> = mapOf(
        "❤️" to "حب قلب love heart", "😂" to "ضحك بكاء laugh", "👍" to "موافق like جيد",
        "🔥" to "نار fire رائع", "🙏" to "شكر دعاء thanks pray", "🎉" to "احتفال عيد party",
        "😢" to "حزن بكاء sad cry", "😡" to "غضب angry", "🥳" to "عيد ميلاد party",
        "✅" to "تم صحيح done check", "❌" to "خطأ لا wrong", "⚠️" to "تحذير warning",
        "🔒" to "قفل أمان lock", "🔑" to "مفتاح key", "🇾🇪" to "اليمن yemen",
        "🕌" to "مسجد mosque", "☕" to "قهوة coffee", "💯" to "مئة ممتاز 100"
    )

    fun matches(emoji: String, query: String): Boolean {
        if (query.isBlank()) return true
        val q = query.trim().lowercase()
        return emoji.contains(q) || (aliases[emoji]?.contains(q) == true)
    }
}

private const val PREFS = "younes_emoji_recents"
private const val KEY = "recent"
private const val MAX_RECENTS = 24

private fun loadRecents(context: Context): List<String> =
    runCatching {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY, "").orEmpty()
            .split("\u0001")
            .filter { it.isNotBlank() }
    }.getOrDefault(emptyList())

private fun saveRecent(context: Context, emoji: String) {
    runCatching {
        val current = loadRecents(context).toMutableList()
        current.remove(emoji)
        current.add(0, emoji)
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY, current.take(MAX_RECENTS).joinToString("\u0001"))
            .apply()
    }
}

@Composable
fun SovereignEmojiPicker(
    onEmoji: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var category by remember { mutableIntStateOf(-1) } // -1 = المستخدمة حديثاً
    var query by remember { mutableStateOf("") }
    var toneIndex by remember { mutableIntStateOf(0) }
    var recents by remember { mutableStateOf(loadRecents(context)) }

    val tone = SovereignEmojiData.skinTones.getOrElse(toneIndex) { "" }
    val source: List<String> = when {
        query.isNotBlank() -> SovereignEmojiData.categories
            .flatMap { it.second }
            .distinct()
            .filter { SovereignEmojiData.matches(it, query) }
        category == -1 -> recents
        else -> SovereignEmojiData.categories.getOrElse(category) { "" to emptyList() }.second
    }
    val emojis = remember(source, tone) { source.map { SovereignEmojiData.withTone(it, tone) }.distinct() }

    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = RoundedCornerShape(YounesRadius.sheet)
    ) {
        Column(Modifier.padding(vertical = YounesSpace.xs)) {
            // البحث + درجات البشرة
            Row(
                Modifier.fillMaxWidth().padding(horizontal = YounesSpace.s),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(YounesSpace.xs)
            ) {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    modifier = Modifier.weight(1f).heightIn(min = YounesSpace.minTouch),
                    placeholder = { Text("ابحث عن إيموجي…", fontSize = 12.sp, fontFamily = PlexArabicFamily) },
                    singleLine = true,
                    shape = RoundedCornerShape(YounesRadius.field)
                )
                LazyRow(horizontalArrangement = Arrangement.spacedBy(YounesSpace.xxxs)) {
                    items(SovereignEmojiData.skinTones.size) { index ->
                        val label = SovereignEmojiData.skinTones[index]
                        Box(
                            Modifier
                                .size(YounesIconSize.large)
                                .clip(CircleShape)
                                .background(
                                    if (index == toneIndex) MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)
                                    else MaterialTheme.colorScheme.surfaceVariant
                                )
                                .clickable { toneIndex = index },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                if (label.isEmpty()) "✋" else "✋$label",
                                fontSize = 14.sp
                            )
                        }
                    }
                }
            }

            // التصنيفات
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(YounesSpace.xxs),
                modifier = Modifier.padding(horizontal = YounesSpace.s, vertical = YounesSpace.xxs)
            ) {
                item {
                    FilterChip(
                        selected = category == -1,
                        onClick = { category = -1; query = "" },
                        label = { Text("🕘 الأخيرة", fontSize = 11.sp, fontFamily = PlexArabicFamily) },
                        colors = FilterChipDefaults.filterChipColors()
                    )
                }
                items(SovereignEmojiData.categories.size) { index ->
                    FilterChip(
                        selected = category == index,
                        onClick = { category = index; query = "" },
                        label = { Text(SovereignEmojiData.categories[index].first, fontSize = 11.sp, fontFamily = PlexArabicFamily) }
                    )
                }
            }

            if (emojis.isEmpty()) {
                Box(
                    Modifier.fillMaxWidth().height(140.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        if (category == -1) "لم تستخدم أي إيموجي بعد — اختر تصنيفاً" else "لا نتائج مطابقة",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 12.sp,
                        fontFamily = PlexArabicFamily,
                        textAlign = TextAlign.Center
                    )
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = 44.dp),
                    modifier = Modifier.fillMaxWidth().height(190.dp),
                    horizontalArrangement = Arrangement.spacedBy(YounesSpace.xxxs),
                    verticalArrangement = Arrangement.spacedBy(YounesSpace.xxxs),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = YounesSpace.s)
                ) {
                    items(emojis) { emoji ->
                        Box(
                            Modifier
                                .size(44.dp)
                                .clip(RoundedCornerShape(YounesRadius.chip))
                                .clickable {
                                    recents = listOf(emoji) + (recents - emoji)
                                    saveRecent(context, emoji)
                                    onEmoji(emoji)
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(emoji, fontSize = 24.sp)
                        }
                    }
                }
            }
        }
    }
}
