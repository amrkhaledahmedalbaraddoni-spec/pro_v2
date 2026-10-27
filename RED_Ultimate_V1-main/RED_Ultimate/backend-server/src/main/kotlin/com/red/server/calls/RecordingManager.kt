package com.red.server.calls

import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap

/**
 * مدير التسجيل
 * يدير تسجيل المكالمات والاجتماعات والبث المباشر
 */
@Service
class RecordingManager {
    companion object {
        private val log = LoggerFactory.getLogger(RecordingManager::class.java)
        const val MAX_RECORDING_DURATION_HOURS = 24L
        const val SEGMENT_DURATION_SECONDS = 10L
    }

    enum class RecordingType {
        CONFERENCE,    // اجتماع جماعي
        AUDIO_SPACE,   // مساحة صوتية
        LIVESTREAM     // بث مباشر
    }

    enum class RecordingState {
        INITIALIZING,
        RECORDING,
        PAUSED,
        STOPPING,
        STOPPED,
        PROCESSING,
        COMPLETED,
        FAILED
    }

    enum class RecordingLayout {
        GRID,          // عرض شبكة
        SPEAKER,       // عرض المتحدث
        PIP,           // Picture in Picture
        AUDIO_ONLY,    // صوت فقط
        CUSTOM         // تخطيط مخصص
    }

    data class Recording(
        val recordingId: String,
        val roomId: String,
        val type: RecordingType,
        val initiatedBy: String,
        val startedAt: Instant = Instant.now(),
        var stoppedAt: Instant? = null,
        var state: RecordingState = RecordingState.INITIALIZING,
        var layout: RecordingLayout = RecordingLayout.GRID,
        var width: Int = 1920,
        var height: Int = 1080,
        var fps: Int = 30,
        var videoBitrateMbps: Int = 5,
        var audioBitrateMbps: Int = 1,
        var fileSize: Long = 0, // bytes
        var filePath: String = "",
        var hlsUrl: String? = null,
        var vodUrl: String? = null,
        var participants: Set<String> = emptySet(),
        var segments: Int = 0,
        var duration: Long = 0 // seconds
    )

    data class RecordingSegment(
        val segmentId: String,
        val recordingId: String,
        val sequenceNumber: Int,
        val startTime: Long, // relative to recording start
        val duration: Long,  // seconds
        val path: String,
        val size: Long       // bytes
    )

    // التسجيلات النشطة: recordingId -> Recording
    private val activeRecordings = ConcurrentHashMap<String, Recording>()

    // التسجيلات حسب الغرفة: roomId -> LinkedList<recordingId>
    private val roomRecordings = ConcurrentHashMap<String, MutableList<String>>()

    // الحلقات: recordingId -> LinkedList<RecordingSegment>
    private val recordingSegments = ConcurrentHashMap<String, MutableList<RecordingSegment>>()

    // السجل المكتمل: recordingId -> Recording
    private val completedRecordings = ConcurrentHashMap<String, Recording>()

    fun startRecording(
        roomId: String,
        type: RecordingType,
        initiatedBy: String,
        layout: RecordingLayout = RecordingLayout.GRID,
        width: Int = 1920,
        height: Int = 1080,
        fps: Int = 30
    ): Recording? {
        // تحقق من أنه لا يوجد تسجيل نشط بالفعل
        val existingRecording = roomRecordings[roomId]?.lastOrNull()?.let { activeRecordings[it] }
        if (existingRecording?.state == RecordingState.RECORDING) {
            log.warn("Recording already in progress for room {}", roomId)
            return null
        }

        val recordingId = "recording_${System.nanoTime()}"
        val recording = Recording(
            recordingId = recordingId,
            roomId = roomId,
            type = type,
            initiatedBy = initiatedBy,
            layout = layout,
            width = width,
            height = height,
            fps = fps,
            state = RecordingState.RECORDING
        )

        activeRecordings[recordingId] = recording
        roomRecordings.computeIfAbsent(roomId) { mutableListOf() }.add(recordingId)
        recordingSegments[recordingId] = mutableListOf()

        log.info("Recording started: {} (type={}, layout={}, resolution={}x{})",
            recordingId, type, layout, width, height)
        return recording
    }

    fun stopRecording(recordingId: String): Recording? {
        val recording = activeRecordings[recordingId] ?: return null

        recording.apply {
            stoppedAt = Instant.now()
            state = RecordingState.STOPPING
            duration = java.time.Duration.between(startedAt, stoppedAt).seconds
        }

        log.info("Recording stopped: {} (duration: {} seconds)", recordingId, recording.duration)
        return recording
    }

    fun pauseRecording(recordingId: String): Boolean {
        val recording = activeRecordings[recordingId] ?: return false
        if (recording.state != RecordingState.RECORDING) return false
        recording.state = RecordingState.PAUSED
        return true
    }

    fun resumeRecording(recordingId: String): Boolean {
        val recording = activeRecordings[recordingId] ?: return false
        if (recording.state != RecordingState.PAUSED) return false
        recording.state = RecordingState.RECORDING
        return true
    }

    fun getRecording(recordingId: String): Recording? =
        activeRecordings[recordingId] ?: completedRecordings[recordingId]

    fun isRecording(roomId: String): Boolean =
        roomRecordings[roomId]?.lastOrNull()?.let { activeRecordings[it]?.state == RecordingState.RECORDING } ?: false

    fun getActiveRecording(roomId: String): Recording? =
        roomRecordings[roomId]?.lastOrNull()?.let { activeRecordings[it] }

    // ── إدارة الحلقات ────────────────────────────────────────────────────────
    fun addSegment(
        recordingId: String,
        sequenceNumber: Int,
        startTime: Long,
        duration: Long,
        path: String,
        size: Long
    ): RecordingSegment? {
        val recording = activeRecordings[recordingId] ?: return null

        val segment = RecordingSegment(
            segmentId = "segment_${recordingId}_$sequenceNumber",
            recordingId = recordingId,
            sequenceNumber = sequenceNumber,
            startTime = startTime,
            duration = duration,
            path = path,
            size = size
        )

        recordingSegments[recordingId]?.add(segment)
        recording.segments += 1
        recording.fileSize += size

        log.debug("Recording segment added: {} (seq={}, size={})", recordingId, sequenceNumber, size)
        return segment
    }

    fun getSegments(recordingId: String): List<RecordingSegment> =
        recordingSegments[recordingId]?.toList() ?: emptyList()

    fun getSegmentCount(recordingId: String): Int =
        recordingSegments[recordingId]?.size ?: 0

    // ── إدارة الحالة والمعالجة ────────────────────────────────────────────
    fun updateRecordingState(recordingId: String, state: RecordingState): Boolean {
        val recording = activeRecordings[recordingId] ?: return false
        recording.state = state

        // إذا اكتملت، انقل إلى السجل المكتمل
        if (state == RecordingState.COMPLETED || state == RecordingState.FAILED) {
            completedRecordings[recordingId] = recording
            activeRecordings.remove(recordingId)
        }

        log.debug("Recording state updated: {} -> {}", recordingId, state)
        return true
    }

    fun setRecordingUrls(recordingId: String, hlsUrl: String?, vodUrl: String?): Boolean {
        val recording = getRecording(recordingId) ?: return false
        recording.hlsUrl = hlsUrl
        recording.vodUrl = vodUrl
        log.debug("Recording URLs set: {} (HLS={}, VOD={})", recordingId, hlsUrl != null, vodUrl != null)
        return true
    }

    fun setRecordingPath(recordingId: String, filePath: String): Boolean {
        val recording = getRecording(recordingId) ?: return false
        recording.filePath = filePath
        return true
    }

    fun addParticipant(recordingId: String, participantId: String): Boolean {
        val recording = getRecording(recordingId) ?: return false
        recording.participants = recording.participants + participantId
        return true
    }

    fun removeParticipant(recordingId: String, participantId: String): Boolean {
        val recording = getRecording(recordingId) ?: return false
        recording.participants = recording.participants - participantId
        return true
    }

    // ── استرجاع السجلات ────────────────────────────────────────────────────────
    fun getRoomRecordings(roomId: String, limit: Int = 20): List<Recording> {
        return roomRecordings[roomId]?.takeLast(limit)?.mapNotNull { recordingId ->
            completedRecordings[recordingId]
        } ?: emptyList()
    }

    fun getRecordingsByUser(userId: String, limit: Int = 20): List<Recording> {
        return completedRecordings.values
            .filter { it.initiatedBy == userId }
            .sortedByDescending { it.startedAt }
            .take(limit)
    }

    fun getRecordingStats(recordingId: String): Map<String, Any>? {
        val recording = getRecording(recordingId) ?: return null
        val duration = recording.duration.takeIf { it > 0 } ?:
            java.time.Duration.between(recording.startedAt, Instant.now()).seconds

        return mapOf(
            "recordingId" to recordingId,
            "type" to recording.type,
            "state" to recording.state,
            "startedAt" to recording.startedAt.toString(),
            "stoppedAt" to (recording.stoppedAt?.toString() ?: ""),
            "duration" to duration,
            "resolution" to "${recording.width}x${recording.height}",
            "fps" to recording.fps,
            "videoBitrateMbps" to recording.videoBitrateMbps,
            "segments" to recording.segments,
            "fileSize" to recording.fileSize,
            "participants" to recording.participants.size,
            "hlsUrl" to (recording.hlsUrl ?: ""),
            "vodUrl" to (recording.vodUrl ?: "")
        )
    }

    fun deleteRecording(recordingId: String): Boolean {
        if (activeRecordings.remove(recordingId) != null) {
            log.warn("Cannot delete active recording: {}", recordingId)
            return false
        }
        if (completedRecordings.remove(recordingId) != null) {
            recordingSegments.remove(recordingId)
            log.info("Recording deleted: {}", recordingId)
            return true
        }
        return false
    }

    fun closeRoom(roomId: String) {
        roomRecordings[roomId]?.forEach { recordingId ->
            val recording = activeRecordings[recordingId]
            if (recording?.state == RecordingState.RECORDING) {
                stopRecording(recordingId)
                updateRecordingState(recordingId, RecordingState.COMPLETED)
            }
        }
        roomRecordings.remove(roomId)
    }
}
