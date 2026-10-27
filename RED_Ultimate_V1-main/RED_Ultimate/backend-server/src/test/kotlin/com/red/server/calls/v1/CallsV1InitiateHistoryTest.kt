package com.red.server.calls.v1

import com.red.server.auth.model.UserAccount
import com.red.server.auth.repository.UserAccountRepository
import com.red.server.calls.CallHistoryDocument
import com.red.server.calls.CallHistoryService
import com.red.server.calls.CallRoute
import com.red.server.calls.CallStatus
import com.red.server.calls.CallType
import com.red.server.calls.LiveStreamService
import com.red.server.calls.ScheduledCallController
import com.red.server.services.NotificationService
import com.red.server.websocket.CallWebSocketHandler
import com.red.server.websocket.ConferenceWebSocketHandler
import com.red.server.websocket.LiveStreamWebSocketHandler
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.http.HttpStatus
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import java.time.Instant
import java.util.Optional
import java.util.UUID

/**
 * يغطي إصلاح بدء v1: العرض يُسجَّل في السجل الدائم (RINGING) حتى
 * يُسجَّل MISSED عند انتهاء 45s ويظهر في history — كان غائباً تماماً.
 */
class CallsV1InitiateHistoryTest {
    private val callSignaling: CallWebSocketHandler = mock()
    private val conferenceSignaling: ConferenceWebSocketHandler = mock()
    private val liveStreamService: LiveStreamService = mock()
    private val liveStreamSignaling: LiveStreamWebSocketHandler = mock()
    private val callHistory: CallHistoryService = mock()
    private val users: UserAccountRepository = mock()
    private val notifications: NotificationService = mock()
    private val scheduledCallController: ScheduledCallController = mock()
    private val db: JdbcTemplate = mock()

    private val controller = CallsV1Controller(
        callSignaling, conferenceSignaling, liveStreamService, liveStreamSignaling,
        callHistory, users, notifications, scheduledCallController, db
    )

    @Test
    fun `initiate records call history for expiry and missed`() {
        val callerId = UUID.randomUUID()
        val caller = UserAccount(id = callerId, redId = "caller-1", username = "caller")
        val callee = UserAccount(id = UUID.randomUUID(), redId = "callee-1", username = "callee")
        whenever(users.findById(callerId)).thenReturn(Optional.of(caller))
        whenever(users.findByRedId("callee-1")).thenReturn(callee)
        whenever(
            callHistory.start(
                eq("caller-1"), eq("callee-1"), eq("callee-1"),
                eq(CallType.VIDEO_1V1), eq(CallRoute.RED), anyOrNull()
            )
        ).thenReturn(
            CallHistoryDocument(
                id = "call-v1", initiatorId = "caller-1", targetId = "callee-1",
                targetLabel = "callee-1", type = CallType.VIDEO_1V1,
                route = CallRoute.RED, status = CallStatus.RINGING, startedAt = Instant.now()
            )
        )

        val response = controller.initiate(
            InitiateCallRequest(targetRedId = "callee-1", mode = "VIDEO", offerSdp = "sdp"),
            UsernamePasswordAuthenticationToken(callerId.toString(), "device-token")
        )

        assertEquals(HttpStatus.CREATED, response.statusCode)
        verify(callHistory).start(
            eq("caller-1"), eq("callee-1"), eq("callee-1"),
            eq(CallType.VIDEO_1V1), eq(CallRoute.RED), anyOrNull()
        )
    }
}
