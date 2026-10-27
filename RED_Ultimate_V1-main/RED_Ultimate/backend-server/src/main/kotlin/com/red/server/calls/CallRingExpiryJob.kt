package com.red.server.calls

import com.red.server.websocket.CallWebSocketHandler
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

@Component
class CallRingExpiryJob(
    private val history: CallHistoryService,
    private val callWebSocketHandler: CallWebSocketHandler,
    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private val delivery: UnifiedCallDeliveryService? = null
) {
    private val log = LoggerFactory.getLogger(CallRingExpiryJob::class.java)

    @Scheduled(fixedDelay = 10_000, initialDelay = 15_000)
    fun expireUnanswered() {
        // 1) RINGING الأقدم من 45s → MISSED، مع استلام المستندات لإشعار أطرافها.
        val expired = runCatching { history.expireStaleRingingWithDocs() }.getOrDefault(emptyList())
        // 2) لكل منتهية: المتصل يعلم NO_ANSWER (حيّاً أو صندوقاً) بدل الصمت،
        //    وتُسقط حالتها المعلقة وطوابيرها حتى لا يرنّ عرض ميت لاحقاً.
        expired.forEach { doc ->
            runCatching {
                delivery?.discardPending(doc.id)
                callWebSocketHandler.cancelPendingCall(doc.id)
                callWebSocketHandler.purgeQueuedCall(doc.id, doc.targetId)
                callWebSocketHandler.deliverSignal(
                    targetRedId = doc.initiatorId,
                    type = "END",
                    roomId = doc.id,
                    sourceRedId = doc.targetId,
                    mode = expiryMode(doc.type),
                    payload = mapOf("reason" to "NO_ANSWER", "expired" to true)
                )
            }.onFailure { e ->
                log.debug("Expiry notify failed for call={}: {}", doc.id, e.message)
            }
        }
        callWebSocketHandler.cleanupStaleGroups()
    }

    private fun expiryMode(type: CallType): String = when (type) {
        CallType.VIDEO_1V1 -> "VIDEO"
        CallType.GROUP_VIDEO -> "GROUP_VIDEO"
        CallType.GROUP_AUDIO -> "GROUP"
        CallType.LIVE_STREAM -> "LIVE"
        CallType.SPACE -> "SPACE"
        else -> "VOICE"
    }
}
