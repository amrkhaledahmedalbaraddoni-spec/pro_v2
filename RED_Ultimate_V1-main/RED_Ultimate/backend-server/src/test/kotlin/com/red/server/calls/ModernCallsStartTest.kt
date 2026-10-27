package com.red.server.calls

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import java.time.Instant

/**
 * يغطي إصلاحي بدء المكالمة في ModernCallsController:
 * 1) ICE حقيقية من IceServerController (TURN) لا STUN ثابتة.
 * 2) رسالة صدق للمتصل حين الطرف offline.
 */
class ModernCallsStartTest {
    private val secret = "abcdefghijklmnopqrstuvwxyz123456"
    private val history: CallHistoryService = mock()
    private val delivery: UnifiedCallDeliveryService = mock()
    private val ice = IceServerController(
        host = "192.168.1.67", port = 3478, tlsPort = 0, altPort = 0,
        secret = secret, ttlSeconds = 3600,
        openRelayEnabled = false, openRelayUsername = "", openRelayPassword = ""
    )
    private val controller = ModernCallsController(history, delivery, null, null, ice)

    private fun auth() = UsernamePasswordAuthenticationToken("caller-1", "device-token")

    private fun stubStart() {
        whenever(
            history.start(any(), any(), any(), any(), any(), anyOrNull())
        ).thenReturn(
            CallHistoryDocument(
                id = "call-x", initiatorId = "caller-1", targetId = "target-1",
                targetLabel = "target-1", type = CallType.AUDIO_1V1,
                route = CallRoute.RED, status = CallStatus.RINGING, startedAt = Instant.now()
            )
        )
        whenever(
            delivery.deliverCall(
                any(), any(), any(), any(), any(),
                any<Map<String, Any?>>(), any<Boolean>(), any<List<String>>()
            )
        ).thenReturn(true)
    }

    @Test
    fun `start returns real TURN ice and honest offline message`() {
        stubStart()
        whenever(delivery.isReachable(eq("target-1"))).thenReturn(false)

        val response = controller.startCall(
            StartCallRequest(targetId = "target-1", type = "AUDIO_1V1"), auth()
        ).body!!

        assertTrue(response.status == "RINGING")
        val urls = response.iceServers.orEmpty().flatMap { (it["urls"] as? List<*>) ?: emptyList<Any>() }
            .map { it.toString() }
        assertTrue(urls.any { it.contains("192.168.1.67") }) {
            "Client must receive the fixed TURN host, not generic STUN: $urls"
        }
        assertTrue(response.message.contains("offline", ignoreCase = true)) {
            "Caller must be told the target is offline: ${response.message}"
        }
    }

    @Test
    fun `start keeps ringing message when target is reachable`() {
        stubStart()
        whenever(delivery.isReachable(eq("target-1"))).thenReturn(true)

        val response = controller.startCall(
            StartCallRequest(targetId = "target-1", type = "AUDIO_1V1"), auth()
        ).body!!

        assertTrue(response.message.contains("Ringing", ignoreCase = true))
    }
}
