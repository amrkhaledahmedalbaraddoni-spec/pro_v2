package com.red.server

import com.google.protobuf.ByteString
import com.red.server.auth.model.UserAccount
import com.red.server.auth.repository.UserAccountRepository
import com.red.server.database.ConversationSequence
import com.red.server.database.MessageDocument
import com.red.server.groups.GroupDocument
import com.red.server.messaging.MessageService
import com.red.server.social.UuidV7
import com.red.sovereign.proto.RedProtos
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.mockito.kotlin.*
import org.springframework.data.mongodb.core.FindAndModifyOptions
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.data.mongodb.core.query.Query
import org.springframework.data.mongodb.core.query.Update
import org.springframework.data.redis.core.RedisTemplate
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.core.RowMapper
import java.util.UUID
import java.util.concurrent.atomic.AtomicLong

/**
 * مروحة multi-device لرسائل 1:1 + إيصالات المجموعات المجمعة.
 *
 * 1) processIncomingFanout تنسخ الرسالة لكل protocolDeviceId معتمد للمستلم
 *    من user_devices (بسقف 100) مع احترام الحظر المتبادل.
 * 2) group_message_receipts: كتابة مجمعة رتيبة على UNIQUE(message_uuid, user_id)
 *    + قراءة مجمعة لعدّادات delivered/read.
 */
class MessageServiceFanoutTest {

    private data class FanoutHarness(
        val service: MessageService,
        val mongo: MongoTemplate,
        val jdbc: JdbcTemplate,
        val seq: AtomicLong
    )

    private fun harness(
        approvedDevices: List<Int> = listOf(1, 2, 3),
        blockedCount: Int = 0
    ): FanoutHarness {
        val mongo: MongoTemplate = mock()
        val jdbc: JdbcTemplate = mock()
        val users: UserAccountRepository = mock()
        val service = MessageService(
            mongo, mock<RedisTemplate<String, String>>(), users, jdbc, mock()
        )

        val senderId = UUID.randomUUID()
        val receiverId = UUID.randomUUID()
        whenever(users.findByRedId("10001")).thenReturn(
            UserAccount(id = senderId, redId = "10001", username = "alice", displayName = "Alice", passwordHash = "x")
        )
        whenever(users.findByRedId("10002")).thenReturn(
            UserAccount(id = receiverId, redId = "10002", username = "bob", displayName = "Bob", passwordHash = "x")
        )
        // الحظر المتبادل: 0 = مسموح، >0 = محظور في أي اتجاه
        whenever(
            jdbc.queryForObject(
                eq("SELECT COUNT(*) FROM user_blocks WHERE (blocker_id=? AND blocked_id=?) OR (blocker_id=? AND blocked_id=?)"),
                eq(Int::class.java), any(), any(), any(), any()
            )
        ).thenReturn(blockedCount)
        // أجهزة المستلم المعتمدة من user_devices
        whenever(jdbc.queryForList(any<String>(), eq(Int::class.java), any()))
            .thenReturn(approvedDevices)

        whenever(mongo.exists(any<Query>(), eq(GroupDocument::class.java))).thenReturn(false)
        whenever(mongo.findOne(any<Query>(), eq(MessageDocument::class.java))).thenReturn(null)
        val seq = AtomicLong(0)
        whenever(
            mongo.findAndModify(
                any<Query>(), any<Update>(), any<FindAndModifyOptions>(),
                eq(ConversationSequence::class.java)
            )
        ).thenAnswer { ConversationSequence("conv-10001-10002", seq.incrementAndGet()) }
        whenever(mongo.save(any<MessageDocument>())).thenAnswer { it.getArgument<MessageDocument>(0) }

        return FanoutHarness(service, mongo, jdbc, seq)
    }

    private fun chat(receiverDevice: Int = 2): RedProtos.ChatMessage =
        RedProtos.ChatMessage.newBuilder()
            .setId(UuidV7.next())
            .setConversationId("conv-10001-10002")
            .setSenderId("10001")
            .setReceiverId("10002")
            .setPayload(ByteString.copyFrom(ByteArray(8) { 1 }))
            .setType("TEXT")
            .setSenderDeviceId(1)
            .setReceiverDeviceId(receiverDevice)
            .setCiphertextType(2)
            .build()

    @Test
    fun `three approved devices receive three stored copies`() {
        val h = harness(approvedDevices = listOf(1, 2, 3))
        val msg = chat(receiverDevice = 2)

        val copies = h.service.processIncomingFanout(msg, null, null)

        // 3 أجهزة معتمدة → 3 نسخ مخزنة
        assertEquals(3, copies.size)
        assertEquals(setOf(1, 2, 3), copies.map { it.receiverDeviceId }.toSet())
        // الأساسية تحمل UUID المغلف؛ الإضافية UUIDv7 جديدة مربوطة بالأصل
        assertEquals(msg.id, copies[0].uuid)
        assertNull(copies[0].fanoutParentUuid)
        assertTrue(copies.subList(1, 3).all { it.fanoutParentUuid == msg.id })
        assertEquals(3, copies.map { it.uuid }.toSet().size)
        // كل نسخة بتسلسل مستقل ودورة ACK مستقلة
        assertEquals(setOf(1L, 2L, 3L), copies.map { it.sequenceNumber }.toSet())
        assertTrue(copies.all { it.status == "SENT" })
        verify(h.mongo, times(3)).save(any<MessageDocument>())
    }

    @Test
    fun `fanout retry with same uuid returns existing copies without new saves`() {
        val h = harness(approvedDevices = listOf(1, 2, 3))
        val msg = chat(receiverDevice = 2)

        val first = h.service.processIncomingFanout(msg, null, null)
        assertEquals(3, first.size)

        // إعادة الإرسال: الفحص المسبق يجد الأساسية + fanoutParentUuid يجد الشقيقات
        whenever(h.mongo.findOne(any<Query>(), eq(MessageDocument::class.java))).thenReturn(first[0])
        whenever(h.mongo.find(any<Query>(), eq(MessageDocument::class.java))).thenReturn(first.subList(1, 3))

        val second = h.service.processIncomingFanout(msg, null, null)
        assertEquals(3, second.size)
        assertEquals(first.map { it.uuid }.toSet(), second.map { it.uuid }.toSet())
        verify(h.mongo, times(3)).save(any<MessageDocument>())
    }

    @Test
    fun `fanout is capped at 100 copies per message`() {
        val h = harness(approvedDevices = (1..127).toList())
        val msg = chat(receiverDevice = 1)

        val copies = h.service.processIncomingFanout(msg, null, null)

        assertEquals(MessageService.MAX_FANOUT_COPIES, copies.size)
        assertEquals(100, copies.map { it.receiverDeviceId }.toSet().size)
        verify(h.mongo, times(100)).save(any<MessageDocument>())
    }

    @Test
    fun `mutual block rejects fanout before any copy is stored`() {
        val h = harness(blockedCount = 1)
        val msg = chat()

        assertThrows(IllegalArgumentException::class.java) {
            h.service.processIncomingFanout(msg, null, null)
        }
        verify(h.mongo, never()).save(any<MessageDocument>())
    }

    @Test
    fun `group receipts bulk upsert uses ON CONFLICT and counts aggregate delivered-read`() {
        val jdbc: JdbcTemplate = mock()
        val service = MessageService(mock(), mock<RedisTemplate<String, String>>(), mock(), jdbc, mock())
        val groupId = UUID.randomUUID()
        val u1 = UUID.randomUUID()
        val u2 = UUID.randomUUID()

        whenever(jdbc.batchUpdate(any<String>(), any<List<Array<Any>>>())).thenReturn(intArrayOf(1, 1))
        whenever(jdbc.queryForMap(any<String>(), any())).thenReturn(
            mutableMapOf<String, Any?>("delivered" to 2L, "read" to 1L, "total" to 3L)
        )

        val applied = service.upsertGroupReceiptsBulk(
            groupId, "msg-uuid-1", mapOf(u1 to "DELIVERED", u2 to "read")
        )
        assertArrayEquals(intArrayOf(1, 1), applied)

        // قيد الفرادة UNIQUE(message_uuid, user_id) هو بوابة الـ upsert
        val sqlCaptor = argumentCaptor<String>()
        verify(jdbc).batchUpdate(sqlCaptor.capture(), any<List<Array<Any>>>())
        assertTrue(sqlCaptor.firstValue.contains("ON CONFLICT (message_uuid, user_id)"))

        val counts = service.getGroupReceiptCounts("msg-uuid-1")
        assertEquals("msg-uuid-1", counts.messageUuid)
        assertEquals(2, counts.delivered)
        assertEquals(1, counts.read)
        assertEquals(3, counts.total)
    }

    @Test
    fun `single group receipt upsert writes one row and rejects bad status`() {
        val jdbc: JdbcTemplate = mock()
        val service = MessageService(mock(), mock<RedisTemplate<String, String>>(), mock(), jdbc, mock())
        val groupId = UUID.randomUUID()
        val userId = UUID.randomUUID()

        whenever(
            jdbc.update(any<String>(), any(), any(), any(), any(), any(), any(), any())
        ).thenReturn(1)
        assertEquals(1, service.upsertGroupReceipt(groupId, "m1", userId, "READ"))

        assertThrows(IllegalArgumentException::class.java) {
            service.upsertGroupReceipt(groupId, "m1", userId, "BOGUS")
        }
        assertThrows(IllegalArgumentException::class.java) {
            service.upsertGroupReceiptsBulk(groupId, "m1", mapOf(userId to "NOPE"))
        }
        // دفعة فوق السقف تُرفض قبل لمس قاعدة البيانات
        val oversized = (1..(MessageService.GROUP_RECEIPT_BATCH_LIMIT + 1)).associate { UUID.randomUUID() to "READ" }
        assertThrows(IllegalArgumentException::class.java) {
            service.upsertGroupReceiptsBulk(groupId, "m1", oversized)
        }
    }

    @Test
    fun `bulk receipt read returns rows and filters by status`() {
        val jdbc: JdbcTemplate = mock()
        val service = MessageService(mock(), mock<RedisTemplate<String, String>>(), mock(), jdbc, mock())
        val u1 = UUID.randomUUID()
        val rows = listOf(
            MessageService.GroupReceiptRow(u1, "READ", null)
        )
        whenever(
            jdbc.query(any<String>(), any<RowMapper<MessageService.GroupReceiptRow>>(), any(), any())
        ).thenReturn(rows)

        val got = service.getGroupReceipts("m1", "read")
        assertEquals(1, got.size)
        assertEquals(u1, got[0].userId)
        assertEquals("READ", got[0].status)

        assertThrows(IllegalArgumentException::class.java) {
            service.getGroupReceipts("m1", "BOGUS")
        }
    }
}
