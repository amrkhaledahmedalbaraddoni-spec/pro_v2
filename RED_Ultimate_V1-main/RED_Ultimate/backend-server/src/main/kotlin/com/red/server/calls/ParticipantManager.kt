package com.red.server.calls

import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

/**
 * مدير المشاركين في الاجتماعات والمساحات الصوتية
 * يدير الأدوار والصلاحيات والحالة
 */
@Service
class ParticipantManager {
    companion object {
        private val log = LoggerFactory.getLogger(ParticipantManager::class.java)
        const val RAISE_HAND_TTL_MS = 60000L // مدة رفع اليد
        const val SPEAKING_COOLDOWN_MS = 500L // أدنى فترة بين تحديثات المتحدث
    }

    data class Participant(
        val participantId: String,
        val userId: String,
        val redId: String,
        val displayName: String,
        val role: ParticipantRole = ParticipantRole.PARTICIPANT,
        val joinedAt: Instant = Instant.now(),
        var mutedAudio: Boolean = false,
        var mutedVideo: Boolean = false,
        var screenSharing: Boolean = false,
        var handRaised: Boolean = false,
        var raisedHandAt: Long? = null,
        var speakingLevel: Int = 0, // 0-100
        var lastSpokeAt: Long = 0,
        var audioSSRC: Long? = null,
        var videoSSRC: Long? = null,
        var connectionState: ConnectionState = ConnectionState.CONNECTED,
        var networkQuality: NetworkQuality = NetworkQuality.GOOD,
        var bandwidth: Long = 0, // bps
        var latency: Long = 0, // ms
        var jitter: Long = 0, // ms
        var packetLoss: Float = 0f // 0-100%
    )

    enum class ParticipantRole {
        HOST,           // مضيف الاجتماع
        PRESENTER,      // مقدم (يمكنه التحكم)
        PARTICIPANT,    // مشارك عادي
        GUEST           // ضيف (مشاهد فقط)
    }

    enum class ConnectionState {
        CONNECTING,
        CONNECTED,
        DEGRADED,
        RECONNECTING,
        DISCONNECTED
    }

    enum class NetworkQuality {
        EXCELLENT,
        GOOD,
        FAIR,
        POOR,
        VERY_POOR
    }

    // المشاركون النشطون: roomId -> (participantId -> Participant)
    private val roomParticipants = ConcurrentHashMap<String, ConcurrentHashMap<String, Participant>>()

    // عد المشاركين: roomId -> count
    private val participantCounts = ConcurrentHashMap<String, AtomicInteger>()

    // المتحدثون النشطون: roomId -> participantId
    private val activeSpeakers = ConcurrentHashMap<String, String>()

    // سجل المتحدثين: roomId -> LinkedList<(participantId, timestamp)>
    private val speakingHistory = ConcurrentHashMap<String, MutableList<Pair<String, Long>>>()

    // الأيدي المرفوعة: roomId -> LinkedSet<participantId>
    private val raisedHands = ConcurrentHashMap<String, LinkedHashSet<String>>()

    fun createRoom(roomId: String) {
        roomParticipants.putIfAbsent(roomId, ConcurrentHashMap())
        participantCounts.putIfAbsent(roomId, AtomicInteger(0))
        speakingHistory.putIfAbsent(roomId, mutableListOf())
        raisedHands.putIfAbsent(roomId, linkedSetOf())
        log.debug("Participant tracking initialized for room {}", roomId)
    }

    fun addParticipant(
        roomId: String,
        participantId: String,
        userId: String,
        redId: String,
        displayName: String,
        role: ParticipantRole = ParticipantRole.PARTICIPANT,
        mutedByDefault: Boolean = false
    ): Participant? {
        createRoom(roomId)
        val participant = Participant(
            participantId = participantId,
            userId = userId,
            redId = redId,
            displayName = displayName,
            role = role,
            mutedAudio = mutedByDefault,
            mutedVideo = mutedByDefault
        )
        roomParticipants[roomId]?.put(participantId, participant)
        participantCounts[roomId]?.incrementAndGet()
        log.info("Participant {} joined room {} as {}", participantId, roomId, role)
        return participant
    }

    fun removeParticipant(roomId: String, participantId: String) {
        val removed = roomParticipants[roomId]?.remove(participantId)
        if (removed != null) {
            participantCounts[roomId]?.decrementAndGet()
            raisedHands[roomId]?.remove(participantId)
            speakingHistory[roomId]?.removeAll { it.first == participantId }
            if (activeSpeakers[roomId] == participantId) {
                activeSpeakers.remove(roomId)
            }
            log.info("Participant {} left room {}", participantId, roomId)
        }
    }

    fun getParticipant(roomId: String, participantId: String): Participant? =
        roomParticipants[roomId]?.get(participantId)

    fun getParticipants(roomId: String): List<Participant> =
        roomParticipants[roomId]?.values?.toList() ?: emptyList()

    fun getParticipantCount(roomId: String): Int =
        participantCounts[roomId]?.get() ?: 0

    // ── إدارة الصوت والفيديو ────────────────────────────────────────────────
    fun setAudioMuted(roomId: String, participantId: String, muted: Boolean) {
        roomParticipants[roomId]?.get(participantId)?.mutedAudio = muted
        log.debug("Participant {} audio muted: {}", participantId, muted)
    }

    fun setVideoMuted(roomId: String, participantId: String, muted: Boolean) {
        roomParticipants[roomId]?.get(participantId)?.mutedVideo = muted
        log.debug("Participant {} video muted: {}", participantId, muted)
    }

    fun setScreenSharing(roomId: String, participantId: String, sharing: Boolean) {
        roomParticipants[roomId]?.get(participantId)?.screenSharing = sharing
        log.info("Participant {} screen sharing: {}", participantId, sharing)
    }

    fun isAudioMuted(roomId: String, participantId: String): Boolean =
        roomParticipants[roomId]?.get(participantId)?.mutedAudio ?: false

    fun isVideoMuted(roomId: String, participantId: String): Boolean =
        roomParticipants[roomId]?.get(participantId)?.mutedVideo ?: false

    fun isScreenSharing(roomId: String, participantId: String): Boolean =
        roomParticipants[roomId]?.get(participantId)?.screenSharing ?: false

    // ── إدارة رفع اليد ────────────────────────────────────────────────────────
    fun raiseHand(roomId: String, participantId: String): Boolean {
        raisedHands[roomId]?.add(participantId)
        val now = System.currentTimeMillis()
        roomParticipants[roomId]?.get(participantId)?.apply {
            handRaised = true
            raisedHandAt = now
        }
        log.debug("Participant {} raised hand in room {}", participantId, roomId)
        return true
    }

    fun lowerHand(roomId: String, participantId: String): Boolean {
        raisedHands[roomId]?.remove(participantId)
        roomParticipants[roomId]?.get(participantId)?.apply {
            handRaised = false
            raisedHandAt = null
        }
        log.debug("Participant {} lowered hand in room {}", participantId, roomId)
        return true
    }

    fun getRaisedHands(roomId: String): List<String> {
        // قم بتنظيف الأيدي المرفوعة المنتهية الصلاحية
        val now = System.currentTimeMillis()
        val hands = raisedHands[roomId] ?: return emptyList()
        hands.removeAll { participantId ->
            val p = roomParticipants[roomId]?.get(participantId)
            p?.raisedHandAt?.let { now - it > RAISE_HAND_TTL_MS } ?: false
        }
        return hands.toList()
    }

    // ── إدارة المتحدثين النشطين ────────────────────────────────────────────
    fun updateSpeakingLevel(roomId: String, participantId: String, level: Int) {
        val now = System.currentTimeMillis()
        val participant = roomParticipants[roomId]?.get(participantId) ?: return

        participant.speakingLevel = level.coerceIn(0, 100)

        // تحديث المتحدث النشط إذا كان مستوى الصوت عالياً
        if (level > 30) {
            val lastActive = activeSpeakers[roomId]
            if (lastActive != participantId) {
                participant.lastSpokeAt = now
                activeSpeakers[roomId] = participantId
                // إضافة إلى السجل
                speakingHistory[roomId]?.add(participantId to now)
            }
        }
    }

    fun getActiveSpeaker(roomId: String): String? =
        activeSpeakers[roomId]

    fun getSpeakingHistory(roomId: String, limit: Int = 10): List<String> {
        val history = speakingHistory[roomId] ?: return emptyList()
        return history.takeLast(limit).map { it.first }.distinct()
    }

    // ── إدارة جودة الشبكة ────────────────────────────────────────────────────
    fun updateNetworkQuality(
        roomId: String,
        participantId: String,
        bandwidth: Long,
        latency: Long,
        jitter: Long,
        packetLoss: Float
    ) {
        roomParticipants[roomId]?.get(participantId)?.apply {
            this.bandwidth = bandwidth
            this.latency = latency
            this.jitter = jitter
            this.packetLoss = packetLoss.coerceIn(0f, 100f)
            this.networkQuality = calculateNetworkQuality(latency, jitter, packetLoss)
        }
    }

    fun getNetworkQuality(roomId: String, participantId: String): NetworkQuality? =
        roomParticipants[roomId]?.get(participantId)?.networkQuality

    fun getDegradedParticipants(roomId: String): List<String> =
        roomParticipants[roomId]?.values
            ?.filter { it.networkQuality in listOf(NetworkQuality.POOR, NetworkQuality.VERY_POOR) }
            ?.map { it.participantId }
            ?: emptyList()

    private fun calculateNetworkQuality(latency: Long, jitter: Long, packetLoss: Float): NetworkQuality =
        when {
            latency < 50 && jitter < 10 && packetLoss < 1f -> NetworkQuality.EXCELLENT
            latency < 100 && jitter < 20 && packetLoss < 2f -> NetworkQuality.GOOD
            latency < 200 && jitter < 50 && packetLoss < 5f -> NetworkQuality.FAIR
            latency < 400 && jitter < 100 && packetLoss < 10f -> NetworkQuality.POOR
            else -> NetworkQuality.VERY_POOR
        }

    fun setConnectionState(roomId: String, participantId: String, state: ConnectionState) {
        roomParticipants[roomId]?.get(participantId)?.connectionState = state
        log.debug("Participant {} connection state: {}", participantId, state)
    }

    fun getConnectionState(roomId: String, participantId: String): ConnectionState? =
        roomParticipants[roomId]?.get(participantId)?.connectionState

    fun setSSRC(roomId: String, participantId: String, audioSSRC: Long? = null, videoSSRC: Long? = null) {
        roomParticipants[roomId]?.get(participantId)?.apply {
            audioSSRC?.let { this.audioSSRC = it }
            videoSSRC?.let { this.videoSSRC = it }
        }
    }

    fun closeRoom(roomId: String) {
        roomParticipants.remove(roomId)
        participantCounts.remove(roomId)
        speakingHistory.remove(roomId)
        raisedHands.remove(roomId)
        activeSpeakers.remove(roomId)
        log.info("Participant tracking closed for room {}", roomId)
    }

    fun getRoomStats(roomId: String): Map<String, Any> {
        val participants = getParticipants(roomId)
        // نوع صريح: بدون <String, Any> يستنتج Kotlin
        // Map<String, Comparable<*>? & Serializable?> ويفشل في مطابقة نوع الإرجاع.
        return mapOf<String, Any>(
            "totalParticipants" to participants.size,
            "audioEnabled" to participants.count { !it.mutedAudio },
            "videoEnabled" to participants.count { !it.mutedVideo },
            "screenSharing" to participants.count { it.screenSharing },
            "handRaised" to getRaisedHands(roomId).size,
            "activeSpeaker" to (getActiveSpeaker(roomId) ?: ""),
            "degradedConnections" to getDegradedParticipants(roomId).size
        )
    }
}
