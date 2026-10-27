package com.red.server.messaging

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.mockito.kotlin.*
import org.springframework.data.domain.Range
import org.springframework.data.redis.connection.RedisStreamCommands.XClaimOptions
import org.springframework.data.redis.connection.stream.Consumer
import org.springframework.data.redis.connection.stream.MapRecord
import org.springframework.data.redis.connection.stream.PendingMessage
import org.springframework.data.redis.connection.stream.PendingMessages
import org.springframework.data.redis.connection.stream.RecordId
import org.springframework.data.redis.connection.stream.StreamOffset
import org.springframework.data.redis.connection.stream.StreamReadOptions
import org.springframework.data.redis.core.RedisTemplate
import org.springframework.data.redis.core.StreamOperations
import java.time.Duration

/**
 * ترقية التوصيل إلى Redis Streams:
 * رسالة منشورة على الدفق تُستلم عبر consumer-group ثم XACK،
 * والمعلّق يُسترد عبر XPENDING+XCLAIM (مكافئ XAUTOCLAIM) بحد 100/دفعة.
 */
class MessageStreamServiceTest {

    private data class Harness(
        val service: MessageStreamService,
        val redis: RedisTemplate<String, String>,
        val ops: StreamOperations<String, String, String>
    )

    private fun harness(): Harness {
        val redis: RedisTemplate<String, String> = mock()
        val ops: StreamOperations<String, String, String> = mock()
        whenever(redis.opsForStream<String, String>()).thenReturn(ops)
        return Harness(MessageStreamService(redis), redis, ops)
    }

    @Test
    fun `published stream message is received via consumer group and acked after delivery`() {
        val h = harness()
        // 1) النشر: XADD على دفق المستلم.
        val recordId = RecordId.of("1700000000000-0")
        whenever(h.ops.add(any<MapRecord<String, String, String>>())).thenReturn(recordId)

        val published = h.service.publish("10002", "msg-uuid-9", 2)
        assertNotNull(published)

        val addCaptor = argumentCaptor<MapRecord<String, String, String>>()
        verify(h.ops).add(addCaptor.capture())
        val added = addCaptor.firstValue
        assertEquals("red:stream:messages:10002", added.stream)
        assertEquals("msg-uuid-9", added.value["uuid"])
        assertEquals("2", added.value["device"])
        // ضمان consumer-group نسخة الباك.
        verify(h.ops).createGroup("red:stream:messages:10002", h.service.groupName())

        // 2) الاستلام عبر consumer-group (XREADGROUP) — كما يفعل deliverMessageFromRedis.
        val stored = MapRecord.create(
            "red:stream:messages:10002",
            mapOf("uuid" to "msg-uuid-9", "device" to "2")
        ).withId(recordId)
        whenever(
            h.ops.read(any<Consumer>(), any<StreamReadOptions>(), any<StreamOffset<String>>())
        ).thenReturn(listOf(stored))

        val received = h.service.readNew("10002")
        assertEquals(1, received.size)
        assertEquals("msg-uuid-9", received[0].value["uuid"])
        assertEquals(recordId, received[0].id)

        // 3) XACK بعد التسليم.
        h.service.acknowledge("10002", received.map { it.id })
        verify(h.ops).acknowledge("red:stream:messages:10002", h.service.groupName(), recordId)
    }

    @Test
    fun `pending redelivery reclaims idle entries capped at 100 per batch`() {
        val h = harness()
        val group = h.service.groupName()
        // 150 معلّقًا — الدفعة تُسقف عند 100.
        val ids = (1..150).map { RecordId.of(1700000000000L, it.toLong()) }
        val pendingList = ids.take(100).map { id ->
            PendingMessage(id, Consumer.from(group, h.service.consumerName()), Duration.ofMinutes(5), 1)
        }
        val pending = PendingMessages(group, pendingList)
        whenever(
            h.ops.pending(any<String>(), any<String>(), any<Range<*>>(), any<Long>(), any<Duration>())
        ).thenReturn(pending)
        val claimedRecords = pendingList.map { pm ->
            MapRecord.create("red:stream:messages:10002", mapOf("uuid" to "m-${pm.id}", "device" to "1"))
                .withId(pm.id)
        }
        whenever(
            h.ops.claim(any<String>(), any<String>(), any<String>(), any<XClaimOptions>())
        ).thenReturn(claimedRecords)

        val claimed = h.service.reclaimIdle("10002", minIdle = Duration.ofSeconds(60), count = 500)

        assertEquals(100, claimed.size)
        // السقف طُبّق على استعلام XPENDING نفسه (لا 500).
        val countCaptor = argumentCaptor<Long>()
        verify(h.ops).pending(
            eq("red:stream:messages:10002"), eq(group), any<Range<*>>(),
            countCaptor.capture(), eq(Duration.ofSeconds(60))
        )
        assertEquals(100L, countCaptor.firstValue)
        // والاستلام تم عبر XCLAIM بمهلة الخمول نفسها.
        val claimCaptor = argumentCaptor<XClaimOptions>()
        verify(h.ops).claim(eq("red:stream:messages:10002"), eq(group), eq(h.service.consumerName()), claimCaptor.capture())
        assertEquals(Duration.ofSeconds(60), claimCaptor.firstValue.minIdleTime)
    }

    @Test
    fun `stream keys and group names follow contract`() {
        val h = harness()
        assertEquals("red:stream:messages:10002", MessageStreamService.streamKey("10002"))
        assertTrue(h.service.groupName().startsWith("red-backend-"))
        assertEquals(MessageStreamService.groupForHost("test-host"), "red-backend-test-host")
        assertEquals(100, MessageStreamService.MAX_BATCH)
    }

    @Test
    fun `redis outage degrades gracefully without throwing`() {
        val h = harness()
        whenever(h.ops.add(any<MapRecord<String, String, String>>())).thenThrow(RuntimeException("down"))
        whenever(
            h.ops.read(any<Consumer>(), any<StreamReadOptions>(), any<StreamOffset<String>>())
        ).thenThrow(RuntimeException("down"))

        assertNull(h.service.publish("10002", "msg-uuid-1", 1))
        assertTrue(h.service.readNew("10002").isEmpty())
        assertTrue(h.service.reclaimIdle("10002").isEmpty())
        assertEquals(0, h.service.acknowledge("10002", listOf(RecordId.of("1700000000000-0"))))
    }
}
