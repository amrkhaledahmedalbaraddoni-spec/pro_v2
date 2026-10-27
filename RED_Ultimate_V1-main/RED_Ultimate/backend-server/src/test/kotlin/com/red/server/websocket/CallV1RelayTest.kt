package com.red.server.websocket

import tools.jackson.module.kotlin.jacksonObjectMapper
import com.red.server.calls.ActiveCallRegistry
import com.red.server.calls.CallHistoryDocument
import com.red.server.calls.CallHistoryService
import com.red.server.calls.CallRoute
import com.red.server.calls.CallStatus
import com.red.server.calls.CallType
import com.red.server.services.NotificationService
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.web.socket.TextMessage
import org.springframework.web.socket.WebSocketMessage
import org.springframework.web.socket.WebSocketSession
import java.time.Instant
import java.util.concurrent.CopyOnWriteArrayList

/**
 * يغطي إصلاح ترحيل v1 (كان no-op): الجواب والرفض وICE تصل المتصل/النظير.
 */
class CallV1RelayTest {
    private val objectMapper = jacksonObjectMapper()
    private val history: CallHistoryService = mock()
    private val notifications: NotificationService = mock()
    private val redis: StringRedisTemplate = mock()
    private val activeCalls: ActiveCallRegistry = mock()
    private val handler = CallWebSocketHandler(objectMapper, history, notifications, redis, null, activeCalls)

    private class Probe(sessionId: String, userId: String) {
        val sent = CopyOnWriteArrayList<String>()
        private val attrs: MutableMap<String, Any> = mutableMapOf("userId" to userId)
        val session: WebSocketSession = mock<WebSocketSession>().also { sock ->
            whenever(sock.id).thenReturn(sessionId)
            whenever(sock.attributes).thenReturn(attrs)
            whenever(sock.isOpen).thenReturn(true)
            doAnswer { invocation ->
                val message = invocation.getArgument<WebSocketMessage<*>>(0)
                if (message is TextMessage) sent.add(message.payload)
                null
            }.whenever(sock).sendMessage(any())
        }
    }

    private fun doc(id: String) = CallHistoryDocument(
        id = id, initiatorId = "caller-1", targetId = "callee-1", targetLabel = "callee-1",
        type = CallType.AUDIO_1V1, route = CallRoute.RED,
        status = CallStatus.RINGING, startedAt = Instant.now()
    )

    @Test
    fun `answer sdp reaches the caller live`() {
        whenever(history.answer(any(), anyOrNull())).thenAnswer { doc("c-answer") }
        val caller = Probe("s-caller", "caller-1")
        val callee = Probe("s-callee", "callee-1")
        handler.afterConnectionEstablished(caller.session)
        handler.afterConnectionEstablished(callee.session)

        handler.storeCallOffer("c-answer", "caller-1", "callee-1", "VOICE", "sdp-offer")
        handler.takeCallOffer("c-answer", "callee-1")
        handler.sendAnswerToCaller("c-answer", "callee-1", "sdp-answer")

        assertTrue(caller.sent.any { it.contains("\"ANSWER\"") && it.contains("sdp-answer") }) {
            "Caller must receive ANSWER with SDP: ${caller.sent}"
        }
    }

    @Test
    fun `reject reaches the caller live`() {
        whenever(history.rejected(any(), anyOrNull())).thenAnswer { doc("c-reject") }
        val caller = Probe("s-caller", "caller-1")
        val callee = Probe("s-callee", "callee-1")
        handler.afterConnectionEstablished(caller.session)
        handler.afterConnectionEstablished(callee.session)

        handler.storeCallOffer("c-reject", "caller-1", "callee-1", "VOICE", "sdp-offer")
        handler.takeCallOffer("c-reject", "callee-1")
        handler.sendRejectToCaller("c-reject", "callee-1")

        assertTrue(caller.sent.any { it.contains("\"REJECT\"") }) {
            "Caller must receive REJECT instead of endless ringing: ${caller.sent}"
        }
    }

    @Test
    fun `ice candidate is forwarded to the peer`() {
        val caller = Probe("s-caller", "caller-1")
        val callee = Probe("s-callee", "callee-1")
        handler.afterConnectionEstablished(caller.session)
        handler.afterConnectionEstablished(callee.session)

        handler.storeCallOffer("c-ice", "caller-1", "callee-1", "VOICE", "sdp-offer")
        handler.relayIceCandidate("c-ice", "caller-1", "candidate:1", 0, "audio")

        assertTrue(callee.sent.any { it.contains("\"ICE\"") && it.contains("candidate:1") }) {
            "Callee must receive ICE: ${callee.sent}"
        }
    }

    @Test
    fun `answer to offline caller is queued and flushed on connect`() {
        whenever(history.answer(any(), anyOrNull())).thenAnswer { doc("c-off") }
        val callee = Probe("s-callee", "callee-1")
        handler.afterConnectionEstablished(callee.session)

        handler.storeCallOffer("c-off", "caller-1", "callee-1", "VOICE", "sdp-offer")
        handler.takeCallOffer("c-off", "callee-1")
        handler.sendAnswerToCaller("c-off", "callee-1", "sdp-answer")

        val caller = Probe("s-caller", "caller-1")
        handler.afterConnectionEstablished(caller.session)
        assertTrue(caller.sent.any { it.contains("\"ANSWER\"") && it.contains("sdp-answer") }) {
            "Offline caller must receive queued ANSWER on connect: ${caller.sent}"
        }
    }

    @Test
    fun `ws answer to offline peer is queued and flushed on connect`() {
        whenever(history.answer(any(), anyOrNull())).thenAnswer { doc("c-ws") }
        val callee = Probe("s-callee", "callee-1")
        handler.afterConnectionEstablished(callee.session)
        handler.handleTextMessage(
            callee.session,
            TextMessage("""{"callId":"c-ws","targetUserId":"caller-1","type":"ANSWER","mode":"VOICE","payload":{}}""")
        )
        val caller = Probe("s-caller", "caller-1")
        handler.afterConnectionEstablished(caller.session)
        assertTrue(caller.sent.any { it.contains("\"ANSWER\"") }) {
            "Offline WS peer must receive queued ANSWER on connect: ${caller.sent}"
        }
    }
}
