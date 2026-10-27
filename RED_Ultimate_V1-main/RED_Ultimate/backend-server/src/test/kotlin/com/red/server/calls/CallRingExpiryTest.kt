package com.red.server.calls

import com.red.server.websocket.CallWebSocketHandler
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.data.mongodb.core.query.Query
import java.time.Instant

/**
 * يغطي إصلاح انتهاء الرنين:
 * 1) المنتهية تُسجَّل MISSED وتُرجَع كمستندات.
 * 2) المهمة تُشعر المتصل بـ NO_ANSWER وتُسقط المعلق والطوابير.
 */
class CallRingExpiryTest {
    private val mongo: MongoTemplate = mock()
    private val publisher: CallEventPublisher = mock()
    private val service = CallHistoryService(mongo, publisher)

    private fun ringingDoc(id: String, startedAt: Instant) = CallHistoryDocument(
        id = id, initiatorId = "caller-1", targetId = "callee-1", targetLabel = "callee-1",
        type = CallType.AUDIO_1V1, route = CallRoute.RED,
        status = CallStatus.RINGING, startedAt = startedAt
    )

    @Test
    fun `stale ringing becomes missed and is returned`() {
        val stale = ringingDoc("old-1", Instant.now().minusSeconds(120))
        whenever(mongo.find(any<Query>(), eq(CallHistoryDocument::class.java))).thenReturn(listOf(stale))
        whenever(mongo.findById(eq("old-1"), eq(CallHistoryDocument::class.java))).thenReturn(stale)
        whenever(mongo.save(any<CallHistoryDocument>())).thenAnswer { it.getArgument(0) }

        val expired = service.expireStaleRingingWithDocs()

        assertEquals(1, expired.size)
        assertEquals(CallStatus.MISSED, stale.status)
        verify(publisher).callMissed(eq("old-1"))
    }

    @Test
    fun `expiry job notifies the caller and purges queues`() {
        val stale = ringingDoc("old-9", Instant.now().minusSeconds(120))
        stale.status = CallStatus.MISSED
        val history: CallHistoryService = mock()
        val handler: CallWebSocketHandler = mock()
        val delivery: UnifiedCallDeliveryService = mock()
        whenever(history.expireStaleRingingWithDocs()).thenReturn(listOf(stale))

        CallRingExpiryJob(history, handler, delivery).expireUnanswered()

        // المتصل يعلم سبباً صادقاً بدل الصمت.
        verify(handler).deliverSignal(
            eq("caller-1"), eq("END"), eq("old-9"), eq("callee-1"), any(), any()
        )
        // لا رنين لاحق لعرض ميت ولا محاولة جديدة.
        verify(delivery).discardPending(eq("old-9"))
        verify(handler).cancelPendingCall(eq("old-9"))
        verify(handler).purgeQueuedCall(eq("old-9"), eq("callee-1"))
    }
}
