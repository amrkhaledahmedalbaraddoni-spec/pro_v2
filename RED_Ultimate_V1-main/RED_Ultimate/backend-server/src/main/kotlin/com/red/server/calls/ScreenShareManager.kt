package com.red.server.calls

import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap

/**
 * مدير مشاركة الشاشة
 * يدير البث والجودة والتوقف
 */
@Service
class ScreenShareManager {
    companion object {
        private val log = LoggerFactory.getLogger(ScreenShareManager::class.java)
        const val DEFAULT_WIDTH = 1920
        const val DEFAULT_HEIGHT = 1080
        const val DEFAULT_FPS = 30
        const val DEFAULT_BITRATE_KBPS = 3000
    }

    data class ScreenShare(
        val screenShareId: String,
        val roomId: String,
        val participantId: String,
        val userId: String,
        val displayName: String,
        val startedAt: Instant = Instant.now(),
        var pausedAt: Instant? = null,
        var width: Int = DEFAULT_WIDTH,
        var height: Int = DEFAULT_HEIGHT,
        var fps: Int = DEFAULT_FPS,
        var bitrateKbps: Int = DEFAULT_BITRATE_KBPS,
        var isActive: Boolean = true,
        var producers: Set<String> = emptySet(), // SSRC
        var viewers: Set<String> = emptySet() // participantIds
    )

    data class ScreenShareQuality(
        val resolution: String,
        val fps: Int,
        val bitrateKbps: Int
    )

    // شاشات النشطة: roomId -> ScreenShare
    private val activeScreenShares = ConcurrentHashMap<String, ScreenShare>()

    // سجل المشاركة: roomId -> LinkedList<ScreenShare>
    private val screenShareHistory = ConcurrentHashMap<String, MutableList<ScreenShare>>()

    // المشاهدون: screenShareId -> participantIds
    private val screenShareViewers = ConcurrentHashMap<String, MutableSet<String>>()

    // جودات مدعومة
    private val supportedQualities = listOf(
        ScreenShareQuality("1920x1080", 30, 3000),
        ScreenShareQuality("1280x720", 30, 2000),
        ScreenShareQuality("1024x768", 30, 1500),
        ScreenShareQuality("720x480", 24, 1000)
    )

    fun startScreenShare(
        roomId: String,
        participantId: String,
        userId: String,
        displayName: String
    ): ScreenShare? {
        // لا تسمح بمشاركة شاشة متعددة في نفس الوقت
        if (activeScreenShares.containsKey(roomId)) {
            log.warn("Screen share already active in room {}", roomId)
            return null
        }

        val screenShareId = "screenshare_${System.nanoTime()}"
        val screenShare = ScreenShare(
            screenShareId = screenShareId,
            roomId = roomId,
            participantId = participantId,
            userId = userId,
            displayName = displayName
        )

        activeScreenShares[roomId] = screenShare
        screenShareHistory.computeIfAbsent(roomId) { mutableListOf() }.add(screenShare)
        screenShareViewers[screenShareId] = ConcurrentHashMap.newKeySet()

        log.info("Screen share started in room {} by participant {}", roomId, participantId)
        return screenShare
    }

    fun stopScreenShare(roomId: String, participantId: String): Boolean {
        val screenShare = activeScreenShares[roomId] ?: return false
        if (screenShare.participantId != participantId) {
            log.warn("Participant {} cannot stop screen share from {}", participantId, screenShare.participantId)
            return false
        }

        screenShare.apply {
            isActive = false
            pausedAt = Instant.now()
        }

        activeScreenShares.remove(roomId)
        screenShareViewers.remove(screenShare.screenShareId)

        log.info("Screen share stopped in room {} by participant {}", roomId, participantId)
        return true
    }

    fun pauseScreenShare(roomId: String, participantId: String): Boolean {
        val screenShare = activeScreenShares[roomId] ?: return false
        if (screenShare.participantId != participantId) return false

        screenShare.pausedAt = Instant.now()
        log.debug("Screen share paused in room {}", roomId)
        return true
    }

    fun resumeScreenShare(roomId: String, participantId: String): Boolean {
        val screenShare = activeScreenShares[roomId] ?: return false
        if (screenShare.participantId != participantId) return false

        screenShare.pausedAt = null
        log.debug("Screen share resumed in room {}", roomId)
        return true
    }

    fun getActiveScreenShare(roomId: String): ScreenShare? =
        activeScreenShares[roomId]

    fun isScreenSharing(roomId: String): Boolean =
        activeScreenShares.containsKey(roomId)

    fun getScreenShareId(roomId: String): String? =
        activeScreenShares[roomId]?.screenShareId

    // ── إدارة الجودة ────────────────────────────────────────────────────────
    fun setScreenShareQuality(
        roomId: String,
        participantId: String,
        resolution: String,
        fps: Int,
        bitrateKbps: Int
    ): Boolean {
        val screenShare = activeScreenShares[roomId] ?: return false
        if (screenShare.participantId != participantId) return false

        screenShare.apply {
            val (w, h) = parseResolution(resolution)
            width = w
            height = h
            this.fps = fps.coerceIn(1, 60)
            this.bitrateKbps = bitrateKbps.coerceIn(500, 8000)
        }

        log.info("Screen share quality updated: {}x{} @ {}fps, {}kbps",
            screenShare.width, screenShare.height, screenShare.fps, screenShare.bitrateKbps)
        return true
    }

    fun getOptimalQuality(availableBandwidth: Long): ScreenShareQuality {
        val bandwidthMbps = availableBandwidth / 1000
        return supportedQualities.lastOrNull { it.bitrateKbps <= bandwidthMbps }
            ?: supportedQualities.last()
    }

    fun getSupportedQualities(): List<ScreenShareQuality> = supportedQualities

    private fun parseResolution(resolution: String): Pair<Int, Int> {
        val parts = resolution.split("x")
        return if (parts.size == 2) {
            Pair(parts[0].toIntOrNull() ?: DEFAULT_WIDTH, parts[1].toIntOrNull() ?: DEFAULT_HEIGHT)
        } else {
            Pair(DEFAULT_WIDTH, DEFAULT_HEIGHT)
        }
    }

    // ── إدارة المشاهدين ────────────────────────────────────────────────────────
    fun addViewer(screenShareId: String, participantId: String): Boolean {
        screenShareViewers[screenShareId]?.add(participantId)
        log.debug("Participant {} joined screen share {}", participantId, screenShareId)
        return true
    }

    fun removeViewer(screenShareId: String, participantId: String): Boolean {
        screenShareViewers[screenShareId]?.remove(participantId)
        log.debug("Participant {} left screen share {}", participantId, screenShareId)
        return true
    }

    fun getViewers(screenShareId: String): Set<String> =
        screenShareViewers[screenShareId]?.toSet() ?: emptySet()

    fun getViewerCount(screenShareId: String): Int =
        screenShareViewers[screenShareId]?.size ?: 0

    // ── إدارة SSRC ────────────────────────────────────────────────────────
    fun addProducer(screenShareId: String, ssrc: String): Boolean {
        val screenShare = activeScreenShares.values.firstOrNull { it.screenShareId == screenShareId } ?: return false
        screenShare.producers = screenShare.producers + ssrc
        return true
    }

    fun removeProducer(screenShareId: String, ssrc: String): Boolean {
        val screenShare = activeScreenShares.values.firstOrNull { it.screenShareId == screenShareId } ?: return false
        screenShare.producers = screenShare.producers - ssrc
        return true
    }

    fun getScreenShareStats(roomId: String): Map<String, Any>? {
        val screenShare = activeScreenShares[roomId] ?: return null
        return mapOf(
            "screenShareId" to screenShare.screenShareId,
            "participantId" to screenShare.participantId,
            "displayName" to screenShare.displayName,
            "startedAt" to screenShare.startedAt.toString(),
            "isPaused" to (screenShare.pausedAt != null),
            "resolution" to "${screenShare.width}x${screenShare.height}",
            "fps" to screenShare.fps,
            "bitrateKbps" to screenShare.bitrateKbps,
            "viewers" to screenShare.viewers.size
        )
    }

    fun closeRoom(roomId: String) {
        activeScreenShares.remove(roomId)
        screenShareHistory.remove(roomId)
    }
}
