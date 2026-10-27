package com.red.server.calls

import tools.jackson.module.kotlin.jacksonObjectMapper
import com.red.server.services.NotificationService
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.whenever
import org.springframework.data.redis.core.ListOperations
import org.springframework.data.redis.core.SetOperations
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.data.redis.core.ZSetOperations
import java.time.Instant

/**
 * يغطي إصلاحات UnifiedCallDeliveryService:
 * 1) النشر على قناة red:calls:signals وبصيغة sourceUserId/targetUserId.
 * 2) عدم تزييف حضور المستدعى.
 * 3) صدق isReachable.
 * 4) إسقاط المعلق عبر discardPending.
 */
class UnifiedCallDeliveryServiceTest {
    private val objectMapper = jacksonObjectMapper()
    private val redis: StringRedisTemplate = mock()
    private val listOps: ListOperations<String, String> = mock()
    private val setOps: SetOperations<String, String> = mock()
    private val zsetOps: ZSetOperations<String, String> = mock()
    private val history: CallHistoryService = mock()
    private val notifications: NotificationService = mock()

    private fun service() = UnifiedCallDeliveryService(objectMapper, redis, history, notifications)

    private fun stubCommon() {
        whenever(redis.opsForList()).thenReturn(listOps)
        whenever(redis.opsForSet()).thenReturn(setOps)
        whenever(redis.opsForZSet()).thenReturn(zsetOps)
        whenever(
            history.start(any(), any(), any(), any(), any(), anyOrNull())
        ).thenReturn(
            CallHistoryDocument(
                id = "call-1", initiatorId = "caller-1", targetId = "callee-1",
                targetLabel = "callee-1", type = CallType.AUDIO_1V1,
                route = CallRoute.RED, status = CallStatus.RINGING, startedAt = Instant.now()
            )
        )
        whenever(redis.convertAndSend(any(), any())).thenReturn(1L)
    }

    @Test
    fun `deliverCall publishes parseable signal on the listened channel`() {
        stubCommon()
        service().deliverCall("call-1", "caller-1", "callee-1", "OFFER", "VOICE", mapOf("isVideo" to false))

        // القناة الوحيدة التي تستمع لها كل النسخ — لا قناة ميتة.
        verify(redis, never()).convertAndSend(eq("red:calls:delivery"), any())
        val captor = org.mockito.ArgumentCaptor.forClass(String::class.java)
        verify(redis).convertAndSend(eq("red:calls:signals"), captor.capture())
        @Suppress("UNCHECKED_CAST")
        val body = objectMapper.readValue(captor.value, Map::class.java) as Map<String, Any?>
        assertEquals("call-1", body["callId"])
        assertEquals("caller-1", body["sourceUserId"])
        assertEquals("callee-1", body["targetUserId"])
        assertEquals("OFFER", body["type"])
        assertFalse(body.containsKey("source"), "legacy source key must not be published")
        assertFalse(body.containsKey("target"), "legacy target key must not be published")
    }

    @Test
    fun `deliverCall does not fake callee presence`() {
        stubCommon()
        service().deliverCall("call-1", "caller-1", "callee-1", "OFFER", "VOICE")
        // مجرد الاتصال بالمستدعى لا يجعله online — أي لمس لـ ZSET مرفوض.
        verifyNoInteractions(zsetOps)
    }

    @Test
    fun `isReachable reflects the live online set`() {
        stubCommon()
        whenever(setOps.isMember(eq("red:online"), eq("ghost-1"))).thenReturn(false)
        whenever(setOps.isMember(eq("red:online"), eq("live-1"))).thenReturn(true)
        assertFalse(service().isReachable("ghost-1"))
        assertTrue(service().isReachable("live-1"))
    }

    @Test
    fun `discardPending drops delivery state without touching history`() {
        stubCommon()
        val svc = service()
        svc.deliverCall("call-1", "caller-1", "callee-1", "OFFER", "VOICE")
        assertEquals(1, svc.getPendingCount())
        svc.discardPending("call-1")
        assertEquals(0, svc.getPendingCount())
        // بلا آثار على السجل: الاستدعاء الوحيد المسموح هو start أثناء التسليم.
        verify(history, org.mockito.kotlin.times(1))
            .start(any(), any(), any(), any(), any(), anyOrNull())
    }
}
