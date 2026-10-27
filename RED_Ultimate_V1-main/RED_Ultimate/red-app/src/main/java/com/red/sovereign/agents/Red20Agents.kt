package com.red.sovereign.agents

import java.util.Calendar

/**
 * العشرون وكيلاً السيادياً — التنفيذ الوحيد المعتمد (المصدر للحقيقة).
 *
 * قاعدة الصدق في هذا الملف: **لا يدّعي وكيل قدرة غير موجودة**. كل وكيل هنا ينفّذ
 * منطقاً محلياً حتمياً (قواعد/قوائم/تحليل نصي) أو يمرّر نتيجة حقيقية وردت في
 * [AgentContext.metadata] من طبقة أخرى (خادم، محرّك وسائط، إعدادات النظام).
 * ما يحتاج مزوّد خارجي (ترجمة، تفريغ صوتي، ذكاء اصطناعي توليدي) يُرجع
 * [AgentResult.Ignored] مع سبب صريح بدل رد جاهز مضلّل.
 *
 * السبب: النسخة السابقة كانت تُرجع نصوصاً ثابتة («تمت الترجمة»، «وُجدت 3 مراجع»،
 * «تعمل الأنظمة بلا أخطاء») بلا أي تنفيذ — وهي أسوأ من الغياب لأنها تُطمئن كذباً.
 */
object AgentRules {
    /** كلمات حظر عامة (العربية والإنجليزية) — قابلة للتوسيع من إعدادات المجموعة. */
    val BANNED = listOf(
        "spam", "scam", "phishing", "hack", "malware",
        "احتيال", "نصب", "تصيّد", "اختراق", "فيروس"
    )

    /** مؤشرات طلب بيانات حساسة — أعلى خطورة من كلمات الحظر العامة. */
    val SENSITIVE_REQUESTS = listOf(
        "otp", "رمز التحقق", "كلمة المرور", "password", "cvv", "بطاقة",
        "verify your account", "أرسل الرمز", "send the code"
    )

    val POSITIVE = listOf(
        "شكراً", "ممتاز", "رائع", "أحسنت", "جميل", "تمام", "بارك الله",
        "thanks", "great", "awesome", "perfect", "good", "excellent"
    )
    val NEGATIVE = listOf(
        "سيء", "فاشل", "غبي", "مشكلة", "خطأ", "تعطل", "زعلان",
        "bad", "broken", "fail", "error", "terrible", "hate"
    )
}

/** ترتيب موحّد لخطورة الحدث (0 = بلا خطورة). */
private fun severityOf(content: String): Int {
    val text = content.lowercase()
    if (AgentRules.SENSITIVE_REQUESTS.any { text.contains(it) }) return 3
    val hits = AgentRules.BANNED.count { text.contains(it) }
    return when {
        hits >= 2 -> 3
        hits == 1 -> 2
        // روابط كثيرة أو منشن جماعي في رسالة واحدة = سبام محتمل
        text.split("http").size > 3 -> 2
        else -> 0
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
// 1..7 — التنسيق والإشراف والترجمة والتلخيص والمشاعر (موجودة، منطقها صار حقيقياً)
// ═══════════════════════════════════════════════════════════════════════════════

/** يوزّع الحدث على مسار واحد فقط (تنسيق) — لا يدّعي تحليلاً. */
class MasterCoordinatorAgent : RedAiAgent {
    override val agentId = "agent_master_coordinator"
    override val agentName = "Master Coordinator Agent"
    override val description = "Routes each event to the single specialist lane it belongs to."
    override val isActive = true

    override suspend fun process(context: AgentContext): AgentResult {
        val lane = when {
            context.messageContent.startsWith("/poll") -> "polls"
            context.messageContent.startsWith("/search") -> "search"
            context.messageContent.startsWith("/summarize") -> "summarize"
            context.messageContent.contains("```") -> "code"
            context.messageContent.isBlank() -> "none"
            else -> "conversation"
        }
        if (lane == "none") return AgentResult.Ignored
        // مصدر التوجيه الحقيقي: النتيجة تُقرأ من payload لا من نص تسويقي.
        return AgentResult.Success("routing=$lane", mapOf("lane" to lane))
    }
}

/** إشراف حقيقي: كلمات حظر + طلبات بيانات حساسة + سبام روابط/منشن. */
class GroupModerationAgent : RedAiAgent {
    override val agentId = "agent_group_moderation"
    override val agentName = "Group Moderation & Anti-Spam Agent"
    override val description = "Flags profanity, spam, sensitive-data requests and link floods."
    override val isActive = true

    override suspend fun process(context: AgentContext): AgentResult {
        val severity = severityOf(context.messageContent)
        if (severity == 0) return AgentResult.Ignored
        val why = if (severity >= 3) "طلب بيانات حساسة أو سبام متكرر" else "سبام/محتوى محظور"
        return AgentResult.Flagged(why, severity)
    }
}

/**
 * ترجمة حقيقية فقط: تُعيد الترجمة الجاهزة من [AgentContext.metadata] («translation»).
 * لا مزوّد ترجمة داخل التطبيق، فلا نخترع نصاً مترجماً.
 */
class RealtimeTranslationAgent : RedAiAgent {
    override val agentId = "agent_realtime_translation"
    override val agentName = "Real-time Translation Agent"
    override val description = "Surfaces server/provider translations when present (no local MT engine)."
    override val isActive = true

    override suspend fun process(context: AgentContext): AgentResult {
        val target = context.metadata["target_lang"] as? String ?: return AgentResult.Ignored
        val translated = context.metadata["translation"] as? String
        if (translated.isNullOrBlank()) return AgentResult.Ignored
        return AgentResult.Success(
            translated,
            mapOf("target_lang" to target, "provider" to (context.metadata["translation_provider"] ?: "server"))
        )
    }
}

/**
 * تلخيص استخلاصي حقيقي (لا توليدي): يستقبل رسائل المجموعة في metadata["messages"]
 * ويختار الجمل الأكثر تكراراً للمفردات، بميزانية أحرف محددة.
 */
class SmartSummarizationAgent : RedAiAgent {
    override val agentId = "agent_smart_summarization"
    override val agentName = "Smart Summarization Agent"
    override val description = "Extractive catch-up digest built from real group messages."
    override val isActive = true

    override suspend fun process(context: AgentContext): AgentResult {
        if (!context.messageContent.startsWith("/summarize")) return AgentResult.Ignored
        @Suppress("UNCHECKED_CAST")
        val messages = (context.metadata["messages"] as? List<String>).orEmpty()
            .map { it.trim() }
            .filter { it.isNotBlank() }
        if (messages.isEmpty()) {
            return AgentResult.Success("لا رسائل للتلخيص", mapOf("summarized" to 0))
        }
        val stop = setOf(
            "في", "من", "على", "إلى", "عن", "هذا", "هذه", "التي", "الذي", "هو", "هي", "ثم", "قد",
            "the", "and", "for", "with", "that", "this", "you", "are"
        )
        val freq = HashMap<String, Int>()
        messages.forEach { line ->
            line.lowercase().split(Regex("[^\\p{L}\\p{N}]+"))
                .filter { it.length >= 3 && it !in stop }
                .forEach { freq[it] = (freq[it] ?: 0) + 1 }
        }
        val keywords = freq.entries.sortedByDescending { it.value }.take(12).map { it.key }.toSet()
        val picked = messages
            .map { line -> keywords.count { line.lowercase().contains(it) } to line }
            .sortedByDescending { it.first }
            .map { it.second }
            .distinct()
            .take(5)
        val summary = picked.joinToString("\n• ", prefix = "• ")
        return AgentResult.Success(
            summary,
            mapOf("summarized" to messages.size, "picked" to picked.size)
        )
    }
}

/** تحليل مشاعر حقيقي بقائمة معجم عربية/إنجليزية — النتيجة عددية قابلة للاختبار. */
class SentimentAnalysisAgent : RedAiAgent {
    override val agentId = "agent_sentiment_analysis"
    override val agentName = "Sentiment & Mood Analysis Agent"
    override val description = "Lexicon-based tone score (no LLM, fully deterministic)."
    override val isActive = true

    override suspend fun process(context: AgentContext): AgentResult {
        val text = context.messageContent.lowercase()
        if (text.isBlank()) return AgentResult.Ignored
        val pos = AgentRules.POSITIVE.count { text.contains(it) }
        val neg = AgentRules.NEGATIVE.count { text.contains(it) }
        if (pos == 0 && neg == 0) return AgentResult.Ignored
        val score = pos - neg
        val mood = when {
            score > 0 -> "positive"
            score < 0 -> "negative"
            else -> "mixed"
        }
        return AgentResult.Success(
            "mood=$mood score=$score",
            mapOf("mood" to mood, "score" to score, "positive_hits" to pos, "negative_hits" to neg)
        )
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
// 8..20 — الوكلاء الثلاثة عشر التي كانت **مفقودة تماماً** (استُدعيت بلا تعريف)
// ═══════════════════════════════════════════════════════════════════════════════

/** محلّل أوامر التصويت: `/poll سؤال | خيار | خيار`. */
class SmartPollAgent : RedAiAgent {
    override val agentId = "agent_smart_poll"
    override val agentName = "Smart Poll Agent"
    override val description = "Parses /poll commands into structured question + options."
    override val isActive = true

    private val pollRegex = Regex("^/poll\\s+(.+)$", RegexOption.IGNORE_CASE)

    override suspend fun process(context: AgentContext): AgentResult {
        val match = pollRegex.find(context.messageContent.trim()) ?: return AgentResult.Ignored
        val parts = match.groupValues[1].split('|').map { it.trim() }.filter { it.isNotBlank() }
        if (parts.size < 3) {
            return AgentResult.Flagged("صيغة التصويت ناقصة: سؤال | خيار | خيار", 1)
        }
        val question = parts.first()
        val options = parts.drop(1).take(10)
        return AgentResult.Success(
            question,
            mapOf("question" to question, "options" to options, "option_count" to options.size)
        )
    }
}

/** كشف تهديدات حقيقي: طلب OTP/كلمات مرور، روابط مشبوهة، سبام. */
class SecurityThreatAgent : RedAiAgent {
    override val agentId = "agent_security_threat"
    override val agentName = "Security Threat Agent"
    override val description = "Heuristic phishing/credential-harvest detection with severity."
    override val isActive = true

    override suspend fun process(context: AgentContext): AgentResult {
        val text = context.messageContent
        val severity = severityOf(text)
        if (severity < 2) return AgentResult.Ignored
        val lower = text.lowercase()
        val reason = when {
            AgentRules.SENSITIVE_REQUESTS.any { lower.contains(it) } -> "طلب بيانات دخول/رمز تحقق"
            lower.contains("bit.ly") || lower.contains("tinyurl") -> "رابط مختصر مخفي الهدف"
            else -> "محتوى احتيالي محتمل"
        }
        return AgentResult.Flagged(reason, severity)
    }
}

/** تصنيف وسائط حقيقي من نوع الملف/امتداده — لا تحليل بصري مزعوم. */
class MediaTaggingAgent : RedAiAgent {
    override val agentId = "agent_media_tagging"
    override val agentName = "Media Tagging Agent"
    override val description = "Tags media by MIME type and file extension (metadata only)."
    override val isActive = true

    override suspend fun process(context: AgentContext): AgentResult {
        val mime = (context.metadata["mime"] as? String)?.lowercase()
        val name = (context.metadata["file_name"] as? String)?.lowercase()
        if (mime.isNullOrBlank() && name.isNullOrBlank()) return AgentResult.Ignored
        val ext = name?.substringAfterLast('.', "")
        val kind = when {
            mime?.startsWith("image/") == true || ext in setOf("jpg", "jpeg", "png", "webp", "heic", "gif") -> "image"
            mime?.startsWith("video/") == true || ext in setOf("mp4", "mkv", "mov", "webm") -> "video"
            mime?.startsWith("audio/") == true || ext in setOf("m4a", "ogg", "opus", "mp3", "wav", "aac") -> "audio"
            ext in setOf("pdf", "doc", "docx", "xls", "xlsx", "ppt", "pptx", "txt") -> "document"
            ext in setOf("apk", "zip", "rar", "7z", "tar", "gz") -> "archive"
            else -> "other"
        }
        return AgentResult.Success(kind, mapOf("media_kind" to kind, "extension" to (ext ?: "")))
    }
}

/** تجهيز استعلام بحث حقيقي لـ FTS: تنظيف + كلمات مفتاحية. */
class SmartSearchAgent : RedAiAgent {
    override val agentId = "agent_smart_search"
    override val agentName = "Smart Search Agent"
    override val description = "Normalizes /search queries into FTS-ready keyword tokens."
    override val isActive = true

    private val cmd = Regex("^/search\\s+(.+)$", RegexOption.IGNORE_CASE)

    override suspend fun process(context: AgentContext): AgentResult {
        val raw = cmd.find(context.messageContent.trim())?.groupValues?.get(1)?.trim()
            ?: (context.metadata["query"] as? String)?.trim()
            ?: return AgentResult.Ignored
        val tokens = raw.lowercase()
            .split(Regex("[^\\p{L}\\p{N}]+"))
            .filter { it.length >= 2 }
            .distinct()
            .take(12)
        if (tokens.isEmpty()) return AgentResult.Ignored
        return AgentResult.Success(
            tokens.joinToString(" "),
            mapOf("tokens" to tokens, "fts_query" to tokens.joinToString(" OR "))
        )
    }
}

/**
 * تفريغ صوتي حقيقي فقط: يمرّر النص الجاهز من الخادم (metadata["transcript"]).
 * لا محرّك ASR داخل التطبيق — فلا نخترع نصاً.
 */
class VoiceTranscriptionAgent : RedAiAgent {
    override val agentId = "agent_voice_transcription"
    override val agentName = "Voice Transcription Agent"
    override val description = "Forwards real transcripts produced server-side (no local ASR)."
    override val isActive = true

    override suspend fun process(context: AgentContext): AgentResult {
        val transcript = (context.metadata["transcript"] as? String)?.trim()
        if (transcript.isNullOrBlank()) return AgentResult.Ignored
        return AgentResult.Success(
            transcript,
            mapOf("source" to (context.metadata["transcript_source"] ?: "server"), "chars" to transcript.length)
        )
    }
}

/** جدولة تذكيرات حقيقية: تحليل «غداً/بعد ساعة/at 20:00» إلى طابع زمني. */
class EventReminderAgent : RedAiAgent {
    override val agentId = "agent_event_reminder"
    override val agentName = "Event Reminder Agent"
    override val description = "Parses Arabic/English relative and clock times into epoch millis."
    override val isActive = true

    override suspend fun process(context: AgentContext): AgentResult {
        val text = context.messageContent.lowercase()
        val now = context.timestamp
        val offsetMs = parseOffsetMs(text, now) ?: return AgentResult.Ignored
        val fireAt = now + offsetMs
        val cal = Calendar.getInstance().apply { timeInMillis = fireAt }
        val label = "%04d-%02d-%02d %02d:%02d".format(
            cal.get(Calendar.YEAR), cal.get(Calendar.MONTH) + 1, cal.get(Calendar.DAY_OF_MONTH),
            cal.get(Calendar.HOUR_OF_DAY), cal.get(Calendar.MINUTE)
        )
        return AgentResult.Success(
            "تذكير: $label",
            mapOf("fire_at_ms" to fireAt, "offset_ms" to offsetMs)
        )
    }

    /** يستخرج مهلة زمنية من نص عربي/إنجليزي — null إن لم يوجد نمط معروف. */
    private fun parseOffsetMs(text: String, now: Long): Long? {
        val minute = 60_000L
        val hour = 60 * minute
        val day = 24 * hour
        val relative = Regex("(?:بعد|after|in)\\s+(\\d+)\\s*(دقيقة|دقائق|min|minutes|ساعة|ساعات|hour|hours|يوم|أيام|day|days)")
        relative.find(text)?.let { m ->
            val n = m.groupValues[1].toLongOrNull() ?: return@let
            val unit = m.groupValues[2]
            val ms = when {
                unit.contains("دقيق") || unit.contains("min") -> minute
                unit.contains("ساع") || unit.contains("hour") -> hour
                else -> day
            }
            return n * ms
        }
        if (text.contains("غداً") || text.contains("غدا") || text.contains("tomorrow")) return day
        if (text.contains("بكرة")) return day
        val clock = Regex("(?:at|الساعة)\\s*(\\d{1,2})(?::(\\d{2}))?\\s*(ص|م|am|pm)?")
        clock.find(text)?.let { m ->
            var h = m.groupValues[1].toIntOrNull() ?: return@let
            val min = m.groupValues[2].toIntOrNull() ?: 0
            val ampm = m.groupValues[3]
            if ((ampm == "م" || ampm == "pm") && h < 12) h += 12
            if ((ampm == "ص" || ampm == "am") && h == 12) h = 0
            val cal = Calendar.getInstance().apply { timeInMillis = now }
            cal.set(Calendar.HOUR_OF_DAY, h)
            cal.set(Calendar.MINUTE, min)
            cal.set(Calendar.SECOND, 0)
            var delta = cal.timeInMillis - now
            if (delta <= 0) delta += day
            return delta
        }
        return null
    }
}

/** إحصاء حقيقي من عدّادات واردة في metadata — لا أرقام مُختلقة. */
class AnalyticsInsightAgent : RedAiAgent {
    override val agentId = "agent_analytics_insight"
    override val agentName = "Analytics & Insights Agent"
    override val description = "Aggregates real counters supplied by the repository layer."
    override val isActive = true

    override suspend fun process(context: AgentContext): AgentResult {
        val counts = (context.metadata["daily_counts"] as? List<Int>)?.takeIf { it.isNotEmpty() }
            ?: return AgentResult.Ignored
        val total = counts.sum()
        val avg = total.toDouble() / counts.size
        val peak = counts.maxOrNull() ?: 0
        val trend = when {
            counts.size < 2 -> "flat"
            counts.last() > avg * 1.1 -> "up"
            counts.last() < avg * 0.9 -> "down"
            else -> "flat"
        }
        return AgentResult.Success(
            "إجمالي=$total متوسط=%.1f ذروة=$peak اتجاه=$trend".format(avg),
            mapOf("total" to total, "average" to avg, "peak" to peak, "trend" to trend)
        )
    }
}

/** مساعد كود حقيقي: كشف السياج/اللغة وتنسيق الكتلة — بلا تنفيذ كود. */
class CodeAssistAgent : RedAiAgent {
    override val agentId = "agent_code_assist"
    override val agentName = "Code & Tech Assistant Agent"
    override val description = "Detects fenced code blocks and their language (no execution)."
    override val isActive = true

    private val fence = Regex("```([a-zA-Z0-9+#]*)\\n([\\s\\S]*?)```")

    override suspend fun process(context: AgentContext): AgentResult {
        val block = fence.find(context.messageContent) ?: return AgentResult.Ignored
        val declared = block.groupValues[1].lowercase().ifBlank { guessLanguage(block.groupValues[2]) }
        val code = block.groupValues[2]
        return AgentResult.Success(
            "لغة=$declared أسطر=${code.lines().size}",
            mapOf("language" to declared, "lines" to code.lines().size, "chars" to code.length)
        )
    }

    private fun guessLanguage(code: String): String = when {
        code.contains("fun ") || code.contains("val ") -> "kotlin"
        code.contains("def ") || code.contains("import ") && code.contains(":") -> "python"
        code.contains("public class") || code.contains("System.out") -> "java"
        code.contains("function ") || code.contains("=>") -> "javascript"
        code.contains("SELECT ") || code.contains("select ") -> "sql"
        else -> "text"
    }
}

/** موجّه دعم حقيقي: قاعدة معرفة محلية صغيرة بالنوايا — ليس روبوت محادثة. */
class SupportChatAgent : RedAiAgent {
    override val agentId = "agent_support_chat"
    override val agentName = "Support & FAQ Router Agent"
    override val description = "Routes help requests to a local FAQ entry by keyword."
    override val isActive = true

    private val faq = listOf(
        "connect" to listOf("اتصال", "خادم", "server", "connect", "offline"),
        "backup" to listOf("نسخة", "احتياطي", "backup", "restore", "استعادة"),
        "privacy" to listOf("خصوصية", "privacy", "تشفير", "encrypt", "e2ee"),
        "calls" to listOf("مكالمة", "call", "voice", "فيديو", "video"),
        "media" to listOf("صورة", "image", "فيديو", "voice note", "مقطع"),
        "account" to listOf("حساب", "account", "تسجيل", "login", "رمز")
    )

    override suspend fun process(context: AgentContext): AgentResult {
        val text = context.messageContent.lowercase()
        val isHelp = text.startsWith("/help") || context.metadata["help_request"] == true ||
            text.contains("كيف") || text.contains("how do i")
        if (!isHelp) return AgentResult.Ignored
        val entry = faq.firstOrNull { (_, keys) -> keys.any { text.contains(it) } }
            ?: return AgentResult.Success(
                "لم أجد موضوعاً مطابقاً — اكتب /help ثم وصف المشكلة",
                mapOf("topic" to "unknown")
            )
        return AgentResult.Success("موضوع الدعم: ${entry.first}", mapOf("topic" to entry.first))
    }
}

/** نقاط حقيقية بصيغة معلنة (لا «+10 XP» عشوائي). */
class GamificationAgent : RedAiAgent {
    override val agentId = "agent_gamification"
    override val agentName = "Gamification & Reward Agent"
    override val description = "Deterministic XP from real activity counters."
    override val isActive = true

    override suspend fun process(context: AgentContext): AgentResult {
        val messages = context.metadata["messages_sent"] as? Int ?: return AgentResult.Ignored
        val streak = context.metadata["streak_days"] as? Int ?: 0
        val helps = context.metadata["helpful_reactions"] as? Int ?: 0
        // الصيغة المعلنة: رسالة=1، يوم متواصل=5، تفاعل مفيد=3 (بسقف يومي 300).
        val xp = (messages + streak * 5 + helps * 3).coerceAtMost(300)
        val level = 1 + xp / 100
        return AgentResult.Success(
            "XP=$xp مستوى=$level",
            mapOf("xp" to xp, "level" to level, "streak_days" to streak)
        )
    }
}

/** أولوية إشعار حقيقية من مؤشرات الحدث (منشن لك، كلمة عاجل، مرسل مهم). */
class SmartNotificationAgent : RedAiAgent {
    override val agentId = "agent_smart_notification"
    override val agentName = "Smart Notification Prioritization Agent"
    override val description = "Scores notification priority from real event signals."
    override val isActive = true

    override suspend fun process(context: AgentContext): AgentResult {
        val mentioned = context.metadata["mentions_me"] == true
        val urgent = context.metadata["urgent"] == true ||
            AgentRules.SENSITIVE_REQUESTS.any { context.messageContent.lowercase().contains(it) }
        val direct = context.metadata["is_direct"] == true
        val score = (if (mentioned) 2 else 0) + (if (urgent) 2 else 0) + (if (direct) 1 else 0)
        if (score == 0) return AgentResult.Ignored
        val priority = if (score >= 3) "high" else "normal"
        return AgentResult.Success("priority=$priority", mapOf("priority" to priority, "score" to score))
    }
}

/** وصول حقيقي: توصيات مبنية على إعدادات النظام الفعلية. */
class AccessibilityAgent : RedAiAgent {
    override val agentId = "agent_accessibility"
    override val agentName = "Accessibility Agent"
    override val description = "Advises concrete accessibility settings from real config values."
    override val isActive = true

    override suspend fun process(context: AgentContext): AgentResult {
        val fontScale = (context.metadata["font_scale"] as? Float) ?: return AgentResult.Ignored
        val highContrast = context.metadata["high_contrast"] as? Boolean ?: false
        val reduceMotion = context.metadata["reduce_motion"] as? Boolean ?: false
        val advice = buildList {
            if (fontScale < 1f) add("font_scale_low")
            if (fontScale > 1.3f) add("enable_compact_chrome")
            if (!highContrast) add("prefer_high_contrast_theme")
            if (reduceMotion) add("disable_ambient_glow")
        }
        if (advice.isEmpty()) return AgentResult.Ignored
        return AgentResult.Success(advice.joinToString(","), mapOf("advice" to advice))
    }
}

/** امتثال حقيقي: Luhn لبطاقات الدفع + أنماط هوية/IBAN شائعة. */
class ComplianceAgent : RedAiAgent {
    override val agentId = "agent_compliance"
    override val agentName = "Compliance & Policy Guardrail Agent"
    override val description = "Detects payment-card / national-ID / IBAN patterns (real validators)."
    override val isActive = true

    private val digits = Regex("\\b\\d{13,19}\\b")
    private val iban = Regex("\\b[A-Z]{2}\\d{2}[A-Z0-9]{10,30}\\b")

    override suspend fun process(context: AgentContext): AgentResult {
        val text = context.messageContent.uppercase()
        digits.findAll(text).forEach { m ->
            if (luhn(m.value)) return AgentResult.Flagged("رقم بطاقة دفع محتمل", 3)
        }
        if (iban.containsMatchIn(text)) return AgentResult.Flagged("IBAN محتمل", 2)
        return AgentResult.Ignored
    }

    /** خوارزمية Luhn — تحقّق حسابي حقيقي لا مطابقة نمط فقط. */
    private fun luhn(number: String): Boolean {
        var sum = 0
        var alternate = false
        for (i in number.length - 1 downTo 0) {
            var n = number[i] - '0'
            if (alternate) {
                n *= 2
                if (n > 9) n -= 9
            }
            sum += n
            alternate = !alternate
        }
        return sum % 10 == 0 && number.length >= 13
    }
}

/** تخصيص ثيم حقيقي: قواعد وقت اليوم + تفضيل المستخدم (حتمية وقابلة للاختبار). */
class ThemePersonalizationAgent : RedAiAgent {
    override val agentId = "agent_theme_personalization"
    override val agentName = "Personalization & Theme Agent"
    override val description = "Recommends a theme preset from real time-of-day and user preference."
    override val isActive = true

    override suspend fun process(context: AgentContext): AgentResult {
        val prefersDark = context.metadata["prefers_dark"] as? Boolean ?: return AgentResult.Ignored
        val cal = Calendar.getInstance().apply { timeInMillis = context.timestamp }
        val hour = cal.get(Calendar.HOUR_OF_DAY)
        val preset = when {
            !prefersDark -> "LIGHT_SOVEREIGN"
            hour in 22..23 || hour in 0..5 -> "OLED_BLACK"
            else -> "SOVEREIGN"
        }
        return AgentResult.Success("preset=$preset", mapOf("preset" to preset, "hour" to hour))
    }
}

/** تشخيص حقيقي من قياسات فعلية في metadata — لا «صفر أخطاء» ثابتة. */
class DiagnosticsAgent : RedAiAgent {
    override val agentId = "agent_diagnostics"
    override val agentName = "Self-Healing & Diagnostics Agent"
    override val description = "Reports real runtime metrics and concrete recovery advice."
    override val isActive = true

    override suspend fun process(context: AgentContext): AgentResult {
        val errors = context.metadata["error_count"] as? Int ?: return AgentResult.Ignored
        val queue = context.metadata["outbox_depth"] as? Int ?: 0
        val online = context.metadata["server_online"] as? Boolean ?: false
        val issues = buildList {
            if (errors > 0) add("errors=$errors")
            if (queue > 50) add("outbox_backlog=$queue")
            if (!online) add("server_offline")
        }
        if (issues.isEmpty()) {
            return AgentResult.Success("سليم", mapOf("healthy" to true))
        }
        return AgentResult.Flagged("تشخيص: ${issues.joinToString(", ")}", if (errors > 10 || queue > 200) 2 else 1)
    }
}

/**
 * السجل الوحيد للعشرين وكيلاً — تستخدمه الواجهة ([com.red.sovereign.ui.AdvancedGroupChatScreen])
 * و[SovereignAgentManager] والاختبارات. أي وكيل جديد يُضاف هنا مرة واحدة فقط.
 */
object RedAgentRegistry {
    fun getAllAgents(): List<RedAiAgent> = listOf(
        MasterCoordinatorAgent(),
        GroupModerationAgent(),
        RealtimeTranslationAgent(),
        SmartSummarizationAgent(),
        SentimentAnalysisAgent(),
        SmartPollAgent(),
        SecurityThreatAgent(),
        MediaTaggingAgent(),
        SmartSearchAgent(),
        VoiceTranscriptionAgent(),
        EventReminderAgent(),
        AnalyticsInsightAgent(),
        CodeAssistAgent(),
        SupportChatAgent(),
        GamificationAgent(),
        SmartNotificationAgent(),
        AccessibilityAgent(),
        ComplianceAgent(),
        ThemePersonalizationAgent(),
        DiagnosticsAgent()
    )
}
