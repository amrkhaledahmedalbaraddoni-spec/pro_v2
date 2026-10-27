package com.red.sovereign.calls

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CallEnd
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import kotlin.math.roundToInt
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.lifecycleScope
import com.red.sovereign.auth.TokenStore
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * ط´ط§ط´ط© ظˆط§ط±ط¯ط© ظƒط§ظ…ظ„ط© ظ„ظƒظ„ ط£ظ†ظˆط§ط¹ ط§ظ„ظ…ظƒط§ظ„ظ…ط§طھ â€” ظ…ظ† ط´ط§ط´ط© ط§ظ„ظ‚ظپظ„.
 */
class IncomingCallActivity : ComponentActivity() {
    private val viewModel: IncomingCallViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // ط§ظ„ط­ط¯ظٹط«ط© (API 27+): setShowWhenLocked/setTurnScreenOn â€” ظ…ط¹ ط¥ط¨ظ‚ط§ط، ط§ظ„ط£ط¹ظ„ط§ظ… ظ„ظ„ظ‚ط¯ظٹظ….
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        }
        window.addFlags(
            android.view.WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                android.view.WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
                android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or
                android.view.WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD
        )
        bindIntent(intent)
        // ط§ظ„ط´ط§ط´ط© ط§ظ„ظƒط§ظ…ظ„ط© ظ„ظ„ط®ظ„ظپظٹط© ظپظ‚ط·: ط§ظ„ظ…ظ‚ط¯ظ…ط© طھظڈط؛ط·ظٹظ‡ط§ overlays ط§ظ„ظ…ظˆط­ط¯ط© + ط§ظ„ط¥ط´ط¹ط§ط± â€”
        // ط¥ط·ظ„ط§ظ‚ظ‡ط§ ظپظˆظ‚ MainActivity ظٹظƒط¯ظ‘ط³ ط´ط§ط´طھظٹظ† ظ„ظ†ظپط³ ط§ظ„ط±ظ†ظٹظ†. طھظڈط¹ط±ط¶ ظپظ‚ط· ط¹ظ†ط¯ ظ…ظ‡ظ…ط©
        // ظ…ظٹطھط© (isTaskRoot) ط£ظˆ ظپظˆظ‚ ط§ظ„ظ‚ظپظ„ط› ظˆط¥ظ„ط§ ظ†ظڈط؛ظ„ظ‚ ظˆظ†طھط±ظƒ ط§ظ„ظ€ overlay.
        if (!shouldShowFullScreen()) {
            finish()
            return
        }
        setContent {
            IncomingCallScreen(
                viewModel = viewModel,
                onFinish = { finishToMainIfRoot() },
                // إغفال غير اختياري (المتصل ألغى أو انتهت المكالمة): نُغلق فقط ولا نسرق
                // التركيز بإدخال المستخدم إلى التطبيق — كان finishToMainIfRoot يفتح
                // MainActivity إذا كانت هذه الشاشة جذر المهمة (إيقاظ من الدفع).
                onDismiss = { finish() }
            )
        }
        lifecycleScope.launch {
            delay(CallRingPolicy.UNANSWERED_TIMEOUT_MS)
            if (!viewModel.isHandled) {
                viewModel.decline()
                finishToMainIfRoot()
            }
        }
    }

    /** LEGENDARY FIX P0-5: ط¨ط¹ط¯ END ظƒط§ظ† ظٹط®ط±ط¬ ظ„ظ„ظ€ Launcher (ط§ظ„ظ…ط³طھط®ط¯ظ… ظٹط¸ظ† ط§ظ„ط§ظ†ظ‡ظٹط§ط±) â€” ط¹ظˆط¯ط© ط¯ظ‚ظٹظ‚ط© ظ„ظ€ Main ط¹ظ†ط¯ ط¬ط°ط± ط§ظ„ظ…ظ‡ظ…ط© */
    private fun finishToMainIfRoot() {
        if (isTaskRoot) {
            runCatching {
                startActivity(Intent(this, com.red.sovereign.MainActivity::class.java).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                })
            }
        }
        finish()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        bindIntent(intent)
        // ظ†ظپط³ ظ‚ط§ط¹ط¯ط© ط§ظ„ط®ظ„ظپظٹط©-ظپظ‚ط· ط¹ظ†ط¯ ظˆطµظˆظ„ ط±ظ†ظٹظ† ط«ط§ظ†ظچ ظپظˆظ‚ ظ†ط´ط§ط· ظ‚ط§ط¦ظ….
        if (!shouldShowFullScreen()) {
            finish()
            return
        }
    }

    /** القبول الدائم للعرض الفوري: تضمن انبثاق شاشة المكالمة الواردة دائماً سواء كانت الشاشة مقفلة أم مفتوحة. */
    private fun shouldShowFullScreen(): Boolean {
        return true
    }

    private fun bindIntent(intent: Intent?) {
        val callType = intent?.getStringExtra(EXTRA_CALL_TYPE) ?: CALL_TYPE_CONFERENCE
        viewModel.callType = callType
        when (callType) {
            CALL_TYPE_1TO1 -> {
                viewModel.callId = intent?.getStringExtra(EXTRA_CALL_ID).orEmpty()
                viewModel.peer = intent?.getStringExtra(EXTRA_PEER).orEmpty()
                viewModel.mode = intent?.getStringExtra(EXTRA_MODE) ?: "VOICE"
                viewModel.inviter = intent?.getStringExtra(EXTRA_INVITER).orEmpty().ifBlank { viewModel.peer }
            }
            CALL_TYPE_GROUP -> {
                viewModel.groupCallId = intent?.getStringExtra(GroupCallService.EXTRA_GROUP_CALL_ID).orEmpty()
                viewModel.myUserId = intent?.getStringExtra(GroupCallService.EXTRA_MY_USER_ID).orEmpty()
                viewModel.peer = intent?.getStringExtra(GroupCallService.EXTRA_HOST_NAME).orEmpty()
                viewModel.mode = if (intent?.getBooleanExtra(GroupCallService.EXTRA_IS_VIDEO, false) == true) "VIDEO" else "VOICE"
                viewModel.inviter = viewModel.peer
            }
            CALL_TYPE_LIVESTREAM -> {
                viewModel.streamId = intent?.getStringExtra(LiveStreamService.EXTRA_STREAM_ID).orEmpty()
                viewModel.userId = intent?.getStringExtra(LiveStreamService.EXTRA_USER_ID).orEmpty()
                viewModel.peer = intent?.getStringExtra(LiveStreamService.EXTRA_BROADCASTER_NAME).orEmpty()
                viewModel.mode = "VIDEO"
                viewModel.inviter = viewModel.peer
            }
            CALL_TYPE_ZOOM -> {
                viewModel.meetingId = intent?.getStringExtra(ZoomGroupCallService.EXTRA_MEETING_ID).orEmpty()
                viewModel.myUserId = intent?.getStringExtra(ZoomGroupCallService.EXTRA_MY_USER_ID).orEmpty()
                viewModel.peer = intent?.getStringExtra(ZoomGroupCallService.EXTRA_HOST_NAME).orEmpty()
                viewModel.zoomHostId = intent?.getStringExtra(ZoomGroupCallService.EXTRA_HOST_ID).orEmpty()
                viewModel.mode = if (intent?.getBooleanExtra(ZoomGroupCallService.EXTRA_IS_VIDEO, false) == true) "VIDEO" else "VOICE"
                viewModel.inviter = viewModel.peer
            }
            else -> {
                viewModel.roomId = intent?.getStringExtra(ConferenceService.EXTRA_ROOM_ID).orEmpty()
                viewModel.userId = intent?.getStringExtra(ConferenceService.EXTRA_USER_ID).orEmpty()
                viewModel.inviter = intent?.getStringExtra(ConferenceService.EXTRA_INVITER).orEmpty()
                viewModel.video = intent?.getBooleanExtra(ConferenceService.EXTRA_VIDEO, false) ?: false
                viewModel.mode = if (viewModel.video) "VIDEO" else "VOICE"
            }
        }
    }

    companion object {
        const val EXTRA_CALL_TYPE = "call_type"
        const val CALL_TYPE_1TO1 = "1to1"
        const val CALL_TYPE_GROUP = "group"
        const val CALL_TYPE_CONFERENCE = "conference"
        const val CALL_TYPE_LIVESTREAM = "livestream"
        const val CALL_TYPE_ZOOM = "zoom"
        const val EXTRA_CALL_ID = "call_id"
        const val EXTRA_PEER = "peer"
        const val EXTRA_MODE = "mode"
        const val EXTRA_INVITER = "inviter"

        fun launch1to1(context: Context, callId: String, peer: String, mode: String, inviter: String = peer) {
            context.startActivity(
                Intent(context, IncomingCallActivity::class.java).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                    putExtra(EXTRA_CALL_TYPE, CALL_TYPE_1TO1)
                    putExtra(EXTRA_CALL_ID, callId)
                    putExtra(EXTRA_PEER, peer)
                    putExtra(EXTRA_MODE, mode)
                    putExtra(EXTRA_INVITER, inviter)
                }
            )
        }

        fun launchGroup(context: Context, groupCallId: String, myUserId: String, hostName: String, isVideo: Boolean) {
            context.startActivity(
                Intent(context, IncomingCallActivity::class.java).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                    putExtra(EXTRA_CALL_TYPE, CALL_TYPE_GROUP)
                    putExtra(GroupCallService.EXTRA_GROUP_CALL_ID, groupCallId)
                    putExtra(GroupCallService.EXTRA_MY_USER_ID, myUserId)
                    putExtra(GroupCallService.EXTRA_HOST_NAME, hostName)
                    putExtra(GroupCallService.EXTRA_IS_VIDEO, isVideo)
                }
            )
        }

        fun launchConference(context: Context, roomId: String, userId: String, inviter: String, video: Boolean) {
            context.startActivity(
                Intent(context, IncomingCallActivity::class.java).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                    putExtra(EXTRA_CALL_TYPE, CALL_TYPE_CONFERENCE)
                    putExtra(ConferenceService.EXTRA_ROOM_ID, roomId)
                    putExtra(ConferenceService.EXTRA_USER_ID, userId)
                    putExtra(ConferenceService.EXTRA_INVITER, inviter)
                    putExtra(ConferenceService.EXTRA_VIDEO, video)
                }
            )
        }

        fun launchLive(context: Context, streamId: String, userId: String, broadcasterName: String) {
            context.startActivity(
                Intent(context, IncomingCallActivity::class.java).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                    putExtra(EXTRA_CALL_TYPE, CALL_TYPE_LIVESTREAM)
                    putExtra(LiveStreamService.EXTRA_STREAM_ID, streamId)
                    putExtra(LiveStreamService.EXTRA_USER_ID, userId)
                    putExtra(LiveStreamService.EXTRA_BROADCASTER_NAME, broadcasterName)
                }
            )
        }
    }
}

class IncomingCallViewModel(application: android.app.Application) : AndroidViewModel(application) {
    var callType = IncomingCallActivity.CALL_TYPE_CONFERENCE
    var inviter = ""
    var mode = "VOICE"
    var isHandled = false
    var callId = ""
    var peer = ""
    var groupCallId = ""
    var myUserId = ""
    var roomId = ""
    var userId = ""
    var video = false
    var streamId = ""
    var meetingId = ""
    var zoomHostId = ""
    fun accept(withVideo: Boolean) {
        isHandled = true
        val app = getApplication<android.app.Application>()
        when (callType) {
            IncomingCallActivity.CALL_TYPE_1TO1 -> {
                YounesCallService.accept(app, cameraOn = withVideo && mode == "VIDEO", micOn = true, isVideo = withVideo)
            }
            IncomingCallActivity.CALL_TYPE_GROUP -> {
                val uid = myUserId.ifBlank { TokenStore(app).redId.orEmpty() }
                GroupCallService.accept(app, groupCallId, uid, mode == "VIDEO" || withVideo)
            }
            IncomingCallActivity.CALL_TYPE_LIVESTREAM -> {
                val uid = userId.ifBlank { TokenStore(app).redId.orEmpty() }
                LiveStreamService.start(app, streamId, uid, isBroadcaster = false)
            }
            IncomingCallActivity.CALL_TYPE_ZOOM -> {
                val uid = myUserId.ifBlank { TokenStore(app).redId.orEmpty() }
                ZoomGroupCallService.accept(app, meetingId, uid, mode == "VIDEO" || withVideo, zoomHostId)
            }
            else -> {
                val uid = userId.ifBlank { TokenStore(app).redId.orEmpty() }
                ConferenceService.join(app, roomId, uid, video = withVideo && (video || mode == "VIDEO"), asHost = false)
            }
        }
    }

    fun decline() {
        isHandled = true
        val app = getApplication<android.app.Application>()
        when (callType) {
            IncomingCallActivity.CALL_TYPE_1TO1 -> YounesCallService.action(app, YounesCallService.ACTION_REJECT)
            IncomingCallActivity.CALL_TYPE_GROUP -> GroupCallService.decline(app, groupCallId)
            IncomingCallActivity.CALL_TYPE_LIVESTREAM -> LiveStreamService.stop(app)
            IncomingCallActivity.CALL_TYPE_ZOOM -> ZoomGroupCallService.decline(app, meetingId)
            else -> ConferenceService.leave(app)
        }
    }
}

@Composable
fun IncomingCallScreen(
    viewModel: IncomingCallViewModel,
    onFinish: () -> Unit,
    onDismiss: () -> Unit = onFinish
) {
    // G3: ظ…ط¹ط±ظپ ط§ظ„ظ…طھطµظ„ ط§ظ„ظƒط§ظ…ظ„ â€” ط§ظ„ط§ط³ظ… + Red ID ط¸ط§ظ‡ط± (ظƒط§ظ† ظ…ظ‚طھط·ط¹ط§ظ‹ take(12) ط¨ظ„ط§ Red ID).
    val inviterRaw = viewModel.inviter.ifBlank { viewModel.peer }
    val inviterName = remember(inviterRaw) { inviterRaw.take(24).ifBlank { "ظ…ط¬ظ‡ظˆظ„" } }
    val inviterRedId = remember(inviterRaw, viewModel.peer) {
        // ط¥ظ† ظƒط§ظ† ط§ظ„ظ…ط¹ط±ظˆط¶ ط§ط³ظ…ط§ظ‹طŒ ط§ط¹ط±ط¶ peer ظƒظ€ Red IDط› ظˆط¥ظ„ط§ ط§ط¹ط±ط¶ ط§ظ„ط®ط§ظ… ظ†ظپط³ظ‡.
        when {
            inviterRaw.isBlank() -> ""
            viewModel.peer.isNotBlank() && viewModel.peer != inviterRaw -> viewModel.peer
            else -> inviterRaw
        }
    }
    var showVideoToggle by remember { mutableStateOf(viewModel.mode == "VIDEO" || viewModel.video) }
    val isVideoCapable = viewModel.mode == "VIDEO" || viewModel.video ||
        viewModel.callType == IncomingCallActivity.CALL_TYPE_LIVESTREAM

    // P0 2026-09-27: شاشة الوارد تتبع مالك الحالة (CallRuntime) بدل مؤقتها الخاص وحده.
    // إن ألغى المتصل أو انتهت المكالمة من أي مسار آخر أو رُدّت من مكان آخر (إشعار/سماعة)،
    // تُغلق الشاشة فوراً بدل بقائها رنة ميتة والشاشة تعلن مكالمة غير موجودة.
    val callState = CallRuntime.state
    LaunchedEffect(viewModel.callType, callState) {
        if (viewModel.callType != IncomingCallActivity.CALL_TYPE_1TO1) return@LaunchedEffect
        if (viewModel.isHandled) return@LaunchedEffect
        if (IncomingCallUiPolicy.shouldDismissRingScreen(callState, viewModel.callId)) onDismiss()
    }

    Box(
        modifier = Modifier.fillMaxSize().background(
            Brush.verticalGradient(
                colors = listOf(Color(0xFF0A1128), Color(0xFF03070C)),
                startY = 0f,
                endY = 2000f
            )
        )
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.SpaceBetween,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(start = 24.dp, end = 24.dp, top = 80.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = when (viewModel.callType) {
                        IncomingCallActivity.CALL_TYPE_1TO1 -> "ظ…ظƒط§ظ„ظ…ط© ظˆط§ط±ط¯ط©"
                        IncomingCallActivity.CALL_TYPE_GROUP -> "ظ…ظƒط§ظ„ظ…ط© ط¬ظ…ط§ط¹ظٹط© ظˆط§ط±ط¯ط©"
                        IncomingCallActivity.CALL_TYPE_LIVESTREAM -> "ط¨ط« ظ…ط¨ط§ط´ط±"
                        else -> "ط¯ط¹ظˆط© ظ…ط¤طھظ…ط± / ظ…ط³ط§ط­ط©"
                    },
                    color = Color.White.copy(0.6f),
                    fontSize = 14.sp
                )
                Spacer(Modifier.height(8.dp))
                Text(inviterName, color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.Bold)
                // G3: Red ID ط§ظ„ظƒط§ظ…ظ„ طھط­طھ ط§ظ„ط§ط³ظ… â€” ظ…ط¹ط±ظپ ط§ظ„ظ…طھطµظ„ ط­ظٹط« ظƒط§ظ† ظ†ط§ظ‚طµط§ظ‹.
                if (inviterRedId.isNotBlank() && inviterRedId != inviterName) {
                    Spacer(Modifier.height(2.dp))
                    Text(inviterRedId, color = Color.White.copy(0.55f), fontSize = 13.sp)
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    text = if (isVideoCapable) "ظپظٹط¯ظٹظˆ" else "طµظˆطھ",
                    color = Color(0xFF00C98C),
                    fontSize = 16.sp
                )
            }

            Box(modifier = Modifier.size(140.dp), contentAlignment = Alignment.Center) {
                val pulse by rememberInfiniteTransition(label = "pulse").animateFloat(
                    initialValue = 1f,
                    targetValue = 1.2f,
                    animationSpec = infiniteRepeatable(tween(1000, easing = FastOutSlowInEasing), RepeatMode.Reverse),
                    label = "pulseScale"
                )
                Box(Modifier.size(140.dp).scale(pulse).clip(CircleShape).background(Color(0x3300C98C)))
                Box(
                    modifier = Modifier
                        .size(140.dp)
                        .clip(CircleShape)
                        .background(Brush.radialGradient(listOf(Color(0xFF00C98C), Color(0xFF003023)))),
                    contentAlignment = Alignment.Center
                ) {
                    Text(inviterName.take(1).uppercase(), color = Color.White, fontSize = 56.sp, fontWeight = FontWeight.Bold)
                }
            }

            Column(
                modifier = Modifier.fillMaxWidth().padding(start = 24.dp, end = 24.dp, bottom = 60.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                if (isVideoCapable) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("ظپظٹط¯ظٹظˆ", color = Color.White.copy(0.7f), fontSize = 14.sp)
                        Switch(
                            checked = showVideoToggle,
                            onCheckedChange = { showVideoToggle = it },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color(0xFF00C98C),
                                checkedTrackColor = Color(0xFF00C98C).copy(0.4f)
                            )
                        )
                    }
                }
            // â”€â”€ ط³ظ„ط§ظٹط¯ط± ط§ظ„ط³ط­ط¨ ط§ظ„ط³ط±ظٹط¹ (ط§ظ„ط±ط¯) â”€â”€
            var dragOffset by remember { mutableStateOf(0f) }
            val density = LocalDensity.current
            val maxDrag = with(density) { 200.dp.toPx() } // Slide distance

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 60.dp, start = 32.dp, end = 32.dp)
                    .height(72.dp)
                    .clip(RoundedCornerShape(36.dp))
                    .background(Color(0xFF1E2633).copy(alpha = 0.5f)),
                contentAlignment = Alignment.CenterStart
            ) {
                // Background Text
                Text(
                    text = "ط§ط³ط­ط¨ ظ„ظ„ط±ط¯",
                    color = Color.White.copy(alpha = 0.5f),
                    modifier = Modifier.align(Alignment.Center)
                )

                // Drag Button
                Box(
                    modifier = Modifier
                        .offset { IntOffset(dragOffset.roundToInt(), 0) }
                        .size(72.dp)
                        .clip(CircleShape)
                        .background(
                            Brush.linearGradient(
                                listOf(Color(0xFF00E676), Color(0xFF00C853))
                            )
                        )
                        .draggable(
                            orientation = Orientation.Horizontal,
                            state = rememberDraggableState { delta ->
                                dragOffset = (dragOffset + delta).coerceIn(0f, maxDrag)
                            },
                            onDragStopped = {
                                if (dragOffset > maxDrag * 0.7f) {
                                    // Accepted
                                    viewModel.accept(withVideo = showVideoToggle && isVideoCapable)
                                    onFinish()
                                } else {
                                    // Snap back
                                    dragOffset = 0f
                                }
                            }
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (showVideoToggle && isVideoCapable) Icons.Default.Videocam else Icons.Default.Call,
                        contentDescription = "Accept",
                        tint = Color.White,
                        modifier = Modifier.size(32.dp)
                    )
                }
            }

            // Decline Button Underneath
            IconButton(
                onClick = { viewModel.decline(); onFinish() },
                modifier = Modifier
                    .padding(bottom = 32.dp)
                    .size(64.dp)
                    .background(Color(0xFFE53935), CircleShape)
            ) {
                Icon(Icons.Default.CallEnd, "Reject", tint = Color.White, modifier = Modifier.size(28.dp))
            }
        }
    }
}
}

