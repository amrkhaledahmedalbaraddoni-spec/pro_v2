package com.red.server.websocket

import tools.jackson.module.kotlin.jacksonObjectMapper
import com.red.server.calls.CallHistoryService
import com.red.server.services.NotificationService
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.data.redis.core.SetOperations
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.data.redis.core.ZSetOperations
import org.springframework.web.socket.CloseStatus
import org.springframework.web.socket.TextMessage
import org.springframework.web.socket.WebSocketMessage
import org.springframework.web.socket.WebSocketSession
import java.util.concurrent.CopyOnWriteArrayList

/**
 * يغطي إصلاح حضور مستخدمي /ws/calls:
 * الاتصال يسجل red:online + presence، وآخر إغلاق يزيلهما (والجلسة
 * الثانية المفتوحة تمنع الإزالة المبكرة).
 */
class CallWebSocketHandlerPresenceTest {
    private val objectMapper = jacksonObjectMapper()
    private val history: CallHistoryService = mock()
    private val notifications: NotificationService = mock()
    private val redis: StringRedisTemplate = mock()
    private val setOps: SetOperations<String, String> = mock()
    private val zsetOps: ZSetOperations<String, String> = mock()
    private val handler = CallWebSocketHandler(objectMapper, history, notifications, redis).also {
        whenever(redis.opsForSet()).thenReturn(setOps)
        whenever(redis.opsForZSet()).thenReturn(zsetOps)
    }

    private class Probe(sessionId: String, userId: String) {
        val sent = CopyOnWriteArrayList<String>()
        private val attrs: MutableMap<String, Any> = mutableMapOf("userId" to userId)
        val session: WebSocketSession = mock<WebSocketSession>().also { sock ->
            whenever(sock.id).thenReturn(sessionId)
            whenever(sock.attributes).thenReturn(attrs)
            whenever(sock.isOpen).thenReturn(true)
            org.mockito.kotlin.doAnswer { invocation ->
                val message = invocation.getArgument<WebSocketMessage<*>>(0)
                if (message is TextMessage) sent.add(message.payload)
                null
            }.whenever(sock).sendMessage(any())
        }
    }

    @Test
    fun `connect registers online presence and last close removes it`() {
        val p = Probe("s-1", "user-9")
        handler.afterConnectionEstablished(p.session)
        verify(setOps).add(eq("red:online"), eq("user-9"))
        verify(zsetOps).add(eq("red:presence:index"), eq("user-9"), any())

        handler.afterConnectionClosed(p.session, CloseStatus.NORMAL)
        verify(setOps).remove(eq("red:online"), eq("user-9"))
        verify(zsetOps).remove(eq("red:presence:index"), eq("user-9"))
    }

    @Test
    fun `second open session keeps presence until it closes`() {
        val a = Probe("s-a", "user-9")
        val b = Probe("s-b", "user-9")
        handler.afterConnectionEstablished(a.session)
        handler.afterConnectionEstablished(b.session)

        handler.afterConnectionClosed(a.session, CloseStatus.NORMAL)
        verify(setOps, never()).remove(eq("red:online"), eq("user-9"))
        verify(zsetOps, never()).remove(eq("red:presence:index"), eq("user-9"))

        handler.afterConnectionClosed(b.session, CloseStatus.NORMAL)
        verify(setOps).remove(eq("red:online"), eq("user-9"))
        verify(zsetOps).remove(eq("red:presence:index"), eq("user-9"))
    }
}
