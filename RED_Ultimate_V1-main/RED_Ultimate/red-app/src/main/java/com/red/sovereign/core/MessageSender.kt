package com.red.sovereign.core

import android.content.Context
import com.red.sovereign.core.database.LocalHistoryEntity
import com.red.sovereign.core.database.LocalRepository
import com.red.sovereign.core.database.OutboxMessageEntity
import com.red.sovereign.core.database.RedDatabase
import com.red.sovereign.crypto.DecryptedMessage
import com.red.sovereign.crypto.DecryptedMessageBus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * MessageSender - إرسال موثوق للرسائل
 *
 * المشكلة السابقة: الرسائل لا تظهر لا للمرسل ولا للمستقبل
 * السبب: الاعتماد على ForegroundService لحفظ العرض المتفائل
 * - على Android 12+، startForegroundService من الخلفية يرمي ForegroundServiceStartNotAllowedException
 * - عند الفشل، لا يُحفظ العرض المتفائل ولا تُرسل الرسالة
 *
 * الحل: حفظ متفائل فوري في UI قبل محاولة بدء الخدمة
 * - حفظ في Room + بث لـ DecryptedMessageBus + تحديث صف المحادثة
 * - ثم محاولة الإرسال عبر الخدمة (مع fallback)
 * - حتى لو فشل الإرسال، الرسالة تظهر محلياً مع حالة FAILED
 *
 * ## المرحلة الجديدة — Outbox معاملاتي (موحّد مع OutboxMessageEntity):
 * كل رسالة فردية صادرة تُكتب أيضاً في `outbox_messages` داخل نفس معاملة Room
 * مع `LocalHistoryEntity` (قاعدة OutboxRepository الذهبية). `OutboxRetryWorker`
 * يعيد الإرسال حتى SERVER_ACK عند طفو السيرفر أو موت العملية.
 *
 * ضمانات عدم التكرار:
 * - `id = idempotencyKey = clientId` (فهرس unique على idempotencyKey).
 * - الإدراج REPLACE بنفس المفتاح — إعادة الكتابة لا تُنشئ شبحاً ثانياً.
 * - الخدمة ترسل بنفس clientId والإقرار SERVER_ACK يُتم الصف — handoff فقط.
 *
 * المجموعات: لها redrive مخصص (red_group_outbox + MessageRecoveryWorker) —
 * نتجنب تسجيلها هنا لتفادي قناة إعادة إرسال ثانية تتنافس مع المسار المتخصص.
 */
object MessageSender {

    /**
     * إرسال نص عادي - يحفظ متفائلاً أولاً ثم يرسل
     */
    fun sendText(
        context: Context,
        repository: LocalRepository,
        targetRedId: String,
        conversationId: String,
        text: String,
        clientId: String = UuidV7.next()
    ) {
        val payload = text.toByteArray(Charsets.UTF_8)
        // حفظ متفائل فوري في UI - لا يعتمد على الخدمة
        saveOptimisticLocally(
            context = context,
            repository = repository,
            id = clientId,
            conversationId = conversationId,
            senderId = getMyRedId(context),
            payload = payload,
            type = "TEXT",
            peerId = targetRedId
        )
        // ثم إرسال عبر الخدمة (مع محاولة مباشرة كـ fallback)
        try {
            RedConnectionService.sendText(context, targetRedId, conversationId, text, clientId)
        } catch (e: Exception) {
            android.util.Log.w("MessageSender", "sendText via service failed, optimistic saved", e)
        }
    }

    /**
     * إرسال رسالة غنية (RichMessage) - يحفظ متفائلاً أولاً
     */
    fun sendRich(
        context: Context,
        repository: LocalRepository,
        targetRedId: String,
        conversationId: String,
        message: RichMessage,
        clientId: String = UuidV7.next()
    ) {
        val payload = RichMessage.encode(message)
        saveOptimisticLocally(
            context = context,
            repository = repository,
            id = clientId,
            conversationId = conversationId,
            senderId = getMyRedId(context),
            payload = payload,
            type = "RICH_TEXT",
            peerId = targetRedId
        )
        try {
            RedConnectionService.sendRichText(context, targetRedId, conversationId, message, clientId)
        } catch (e: Exception) {
            android.util.Log.w("MessageSender", "sendRich via service failed", e)
        }
    }

    /**
     * إرسال مجموعة نص عادي
     */
    fun sendGroupText(
        context: Context,
        repository: LocalRepository,
        group: com.red.sovereign.groups.Group,
        text: String,
        clientId: String = UuidV7.next()
    ) {
        val payload = text.toByteArray(Charsets.UTF_8)
        saveOptimisticLocally(
            context = context,
            repository = repository,
            id = clientId,
            conversationId = group.id,
            senderId = getMyRedId(context),
            payload = payload,
            type = "GROUP_MESSAGE",
            peerId = group.id,
            enqueueOutbox = false
        )
        try {
            RedConnectionService.sendGroupText(context, group, text, clientId)
        } catch (e: Exception) {
            android.util.Log.w("MessageSender", "sendGroupText failed", e)
        }
    }

    /**
     * إرسال مجموعة غني
     */
    fun sendGroupRich(
        context: Context,
        repository: LocalRepository,
        group: com.red.sovereign.groups.Group,
        message: RichMessage,
        clientId: String = UuidV7.next()
    ) {
        val payload = RichMessage.encode(message)
        saveOptimisticLocally(
            context = context,
            repository = repository,
            id = clientId,
            conversationId = group.id,
            senderId = getMyRedId(context),
            payload = payload,
            type = "RICH_TEXT",
            peerId = group.id,
            enqueueOutbox = false
        )
        try {
            RedConnectionService.sendGroupRichText(context, group, message, clientId)
        } catch (e: Exception) {
            android.util.Log.w("MessageSender", "sendGroupRich failed", e)
        }
    }

    private fun saveOptimisticLocally(
        context: Context,
        repository: LocalRepository,
        id: String,
        conversationId: String,
        senderId: String,
        payload: ByteArray,
        type: String,
        peerId: String?,
        /** المجموعات لها redrive مخصص — لا تُسجَّل في outbox الفردي (منع قناة إعادة مزدوجة). */
        enqueueOutbox: Boolean = true
    ) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                repository.saveLocalHistory(
                    LocalHistoryEntity(id, conversationId, senderId, payload, type, System.currentTimeMillis(), true)
                )
                DecryptedMessageBus.publish(
                    DecryptedMessage(id, conversationId, senderId, payload, System.currentTimeMillis(), 0, type = type, outgoing = true)
                )
                if (peerId != null) {
                    val preview = decodePreview(payload)
                    repository.onMessageStored(conversationId, peerId, preview, System.currentTimeMillis(), isIncoming = false)
                }
            } catch (e: Exception) {
                android.util.Log.e("MessageSender", "optimistic save failed for $id", e)
            }
            // ── Outbox معاملاتي (فردي فقط) ──
            // يُكتب بعد الحفظ المتفائل في نفس نطاق IO. المفتاح = clientId فيطابق
            // LocalHistoryEntity (فهرس unique على idempotencyKey يمنع الشبح الثاني)،
            // وSERVER_ACK من RedConnectionService هو من ينهي الصف — لا حذف هنا.
            // المجموعات مستثناة: redrive المجموعات المخصص يملكها (لا تنافس مزدوج).
            // P0 2026-09-27: كان الشرط `peerId == conversationId` مستحيلاً في الفردي —
            // conversationId معرّف SHA-256 بينما peerId هو RED ID — فلا يُكتب صف outbox
            // أبداً ولا تُعاد أي رسالة بعد فشل/طفو السيرفر. الآن القرار صريح بالمعامل.
            if (enqueueOutbox && peerId != null && !RedConnectionService.isGroupConversation(conversationId)) {
                try {
                    val db = RedDatabase.getInstance(context)
                    val entity = OutboxMessageEntity.create(
                        id = id,
                        conversationId = conversationId,
                        payload = payload,
                        type = if (type == "RICH_TEXT") "RICH_TEXT" else "CHAT",
                        targetRedId = peerId,
                        idempotencyKey = id
                    )
                    db.outboxDao().insert(entity) // REPLACE — idempotent بنفس المفتاح
                    // إرسال معلقة؟ أكد المفتاح: معرّف الهدف هو ما تقرؤه trySendViaService.
                    com.red.sovereign.core.outbox.OutboxRetryWorker.schedule(context)
                } catch (e: Exception) {
                    // لا فشل العرض المتفائل بسبب الـoutbox — العامل الدوري سيُجدول لاحقاً
                    android.util.Log.w("MessageSender", "outbox enqueue failed for $id (periodic worker covers)", e)
                }
            }
        }
    }

    private fun getMyRedId(context: Context): String {
        return try {
            com.red.sovereign.auth.TokenStore(context).redId.orEmpty()
        } catch (_: Exception) { "" }
    }

    private fun decodePreview(payload: ByteArray): String {
        return try {
            val text = String(payload, Charsets.UTF_8)
            val rich = RichMessage.decode(payload)
            rich?.text?.take(120) ?: text.take(120)
        } catch (_: Exception) {
            try { String(payload, Charsets.UTF_8).take(120) } catch (_: Exception) { "رسالة" }
        }
    }
}
