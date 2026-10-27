package com.red.sovereign.features.lan

import android.content.Context
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.webrtc.AudioTrack
import org.webrtc.IceCandidate
import org.webrtc.PeerConnection
import org.webrtc.SessionDescription
import org.webrtc.VideoTrack
import java.util.UUID

/**
 * P2-LAN â€” ظ…ط¯ظٹط± ظ…ظƒط§ظ„ظ…ط§طھ ظ†ظپط³ ط§ظ„ظˆط§ظٹ ظپط§ظٹ (ط¬ظ‡ط§ط²-ظ„ط¬ظ‡ط§ط²طŒ ط¨ظ„ط§ ط¥ظ†طھط±ظ†طھ ظˆط¨ظ„ط§ ط®ط§ط¯ظ…).
 *
 * ط§ظ„طھط¯ظپظ‚:
 * - ط§ظƒطھط´ط§ظپ NSD â†’ ظ‚ط§ط¦ظ…ط© ط£ظ‚ط±ط§ظ† â†’ ط§طھطµط§ظ„ TCP ظ…ط¨ط§ط´ط± (ط¹ط±ط¶ SDP) â†’ WebRTC P2P.
 * - ط§ظ„ظˆط³ط§ط¦ط· DTLS-SRTP. ط§ظ„ظ‡ظˆظٹط©: redId ظ…ط¹ظ„ظ† + ط´ط§ط±ط© طھط­ظ‚ظ‚ ظ…ظ† ط¬ظ‡ط§طھ ط§ظ„ط§طھطµط§ظ„.
 * - طھط¹ط§ط±ط¶ ط§ظ„ط¹ط±ظˆط¶ (glare): ط§ظ„ط£طµط؛ط± redId ظ…ظ‡ط°ط¨ (ظٹط·ط¨ظ‚ ط§ظ„ظˆط§ط±ط¯) â€” ظ†ظپط³ ظ‚ط§ط¹ط¯ط© Mesh.
 * - ظ…ظ‡ظ„ط© ط±ظ†ظٹظ† 30s ظˆط§ط±ط¯ / 45s طµط§ط¯ط± (ظ…ظˆط§ط¦ظ…ط© CallRingPolicy).
 *
 * ط­ط¯ظˆط¯ ظ…ط¹ظ„ظ†ط© ط¨طµط¯ظ‚:
 * - ظٹطھط·ظ„ط¨ ظ†ظپط³ ط§ظ„ط´ط¨ظƒط© (ظ†ظپط³ /24) ظˆط¹ط¯ظ… طھظپط¹ظٹظ„ ط¹ط²ظ„ ط§ظ„ط¹ظ…ظ„ط§ط، ظپظٹ ط§ظ„ط±ط§ظˆطھط±.
 * - ظ…ظƒط§ظ„ظ…ط© 1:1 طµظˆطھ/ظپظٹط¯ظٹظˆ ظپظ‚ط· ظپظٹ V1 (ط§ظ„ط¬ظ…ط§ط¹ظٹط© ط¹ط¨ط± SFU ط§ظ„ط®ط§ط¯ظ…).
 */
class LanCallManager(
    private val appContext: Context,
    private val myRedId: String,
    private val myName: String,
    /** redIds ط¬ظ‡ط§طھ ط§ظ„ط§طھطµط§ظ„ â€” ظ„ط´ط§ط±ط© ط§ظ„طھط­ظ‚ظ‚. */
    private val contactRedIds: () -> Set<String> = { emptySet() }
) {
    companion object {
        private const val TAG = "LanCallManager"
        const val RING_TIMEOUT_IN_MS = 30_000L
        const val RING_TIMEOUT_OUT_MS = 45_000L
    }

    enum class LanCallState {
        IDLE, CALLING, RINGING, IN_CALL, ENDED
    }

    data class IncomingLanCall(
        val callId: String,
        val from: LanPeer,
        val media: String
    )

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val link = LanLink(myRedId.trim().uppercase())
    private val signalCrypto = LanSignalCrypto(appContext)
    private var presence: LanPresence? = null
    private var rtc: LanRtcSession? = null
    private var ringJob: Job? = null

    private val _peers = MutableStateFlow<List<LanPeer>>(emptyList())
    val peers: StateFlow<List<LanPeer>> = _peers.asStateFlow()

    private val _state = MutableStateFlow(LanCallState.IDLE)
    val state: StateFlow<LanCallState> = _state.asStateFlow()

    private val _incoming = MutableStateFlow<IncomingLanCall?>(null)
    val incoming: StateFlow<IncomingLanCall?> = _incoming.asStateFlow()

    private val _remoteAudio = MutableStateFlow<AudioTrack?>(null)
    val remoteAudio: StateFlow<AudioTrack?> = _remoteAudio.asStateFlow()

    private val _remoteVideo = MutableStateFlow<VideoTrack?>(null)
    val remoteVideo: StateFlow<VideoTrack?> = _remoteVideo.asStateFlow()

    private val _lastError = MutableStateFlow<String?>(null)
    val lastError: StateFlow<String?> = _lastError.asStateFlow()

    /** ط§ظ„ظ…ظƒط§ظ„ظ…ط© ط§ظ„ظ†ط´ط·ط© (طµط§ط¯ط±ط©/ظˆط§ط±ط¯ط©): ط§ظ„ظ†ط¸ظٹط± + ط§ظ„ظ…ط¹ط±ظپ + ظ‡ظ„ ط£ظ†ط§ ط§ظ„ظ…طھطµظ„. */
    private var activePeer: LanPeer? = null
    private var activeCallId: String = ""
    private var iAmCaller = false
    private var haveLocalOffer = false

    var events: Events? = null

    interface Events {
        /** ط±ظ†ظٹظ† ظˆط§ط±ط¯ â€” طھط¹ط±ط¶ ط§ظ„ظˆط§ط¬ظ‡ط© ط­ظˆط§ط± ظ‚ط¨ظˆظ„/ط±ظپط¶. */
        fun onIncomingCall(call: IncomingLanCall)
        fun onCallConnected(peer: LanPeer)
        fun onCallEnded(peer: LanPeer?, reason: String)
    }

    /** ط¨ط¯ط، ط§ظ„ط§ظƒطھط´ط§ظپ ظˆط§ظ„ظ†ط´ط±. */
    fun startDiscovery() {
        val port = link.listen()
        if (port <= 0) {
            _lastError.value = "LAN_PORT_BUSY"
            return
        }
        link.listener = linkListener
        val presence = LanPresence(appContext, myRedId, myName, port).also { this.presence = it }
        scope.launch {
            presence.peersFlow.collect { found ->
                val contacts = runCatching { contactRedIds() }.getOrDefault(emptySet())
                _peers.value = found.filter {
                    it.redId != myRedId.trim().uppercase() &&
                        it.port > 0 &&
                        LanNet.isPrivateIpv4(it.host)
                }.map { p ->
                    if (p.redId in contacts) p.copy(verified = true) else p
                }
            }
        }
        presence.start()
    }

    fun stopDiscovery() {
        runCatching { presence?.stop() }
        presence = null
    }

    fun shutdown() {
        stopDiscovery()
        endCall(notify = false)
        link.stop()
    }

    /** ط§طھطµط§ظ„ طµط§ط¯ط± â€” ظٹط¹ظٹط¯ false ط¥ظ† ظƒظ†ط§ ظ…ط´ط؛ظˆظ„ظٹظ†. */
    fun call(peer: LanPeer, video: Boolean): Boolean {
        if (_state.value == LanCallState.CALLING ||
            _state.value == LanCallState.IN_CALL ||
            _state.value == LanCallState.RINGING
        ) return false
        activePeer = peer
        activeCallId = UUID.randomUUID().toString()
        iAmCaller = true
        haveLocalOffer = false
        ensureRtc(video)
        rtc?.attach()
        _state.value = LanCallState.CALLING
        armRingTimeout(RING_TIMEOUT_OUT_MS)
        // ظ†ظ†ط´ط¦ ط§ظ„ط¹ط±ط¶ ط£ظˆظ„ط§ط› ظٹظڈط±ط³ظ„ ط¹ظ†ط¯ ط§ظƒطھظ…ط§ظ„ ط§ظ„ظˆطµظپ ط§ظ„ظ…ط­ظ„ظٹ (onLocalDescription).
        rtc?.createOffer()
        return true
    }

    /** ظ‚ط¨ظˆظ„ ط§ظ„ظˆط§ط±ط¯. */
    fun accept(video: Boolean) {
        val inc = _incoming.value ?: return
        activePeer = inc.from
        activeCallId = inc.callId
        iAmCaller = false
        haveLocalOffer = false
        _incoming.value = null
        ensureRtc(video)
        rtc?.attach()
        _state.value = LanCallState.IN_CALL
        cancelRingTimeout()
        // ط§ظ„ط¥ط¬ط§ط¨ط© طھظڈط¨ظ†ظ‰ ط¨ط¹ط¯ طھط«ط¨ظٹطھ ط§ظ„ط¹ط±ط¶ ط§ظ„ظˆط§ط±ط¯ (ظ…ط®ط²ظ† ظ…ط¤ظ‚طھط§ ظپظٹ pendingOffer).
        pendingOffer?.let { sdp ->
            pendingOffer = null
            rtc?.handleOffer(sdp)
        }
    }

    /** ط±ظپط¶ ط§ظ„ظˆط§ط±ط¯. */
    fun decline() {
        val inc = _incoming.value ?: return
        _incoming.value = null
        pendingOffer = null
        link.send(inc.from, LanMsg(type = "BYE", from = myRedId, to = inc.from.redId, callId = inc.callId))
        resetCallState()
    }

    /** ط¥ظ†ظ‡ط§ط، ظ†ط´ط·ط©/طµط§ط¯ط±ط©. */
    fun endCall(notify: Boolean = true) {
        val peer = activePeer
        val callId = activeCallId
        if (notify && peer != null && callId.isNotBlank() &&
            (_state.value == LanCallState.CALLING || _state.value == LanCallState.IN_CALL)
        ) {
            link.send(peer, LanMsg(type = "BYE", from = myRedId, to = peer.redId, callId = callId))
        }
        resetCallState()
        if (peer != null) events?.onCallEnded(peer, "LOCAL_HANGUP")
    }

    fun setMicrophoneEnabled(enabled: Boolean) {
        rtc?.setMicrophoneEnabled(enabled)
    }

    fun setCameraEnabled(enabled: Boolean) {
        rtc?.setCameraEnabled(enabled)
    }

    fun switchCamera() {
        rtc?.switchCamera()
    }

    fun localVideo(): VideoTrack? = rtc?.localVideo

    /** ط³ظٹط§ظ‚ EGL ظ„ظ„ط¹ط±ط¶ ط§ظ„ظ…ط­ظ„ظٹ/ط§ظ„ط¨ط¹ظٹط¯ (SurfaceViewRenderer). */
    fun eglContext(): org.webrtc.EglBase.Context? = rtc?.eglContext

    /** ط¹ظ†ظˆط§ظ†ظ†ط§ ط§ظ„ظ…ط¹ظ„ظ† ط¯ط§ط®ظ„ LAN (ظٹظڈط±ط³ظ„ ط¶ظ…ظ† ط§ظ„ط¹ط±ط¶ ظ„ظ„ط±ط¯ ط§ظ„ظ…ط¨ط§ط´ط±). */
    private fun myAdvertisedHost(): String =
        runCatching { LanNet.currentWifiNet(appContext)?.ip }.getOrNull()
            ?: runCatching { LanNet.firstSiteLocalIpv4() }.getOrNull()
            .orEmpty()

    // â”€â”€ ط¯ط§ط®ظ„ظٹ â”€â”€

    private var pendingOffer: String? = null

    // â”€â”€ ط­ظ„ ط§ظ„طھط¹ط§ط±ط¶ (TCP Glare Resolution) â”€â”€
    // ط¥ط°ط§ ظ‚ط§ظ… ط´ط®طµط§ظ† ط¨ط§ظ„ط§طھطµط§ظ„ ط¨ط¨ط¹ط¶ظ‡ظ…ط§ ظپظٹ ظ†ظپط³ ط§ظ„ظ„ط­ط¸ط© (ظˆظ‡ظ…ط§ ط¹ظ„ظ‰ ظ†ظپط³ ط´ط¨ظƒط© LAN)طŒ
    // ط³طھطھظ‚ط§ط·ط¹ ط¥ط´ط§ط±ط§طھ ط§ظ„ط¹ط±ط¶. ط§ظ„ظ‚ط§ط¹ط¯ط©: طµط§ط­ط¨ ط§ظ„ظ…ط¹ط±ظپ (redId) ط§ظ„ط£طµط؛ط± ط£ط¨ط¬ط¯ظٹط§ظ‹ ظٹط³طھط³ظ„ظ… (ظٹطµط¨ط­ ظ…ط³طھظ‚ط¨ظ„ط§ظ‹)
    // ظˆط§ظ„ط¢ط®ط± ظٹظƒظ…ظ„ ظƒظ…طھطµظ„. ظ‡ط°ط§ ظٹظ…ظ†ط¹ ط³ظ‚ظˆط· ط§ظ„ظ…ظƒط§ظ„ظ…ط©.
    private fun shouldYield(remoteRedId: String): Boolean {
        return myRedId.trim().uppercase() > remoteRedId.trim().uppercase()
    }

    private fun ensureRtc(video: Boolean) {
        if (rtc == null) {
            rtc = LanRtcSession(appContext, rtcEvents).also { it.prepare(video) }
        }
    }

    private fun resetCallState() {
        cancelRingTimeout()
        runCatching { rtc?.release() }
        rtc = null
        activePeer = null
        activeCallId = ""
        pendingOffer = null
        haveLocalOffer = false
        _state.value = LanCallState.IDLE
    }

    private fun armRingTimeout(ms: Long) {
        cancelRingTimeout()
        val peer = activePeer
        val callId = activeCallId
        ringJob = scope.launch {
            delay(ms)
            if (activeCallId == callId && callId.isNotBlank()) {
                Log.i(TAG, "ring timeout call=$callId")
                if (peer != null && _state.value == LanCallState.CALLING) {
                    link.send(peer, LanMsg(type = "BYE", from = myRedId, to = peer.redId, callId = callId))
                }
                val endedPeer = activePeer
                resetCallState()
                _lastError.value = "LAN_NO_ANSWER"
                if (endedPeer != null) events?.onCallEnded(endedPeer, "TIMEOUT")
            }
        }
    }

    private fun cancelRingTimeout() {
        ringJob?.cancel()
        ringJob = null
    }

    private val linkListener = object : LanLink.Listener {
        override fun onFrame(msg: LanMsg) {
            if (msg.to.isNotBlank() && msg.to != myRedId.trim().uppercase()) return
            when (msg.type) {
                "OFFER" -> onRemoteOffer(msg)
                "ANSWER" -> onRemoteAnswer(msg)
                "ICE" -> onRemoteIce(msg)
                "BYE" -> onRemoteBye(msg)
                "PING" -> Unit
                "HELLO" -> Unit
                else -> Log.w(TAG, "unknown lan type ${msg.type}")
            }
        }

        override fun onSendError(to: LanPeer, error: LanLink.LanLinkError) {
            _lastError.value = when (error) {
                LanLink.LanLinkError.UNREACHABLE -> "LAN_ISOLATED"
                LanLink.LanLinkError.TIMEOUT -> "LAN_TIMEOUT"
                LanLink.LanLinkError.IO -> "LAN_IO"
            }
            // ظپط´ظ„ ط¥ط±ط³ط§ظ„ ط§ظ„ط¹ط±ط¶ ط§ظ„طµط§ط¯ط± = ط¥ظ†ظ‡ط§ط، ظپظˆط±ظٹ (ظ„ط§ ط±ظ†ظٹظ† ظˆظ‡ظ…ظٹ)
            if (_state.value == LanCallState.CALLING && to.redId == activePeer?.redId) {
                val p = activePeer
                resetCallState()
                if (p != null) events?.onCallEnded(p, "UNREACHABLE")
            }
        }
    }

    private fun onRemoteOffer(msg: LanMsg) {
        if (msg.callId.isBlank()) return
        val sdp = unsealSdp(msg)
        if (sdp.isNullOrBlank()) {
            Log.w(TAG, "offer sealed-unreadable from ${msg.from} â€” ignored")
            return
        }
        val peer = findOrBuildPeer(msg) ?: return
        when (_state.value) {
            LanCallState.IDLE -> {
                activePeer = peer
                activeCallId = msg.callId
                iAmCaller = false
                pendingOffer = sdp
                // P0 2026-09-27: local snapshot instead of !! on shared flow value.
                val incoming = IncomingLanCall(msg.callId, peer, msg.media.ifBlank { "voice" })
                _incoming.value = incoming
                _state.value = LanCallState.RINGING
                armRingTimeout(RING_TIMEOUT_IN_MS)
                events?.onIncomingCall(incoming)
            }
            LanCallState.CALLING -> {
                // ط­ظ„ طھط¹ط§ط±ط¶ TCP (Glare Resolution)
                if (shouldYield(peer.redId)) {
                    Log.i(TAG, "glare: yielding to ${peer.redId}")
                    haveLocalOffer = false
                    pendingOffer = sdp
                    // P0 2026-09-27: local snapshot instead of !! on shared flow value.
                    val incomingGlare = IncomingLanCall(msg.callId, peer, msg.media.ifBlank { "voice" })
                    _incoming.value = incomingGlare
                    activePeer = peer
                    activeCallId = msg.callId
                    iAmCaller = false
                    ensureRtc(msg.media == "video")
                    _state.value = LanCallState.RINGING
                    armRingTimeout(RING_TIMEOUT_IN_MS)
                    events?.onIncomingCall(incomingGlare)
                } else {
                    Log.i(TAG, "glare: ignoring offer from ${peer.redId}, local wins")
                }
            }
            LanCallState.RINGING, LanCallState.IN_CALL -> {
                if (msg.callId != activeCallId) {
                    // ظ…ط´ط؛ظˆظ„: ظ†ط±ط³ظ„ BYE ظ…ط¨ط§ط´ط±ط© ظ„ظ„ط·ط±ظپ ط§ظ„ط¢ط®ط± ظ„ظٹط¹ط±ظپ ط£ظ†ظ†ط§ ظ…ط´ط؛ظˆظ„ظˆظ† ط¨ط¯ظ„ط§ ظ…ظ† طھط¹ظ„ظٹظ‚ظ‡
                    link.send(peer, LanMsg("BYE", myRedId, peer.redId, msg.callId))
                }
            }
            else -> Unit
        }
    }

    private fun onRemoteAnswer(msg: LanMsg) {
        if (msg.callId != activeCallId) return
        val sdp = unsealSdp(msg)
        if (sdp.isNullOrBlank()) return
        if (_state.value != LanCallState.CALLING) return
        cancelRingTimeout()
        rtc?.handleAnswer(sdp)
        _state.value = LanCallState.IN_CALL
        activePeer?.let { events?.onCallConnected(it) }
    }

    private fun onRemoteIce(msg: LanMsg) {
        if (msg.callId != activeCallId) return
        val ice = msg.ice ?: return
        rtc?.handleIce(IceCandidate(ice.sdpMid, ice.sdpMLineIndex, ice.sdp))
    }

    private fun onRemoteBye(msg: LanMsg) {
        if (msg.callId != activeCallId && msg.callId != _incoming.value?.callId) return
        val peer = activePeer ?: _incoming.value?.from
        _incoming.value = null
        resetCallState()
        _state.value = LanCallState.IDLE
        if (peer != null) events?.onCallEnded(peer, "REMOTE_BYE")
    }

    /** ط®طھظ… SDP ط¨ط¬ظ„ط³ط© Signal ط§ظ„ظ‚ط§ط¦ظ…ط© (fallback ط¹ظ„ظ†ظٹ ظ…ظˆط«ظ‚ ط¹ظ†ط¯ ط؛ظٹط§ط¨ظ‡ط§). */
    private fun withSeal(peer: LanPeer, base: LanMsg, sdp: String): LanMsg {
        val sealed = runCatching {
            signalCrypto.seal(peer.redId, sdp.toByteArray(Charsets.UTF_8))
        }.getOrNull() ?: return base
        return base.copy(
            sdp = "",
            sealed = true,
            enc = java.util.Base64.getEncoder().encodeToString(sealed.bytes),
            encType = sealed.type,
            encDevice = sealed.deviceId
        )
    }

    /** ظپطھط­ SDP ط§ظ„ظ…ط®طھظˆظ…طŒ ط£ظˆ ط§ظ„ظ†طµ ط§ظ„ظ…ط¨ط§ط´ط± â€” null = طھط¬ط§ظ‡ظ„ ط¢ظ…ظ†. */
    private fun unsealSdp(msg: LanMsg): String? {
        if (!msg.sealed) return msg.sdp.takeIf { it.isNotBlank() }
        if (msg.enc.isBlank()) return null
        val bytes = runCatching { java.util.Base64.getDecoder().decode(msg.enc) }.getOrNull()
            ?: return null
        return runCatching { signalCrypto.open(msg.from, msg.encDevice, msg.encType, bytes) }
            .getOrNull()?.toString(Charsets.UTF_8)?.takeIf { it.isNotBlank() }
    }

    private fun findOrBuildPeer(msg: LanMsg): LanPeer? {
        _peers.value.firstOrNull { it.redId == msg.from }?.let { return it }
        // ط§ط­طھظٹط§ط·ظٹ: ط§ظ„ط¹ط±ط¶ ظٹط­ظ…ظ„ ط¹ظ†ظˆط§ظ† ط§ظ„ظ…ط±ط³ظ„ â€” ظ†ط¨ظ†ظٹ ظ†ط¸ظٹط±ط§ ظ…ط¤ظ‚طھط§ (ظٹظڈط³طھط¨ط¯ظ„ ط¹ظ†ط¯ ط§ظƒطھط´ط§ظپ NSD).
        // ظ†طھط­ظ‚ظ‚ ظ…ظ† ط§ظ„ط´ط±ط·ظٹظ† ط§ظ„ط£ظ…ظ†ظٹظٹظ†: redId طµط§ظ„ط­ + IP ط®ط§طµ (ظ„ط§ ط¥ظ†طھط±ظ†طھ).
        if (msg.host.isBlank() || msg.port <= 0) {
            Log.w(TAG, "offer from undiscovered peer ${msg.from} without route â€” ignored")
            return null
        }
        if (!LanNet.isPrivateIpv4(msg.host)) {
            Log.w(TAG, "offer from non-private host ${msg.host} â€” rejected")
            return null
        }
        val contacts = runCatching { contactRedIds() }.getOrDefault(emptySet())
        return LanPeer(
            redId = msg.from,
            name = msg.name.take(32).ifBlank { msg.from.take(8) },
            host = msg.host,
            port = msg.port,
            verified = msg.from in contacts
        )
    }

    private val rtcEvents = object : LanRtcSession.Events {
        override fun onLocalDescription(description: SessionDescription) {
            val peer = activePeer ?: return
            val callId = activeCallId
            if (callId.isBlank()) return
            when (description.type) {
                SessionDescription.Type.OFFER -> {
                    haveLocalOffer = true
                    link.send(
                        peer,
                        withSeal(
                            peer,
                            LanMsg(
                                type = "OFFER", from = myRedId, to = peer.redId,
                                callId = callId, sdp = description.description,
                                media = if (rtc?.localVideo != null) "video" else "voice",
                                name = myName,
                                host = myAdvertisedHost(),
                                port = link.actualPort()
                            ),
                            description.description
                        )
                    )
                }
                SessionDescription.Type.ANSWER -> {
                    link.send(
                        peer,
                        withSeal(
                            peer,
                            LanMsg(
                                type = "ANSWER", from = myRedId, to = peer.redId,
                                callId = callId, sdp = description.description
                            ),
                            description.description
                        )
                    )
                }
                else -> Unit
            }
        }

        override fun onIceCandidate(candidate: IceCandidate) {
            val peer = activePeer ?: return
            val callId = activeCallId
            if (callId.isBlank()) return
            link.send(
                peer,
                LanMsg(
                    type = "ICE", from = myRedId, to = peer.redId, callId = callId,
                    ice = LanIce(candidate.sdpMid, candidate.sdpMLineIndex, candidate.sdp)
                )
            )
        }

        override fun onRemoteAudio(track: AudioTrack) {
            _remoteAudio.value = track
        }

        override fun onRemoteVideo(track: VideoTrack) {
            _remoteVideo.value = track
        }

        override fun onConnectionState(state: PeerConnection.PeerConnectionState) {
            if (state == PeerConnection.PeerConnectionState.CONNECTED) {
                cancelRingTimeout()
                if (_state.value == LanCallState.CALLING) {
                    _state.value = LanCallState.IN_CALL
                    activePeer?.let { events?.onCallConnected(it) }
                }
            }
            if (state == PeerConnection.PeerConnectionState.FAILED ||
                state == PeerConnection.PeerConnectionState.DISCONNECTED
            ) {
                val peer = activePeer
                resetCallState()
                _lastError.value = "LAN_PC_FAILED"
                if (peer != null) events?.onCallEnded(peer, state.name)
            }
            if (state == PeerConnection.PeerConnectionState.CLOSED) {
                if (_state.value == LanCallState.IN_CALL) {
                    val peer = activePeer
                    resetCallState()
                    if (peer != null) events?.onCallEnded(peer, "CLOSED")
                }
            }
        }

        override fun onError(message: String) {
            Log.w(TAG, "rtc error: $message")
            _lastError.value = message
        }
    }
}
