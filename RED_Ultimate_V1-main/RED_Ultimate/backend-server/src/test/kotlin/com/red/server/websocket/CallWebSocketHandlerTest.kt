package com.red.server.websocket

import tools.jackson.databind.ObjectMapper
import tools.jackson.module.kotlin.jacksonObjectMapper
import com.red.server.calls.CallHistoryDocument
import com.red.server.calls.CallHistoryService
import com.red.server.calls.CallRoute
import com.red.server.calls.CallStatus
import com.red.server.calls.CallType
import com.red.server.calls.RoomAliasService
import com.red.server.services.NotificationService
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.web.socket.TextMessage
import org.springframework.web.socket.WebSocketMessage
import org.springframework.web.socket.WebSocketSession
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.data.redis.core.ValueOperations
import java.time.Instant
import java.util.concurrent.CopyOnWriteArrayList

/**
 * قواعد الاختبار (كانت ثلاث أخطاء جذرية تُخفي سلوكاً سليماً):
 * 1) مُطابِقات Mockito أي داخل استدعاء verify() ⇒ InvalidUseOfMatchersException
 *    كانت تُقرأ كفشل منتج. الآن تُلَفّ دائماً بـ eq(...).
 * 2) معرفات الغرف "g-1"/"g-2"/"g-3" طولها 3 أحرف — **أقصر** من ROOM_ID
 *    (4..128) فكان فرع GROUP_CALL نفسه يرفضها بـ INVALID_ROOM_ID قبل
 *    التوزيع! الصواب معرفات ≥4 أحرف مطابقة للنمط: "rm01"/"rm02".
 * 3) اختبار v1 القديم كان يتوقع "must not start" بينما توثيق v1 نفسه
 *    (CallsV1Controller: "يبدأ سجل مكالمة") يقول إن OFFER يُنشئ سجلاً —
 *    أُصحح الاختبار ليطابق المواصفة: يبدأ سجلاً ويرنّ للمستلم.
 */
class CallWebSocketHandlerTest {
    private val objectMapper = jacksonObjectMapper()
    private val history: CallHistoryService = mock()
    private val notifications: NotificationService = mock()
    // المنفذ الرابع (redisTemplate) أُضيف في الإنتاج لمشاركة الجلسات بين النسخ.
    private val handler = CallWebSocketHandler(objectMapper, history, notifications, mock())

    /** معرفات غرف مطابقة لـ ROOM_ID (4..128) ⇒ فرع GROUP_CALL يقبلها ويتوزع. */
    private val tinyRoom = "rm01"
    private val tinyRoom2 = "rm02"

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

    @Test
    fun `offline offer is queued and flushed when callee connects`() {
        whenever(
            history.start(any(), any(), any(), any(), any(), anyOrNull())
        ).thenReturn(
            CallHistoryDocument(
                id = "call-1",
                initiatorId = "11111",
                targetId = "22222",
                targetLabel = "22222",
                type = CallType.AUDIO_1V1,
                route = CallRoute.RED,
                status = CallStatus.RINGING,
                startedAt = Instant.now()
            )
        )

        val caller = Probe("s-caller", "11111")
        handler.afterConnectionEstablished(caller.session)
        handler.handleTextMessage(
            caller.session,
            TextMessage("""{"callId":"call-1","targetUserId":"22222","type":"OFFER","mode":"VOICE","payload":{"sdp":"v=0"}}""")
        )

        verify(notifications).sendVoipPushNotification(eq("22222"), eq("11111"), eq("call-1"), eq("VOICE"), anyOrNull(), anyOrNull())
        assertTrue(caller.sent.any { it.contains("RINGING_PUSH_SENT") })

        val callee = Probe("s-callee", "22222")
        handler.afterConnectionEstablished(callee.session)
        assertTrue(callee.sent.any { it.contains("\"OFFER\"") && it.contains("v=0") }) {
            "Queued OFFER must be delivered on connect: ${callee.sent}"
        }
    }

    @Test
    fun `v1 style offer starts a call record and rings the callee`() {
        // المواصفة v1: OFFER يُنشئ سجل مكالمة ثم يرنّ للمستلم (قدمه CallsV1Controller).
        whenever(history.start(any(), any(), any(), any(), any(), anyOrNull())).thenAnswer {
            CallHistoryDocument(
                id = "call-legacy",
                initiatorId = "11111",
                targetId = "22222",
                targetLabel = "22222",
                type = CallType.AUDIO_1V1,
                route = CallRoute.RED,
                status = CallStatus.RINGING,
                startedAt = Instant.now()
            )
        }
        val alice = Probe("a", "11111")
        val bob = Probe("b", "22222")
        handler.afterConnectionEstablished(alice.session)
        handler.afterConnectionEstablished(bob.session)
        handler.handleTextMessage(
            alice.session,
            TextMessage("""{"callId":"call-legacy","targetUserId":"22222","type":"OFFER","mode":"VOICE","payload":{"sdp":"v=0"}}""")
        )
        verify(history).start(eq("11111"), eq("22222"), eq("22222"), any(), any(), anyOrNull())
        assertTrue(bob.sent.any { it.contains("\"OFFER\"") }) { "Callee must be rung: ${bob.sent}" }
    }

    @Test
    fun `renegotiate is forwarded without creating a new call`() {
        whenever(history.start(any(), any(), any(), any(), any(), anyOrNull()))
            .thenThrow(AssertionError("must not start"))
        val alice = Probe("a", "11111")
        val bob = Probe("b", "22222")
        handler.afterConnectionEstablished(alice.session)
        handler.afterConnectionEstablished(bob.session)
        handler.handleTextMessage(
            alice.session,
            TextMessage("""{"callId":"call-9","targetUserId":"22222","type":"RENEGOTIATE","mode":"VOICE","payload":{"sdp":"restart"}}""")
        )
        assertTrue(bob.sent.any { it.contains("RENEGOTIATE") })
        assertEquals(1, bob.sent.count { it.contains("RENEGOTIATE") })
    }

    @Test
    fun `conference invite is delivered without opening a 1-1 call`() {
        val bob = Probe("b", "22222")
        handler.afterConnectionEstablished(bob.session)
        handler.deliverInvite("22222", "CONFERENCE_INVITE", "room-9", "11111", "SPACE", mapOf("video" to "false"))
        assertTrue(bob.sent.any { it.contains("CONFERENCE_INVITE") && it.contains("room-9") })
    }

    @Test
    fun `group call invite rings every online invitee`() {
        val bob = Probe("b", "22222")
        val carol = Probe("c", "33333")
        handler.afterConnectionEstablished(bob.session)
        handler.afterConnectionEstablished(carol.session)
        handler.handleTextMessage(
            Probe("host", "11111").session,
            TextMessage("""{"callId":"$tinyRoom","type":"GROUP_CALL_INVITE","mode":"VIDEO","inviteeIds":["22222","33333"]}""")
        )
        assertTrue(bob.sent.any { it.contains("\"GROUP_CALL_INVITE\"") && it.contains(tinyRoom) })
        assertTrue(carol.sent.any { it.contains("\"GROUP_CALL_INVITE\"") && it.contains(tinyRoom) })
    }

    @Test
    fun `group call invite to offline invitee queues and pushes`() {
        handler.handleTextMessage(
            Probe("b", "22222").session,
            TextMessage("""{"callId":"$tinyRoom2","type":"GROUP_CALL_INVITE","mode":"VOICE","inviteeIds":["44444"]}""")
        )
        verify(notifications).sendVoipPushNotification(eq("44444"), eq("22222"), eq(tinyRoom2), eq("VOICE"), anyOrNull(), anyOrNull())
        val offline = Probe("s-offline", "44444")
        handler.afterConnectionEstablished(offline.session)
        assertTrue(offline.sent.any { it.contains("GROUP_CALL_INVITE") && it.contains(tinyRoom2) }) {
            "Queued group invite must be flushed on connect: ${offline.sent}"
        }
    }

    @Test
    fun `group call status is forwarded between members`() {
        // الحالة تُبث لكل أعضاء الغرفة المعروفة — فتُنشأ الغرفة بدعوة أولاً.
        val bob = Probe("b", "22222")
        val carol = Probe("c", "33333")
        handler.afterConnectionEstablished(bob.session)
        handler.afterConnectionEstablished(carol.session)
        val host = Probe("host", "11111")
        handler.handleTextMessage(
            host.session,
            TextMessage("""{"callId":"$tinyRoom","type":"GROUP_CALL_INVITE","mode":"VIDEO","inviteeIds":["22222","33333"]}""")
        )
        handler.handleTextMessage(
            host.session,
            TextMessage("""{"callId":"$tinyRoom","type":"GROUP_CALL_STATUS","mode":"VOICE","payload":{"status":"joined"}}""")
        )
        assertTrue(bob.sent.any { it.contains("GROUP_CALL_STATUS") })
        assertTrue(carol.sent.any { it.contains("GROUP_CALL_STATUS") })
    }

    @Test
    fun `status with unknown room echoes back to sender`() {
        // سلوك الأمان الموثّق: غرفة مجهولة (إعادة تشغيل الخادم وسط مكالمة)
        // ⇒ ترتد الإشارة للمرسل بدل إسقاطها بصمت.
        val host = Probe("host", "11111")
        handler.afterConnectionEstablished(host.session)
        handler.handleTextMessage(
            host.session,
            TextMessage("""{"callId":"rm09","type":"GROUP_CALL_STATUS","mode":"VOICE","payload":{"status":"joined"}}""")
        )
        assertTrue(host.sent.any { it.contains("GROUP_CALL_STATUS") })
    }

    @Test
    fun `alias bound via REST resolves via WS`() {
        val redis: StringRedisTemplate = mock()
        val ops: ValueOperations<String, String> = mock()
        whenever(redis.opsForValue()).thenReturn(ops)
        whenever(ops.get(any())).thenReturn(null)
        val aliases = RoomAliasService(redis)
        aliases.link("legacyCallAlias01", "GRP_legacyCallAlias01")
        val aliased = CallWebSocketHandler(objectMapper, history, notifications, redis, aliases)
        val bob = Probe("b-alias", "22222")
        aliased.afterConnectionEstablished(bob.session)
        aliased.handleTextMessage(
            Probe("host-alias", "11111").session,
            TextMessage("""{"callId":"legacyCallAlias01","type":"GROUP_CALL_INVITE","mode":"VOICE","inviteeIds":["22222"]}""")
        )
        assertTrue(bob.sent.any { it.contains("GRP_legacyCallAlias01") }) { "WS must resolve REST alias: ${bob.sent}" }
        assertEquals("11111", aliased.groupCallHost("legacyCallAlias01"))
        assertEquals("11111", aliased.groupCallHost("GRP_legacyCallAlias01"))
    }
}
