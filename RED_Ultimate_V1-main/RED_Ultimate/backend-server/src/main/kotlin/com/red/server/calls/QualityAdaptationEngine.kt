package com.red.server.calls

import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

/**
 * محرك التكيف التلقائي للجودة
 * يضبط الجودة بناءً على الشبكة والعتاد
 */
@Service
class QualityAdaptationEngine {
    companion object {
        private val log = LoggerFactory.getLogger(QualityAdaptationEngine::class.java)

        // نطاقات النطاق الترددي
        const val BANDWIDTH_EXCELLENT = 5000L // كبس
        const val BANDWIDTH_GOOD = 2000L
        const val BANDWIDTH_FAIR = 1000L
        const val BANDWIDTH_POOR = 500L

        // نطاقات الكمون
        const val LATENCY_EXCELLENT = 50L // ms
        const val LATENCY_GOOD = 100L
        const val LATENCY_FAIR = 200L
        const val LATENCY_POOR = 400L

        // عتبات فقدان الحزم
        const val PACKET_LOSS_EXCELLENT = 1f // %
        const val PACKET_LOSS_GOOD = 2f
        const val PACKET_LOSS_FAIR = 5f
        const val PACKET_LOSS_POOR = 10f
    }

    data class QualityProfile(
        val name: String,
        val videoWidth: Int,
        val videoHeight: Int,
        val videoBitrateMbps: Int,
        val videoFps: Int,
        val audioBitrateMbps: Int,
        val enableScreenShare: Boolean = true,
        val enableSimulcast: Boolean = true,
        val cpuUsageThreshold: Int = 80
    )

    data class NetworkMetrics(
        val roomId: String,
        val participantId: String,
        val timestamp: Long = System.currentTimeMillis(),
        val bandwidth: Long = 0, // kbps
        val latency: Long = 0, // ms
        val jitter: Long = 0, // ms
        val packetLoss: Float = 0f, // %
        val cpuUsage: Int = 0, // %
        val memoryUsage: Int = 0 // %
    )

    // ملفات الجودة المعرفة
    private val qualityProfiles = mapOf(
        "HD" to QualityProfile(
            name = "HD",
            videoWidth = 1280,
            videoHeight = 720,
            videoBitrateMbps = 2,
            videoFps = 30,
            audioBitrateMbps = 1,
            enableScreenShare = true,
            enableSimulcast = true,
            cpuUsageThreshold = 60
        ),
        "SD" to QualityProfile(
            name = "SD",
            videoWidth = 640,
            videoHeight = 480,
            videoBitrateMbps = 1,
            videoFps = 24,
            audioBitrateMbps = 0.5f.toInt(),
            enableScreenShare = true,
            enableSimulcast = true,
            cpuUsageThreshold = 70
        ),
        "LOW" to QualityProfile(
            name = "LOW",
            videoWidth = 320,
            videoHeight = 240,
            videoBitrateMbps = 500,
            videoFps = 15,
            audioBitrateMbps = 0.25f.toInt(),
            enableScreenShare = false,
            enableSimulcast = false,
            cpuUsageThreshold = 80
        ),
        "AUDIO_ONLY" to QualityProfile(
            name = "AUDIO_ONLY",
            videoWidth = 0,
            videoHeight = 0,
            videoBitrateMbps = 0,
            videoFps = 0,
            audioBitrateMbps = 0.1f.toInt(),
            enableScreenShare = false,
            enableSimulcast = false,
            cpuUsageThreshold = 90
        ),
        "4K" to QualityProfile(
            name = "4K",
            videoWidth = 3840,
            videoHeight = 2160,
            videoBitrateMbps = 8,
            videoFps = 60,
            audioBitrateMbps = 2,
            enableScreenShare = true,
            enableSimulcast = true,
            cpuUsageThreshold = 50
        )
    )

    // الملفات الحالية: roomId -> (participantId -> profileName)
    private val currentProfiles = ConcurrentHashMap<String, ConcurrentHashMap<String, String>>()

    // السجلات: roomId -> (participantId -> LinkedList<NetworkMetrics>)
    private val metricsHistory = ConcurrentHashMap<String, ConcurrentHashMap<String, MutableList<NetworkMetrics>>>()

    // تكيف مكبّر الصوت: roomId -> participantId
    private val lastAdaptationTime = ConcurrentHashMap<String, AtomicLong>()

    fun recordMetrics(metrics: NetworkMetrics) {
        val roomMetrics = metricsHistory.computeIfAbsent(metrics.roomId) { ConcurrentHashMap() }
        val history = roomMetrics.computeIfAbsent(metrics.participantId) { mutableListOf() }
        history.add(metrics)

        // احتفظ بـ أحدث 100 قياس فقط
        if (history.size > 100) {
            history.removeAt(0)
        }

        // حاول التكيف إذا مر دقيقة على آخر محاولة
        val lastTime = lastAdaptationTime.computeIfAbsent("${metrics.roomId}:${metrics.participantId}") { AtomicLong(0) }
        if (System.currentTimeMillis() - lastTime.get() > 60000) {
            lastTime.set(System.currentTimeMillis())
            adaptQuality(metrics)
        }
    }

    private fun adaptQuality(metrics: NetworkMetrics) {
        val currentProfile = getCurrentProfile(metrics.roomId, metrics.participantId)
        val newProfile = selectOptimalProfile(metrics)

        if (currentProfile != newProfile.name) {
            log.info("Quality adaptation: {} -> {} for participant in room {}",
                currentProfile, newProfile.name, metrics.roomId)
            setQualityProfile(metrics.roomId, metrics.participantId, newProfile.name)
        }
    }

    fun selectOptimalProfile(metrics: NetworkMetrics): QualityProfile {
        // حساب درجة الشبكة
        val score = calculateNetworkScore(metrics)

        return when {
            // إذا كانت الموارد ممتازة والشبكة جيدة
            metrics.cpuUsage < 40 && metrics.memoryUsage < 60 && score >= 90 -> qualityProfiles["4K"]!!

            // شبكة ممتازة
            metrics.bandwidth >= BANDWIDTH_EXCELLENT && score >= 80 && metrics.cpuUsage < 60 ->
                qualityProfiles["HD"]!!

            // شبكة جيدة
            metrics.bandwidth >= BANDWIDTH_GOOD && score >= 60 ->
                qualityProfiles["SD"]!!

            // شبكة متوسطة
            metrics.bandwidth >= BANDWIDTH_FAIR && score >= 40 ->
                qualityProfiles["LOW"]!!

            // شبكة ضعيفة أو استخدام عالي للموارد
            else -> qualityProfiles["AUDIO_ONLY"]!!
        }
    }

    private fun calculateNetworkScore(metrics: NetworkMetrics): Int {
        var score = 100

        // تقليل بناءً على النطاق الترددي
        score -= when {
            metrics.bandwidth < BANDWIDTH_POOR -> 40
            metrics.bandwidth < BANDWIDTH_FAIR -> 30
            metrics.bandwidth < BANDWIDTH_GOOD -> 20
            metrics.bandwidth < BANDWIDTH_EXCELLENT -> 10
            else -> 0
        }

        // تقليل بناءً على الكمون
        score -= when {
            metrics.latency > LATENCY_POOR -> 30
            metrics.latency > LATENCY_FAIR -> 20
            metrics.latency > LATENCY_GOOD -> 10
            else -> 0
        }

        // تقليل بناءً على فقدان الحزم
        score -= when {
            metrics.packetLoss > PACKET_LOSS_POOR -> 30
            metrics.packetLoss > PACKET_LOSS_FAIR -> 20
            metrics.packetLoss > PACKET_LOSS_GOOD -> 10
            else -> 0
        }

        // تقليل بناءً على استخدام العتاد
        score -= (metrics.cpuUsage - 40) / 2
        score -= (metrics.memoryUsage - 50) / 2

        return score.coerceIn(0, 100)
    }

    fun setQualityProfile(roomId: String, participantId: String, profileName: String): Boolean {
        if (!qualityProfiles.containsKey(profileName)) {
            log.warn("Unknown quality profile: {}", profileName)
            return false
        }
        currentProfiles.computeIfAbsent(roomId) { ConcurrentHashMap() }[participantId] = profileName
        return true
    }

    fun getCurrentProfile(roomId: String, participantId: String): String =
        currentProfiles[roomId]?.get(participantId) ?: "SD"

    fun getQualityProfile(profileName: String): QualityProfile? =
        qualityProfiles[profileName]

    fun getAvailableProfiles(): List<String> =
        qualityProfiles.keys.toList()

    fun getMetricsHistory(roomId: String, participantId: String, limit: Int = 20): List<NetworkMetrics> {
        val history = metricsHistory[roomId]?.get(participantId) ?: return emptyList()
        return history.takeLast(limit)
    }

    fun getLatestMetrics(roomId: String, participantId: String): NetworkMetrics? {
        val history = metricsHistory[roomId]?.get(participantId) ?: return null
        return history.lastOrNull()
    }

    fun getQualityStats(roomId: String, participantId: String): Map<String, Any> {
        val metrics = getLatestMetrics(roomId, participantId)
        val profile = getQualityProfile(getCurrentProfile(roomId, participantId))

        return mapOf(
            "currentProfile" to getCurrentProfile(roomId, participantId),
            "bandwidth" to (metrics?.bandwidth ?: 0),
            "latency" to (metrics?.latency ?: 0),
            "packetLoss" to (metrics?.packetLoss ?: 0f),
            "cpuUsage" to (metrics?.cpuUsage ?: 0),
            "memoryUsage" to (metrics?.memoryUsage ?: 0),
            "videoResolution" to "${profile?.videoWidth}x${profile?.videoHeight}",
            "fps" to (profile?.videoFps ?: 0),
            "videoBitrateMbps" to (profile?.videoBitrateMbps ?: 0)
        )
    }

    fun closeRoom(roomId: String) {
        currentProfiles.remove(roomId)
        metricsHistory.remove(roomId)
    }
}
