package com.red.server.groups

import org.springframework.data.annotation.Id
import org.springframework.data.mongodb.core.index.CompoundIndex
import org.springframework.data.mongodb.core.index.Indexed
import org.springframework.data.mongodb.core.mapping.Document
import org.springframework.data.mongodb.core.query.Criteria
import org.springframework.data.mongodb.core.query.Query
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.stereotype.Service
import jakarta.validation.constraints.NotBlank
import java.time.Instant
import java.util.UUID

/**
 * نموذج القناة
 */
@Document("group_channels")
@CompoundIndex(name = "group_channel_unique", def = "{'groupId': 1, 'name': 1}", unique = true)
data class GroupChannel(
    @Id val id: String = "",
    @Indexed val groupId: String = "",
    val name: String = "",
    val description: String? = null,
    val channelType: String = "", // TEXT, VOICE, ANNOUNCEMENT
    val private: Boolean = false,
    val createdBy: String = "",
    val createdAt: Instant = Instant.now(),
    var updatedAt: Instant = Instant.now(),
    val messageCount: Long = 0L,
    val memberCount: Long = 0L,
    val topic: String? = null,
    val archived: Boolean = false,
    val position: Int = 0
)

/**
 * نموذج الخيط (Thread) في القناة
 */
@Document("channel_threads")
data class ChannelThread(
    @Id val id: String = "",
    @Indexed val channelId: String = "",
    @Indexed val groupId: String = "",
    val title: String = "",
    val createdBy: String = "",
    val createdAt: Instant = Instant.now(),
    val messageCount: Long = 0L,
    val lastMessageAt: Instant? = null,
    val archived: Boolean = false,
    val pinned: Boolean = false
)

/**
 * نموذج الرسالة المثبتة في القناة
 */
@Document("channel_pinned_messages")
@CompoundIndex(name = "channel_message_unique", def = "{'channelId': 1, 'messageUuid': 1}", unique = true)
data class ChannelPinnedMessage(
    @Id val id: String = "",
    @Indexed val channelId: String = "",
    @Indexed val groupId: String = "",
    val messageUuid: String = "",
    val senderId: String = "",
    val messagePreview: String? = null,
    val pinnedBy: String = "",
    val pinnedAt: Instant = Instant.now(),
    val expiresAt: Instant? = null
)

data class CreateChannelRequest(
    @field:NotBlank val name: String,
    val description: String? = null,
    val channelType: String = "TEXT",
    val private: Boolean = false,
    val topic: String? = null
)

data class UpdateChannelRequest(
    val name: String? = null,
    val description: String? = null,
    val topic: String? = null,
    val archived: Boolean? = null
)

data class CreateThreadRequest(
    @field:NotBlank val title: String,
    val messageUuid: String? = null
)

data class PinChannelMessageRequest(
    @field:NotBlank val messageUuid: String,
    val expiresAt: Instant? = null
)

data class GroupChannelResponse(
    val id: String,
    val groupId: String,
    val name: String,
    val description: String?,
    val channelType: String,
    val private: Boolean,
    val createdBy: String,
    val createdAt: Instant,
    val updatedAt: Instant,
    val messageCount: Long,
    val memberCount: Long,
    val topic: String?,
    val archived: Boolean,
    val position: Int
)

data class ChannelThreadResponse(
    val id: String,
    val channelId: String,
    val groupId: String,
    val title: String,
    val createdBy: String,
    val createdAt: Instant,
    val messageCount: Long,
    val lastMessageAt: Instant?,
    val archived: Boolean,
    val pinned: Boolean
)

data class ChannelPinnedMessageResponse(
    val id: String,
    val channelId: String,
    val messageUuid: String,
    val senderId: String,
    val messagePreview: String?,
    val pinnedBy: String,
    val pinnedAt: Instant,
    val expiresAt: Instant?
)

/**
 * خدمة إدارة القنوات المتقدمة
 */
@Service
class ChannelsManager(private val mongo: MongoTemplate) {

    /**
     * إنشاء قناة جديدة
     */
    fun createChannel(
        groupId: String,
        actorId: UUID,
        request: CreateChannelRequest
    ): GroupChannelResponse {
        val name = request.name.trim().lowercase()
        require(name.length in 2..100) { "Channel name must be 2-100 characters" }
        require(request.channelType in listOf("TEXT", "VOICE", "ANNOUNCEMENT")) { "Invalid channel type" }

        val id = "$groupId:${System.currentTimeMillis()}"
        val channel = GroupChannel(
            id = id,
            groupId = groupId,
            name = name,
            description = request.description?.trim(),
            channelType = request.channelType,
            private = request.private,
            createdBy = actorId.toString(),
            createdAt = Instant.now(),
            topic = request.topic?.trim()
        )

        mongo.save(channel)
        return channel.toResponse()
    }

    /**
     * تحديث قناة
     */
    fun updateChannel(
        groupId: String,
        channelId: String,
        request: UpdateChannelRequest
    ): GroupChannelResponse {
        val channel = mongo.findById(channelId, GroupChannel::class.java)
            ?: throw NoSuchElementException("Channel not found")
        require(channel.groupId == groupId) { "Channel does not belong to this group" }

        val updated = channel.copy(
            name = request.name?.trim()?.lowercase() ?: channel.name,
            description = request.description ?: channel.description,
            topic = request.topic ?: channel.topic,
            archived = request.archived ?: channel.archived,
            updatedAt = Instant.now()
        )

        mongo.save(updated)
        return updated.toResponse()
    }

    /**
     * حذف قناة
     */
    fun deleteChannel(groupId: String, channelId: String) {
        val channel = mongo.findById(channelId, GroupChannel::class.java)
            ?: throw NoSuchElementException("Channel not found")
        require(channel.groupId == groupId) { "Channel does not belong to this group" }

        mongo.remove(Query(Criteria.where("id").`is`(channelId)), GroupChannel::class.java)
        // تنظيف الخيوط والرسائل المثبتة
        mongo.remove(Query(Criteria.where("channelId").`is`(channelId)), ChannelThread::class.java)
        mongo.remove(Query(Criteria.where("channelId").`is`(channelId)), ChannelPinnedMessage::class.java)
    }

    /**
     * الحصول على قائمة القنوات
     */
    fun listChannels(groupId: String, includeArchived: Boolean = false): List<GroupChannelResponse> {
        val query = Query(Criteria.where("groupId").`is`(groupId))
        if (!includeArchived) {
            query.addCriteria(Criteria.where("archived").`is`(false))
        }
        query.with(org.springframework.data.domain.Sort.by("position", "createdAt"))

        return mongo.find(query, GroupChannel::class.java)
            .sortedBy { it.position }
            .map { it.toResponse() }
    }

    /**
     * إنشاء خيط جديد
     */
    fun createThread(
        groupId: String,
        channelId: String,
        actorId: UUID,
        request: CreateThreadRequest
    ): ChannelThreadResponse {
        val channel = mongo.findById(channelId, GroupChannel::class.java)
            ?: throw NoSuchElementException("Channel not found")
        require(channel.groupId == groupId) { "Channel does not belong to this group" }

        val id = "$channelId:${System.currentTimeMillis()}"
        val thread = ChannelThread(
            id = id,
            channelId = channelId,
            groupId = groupId,
            title = request.title.trim(),
            createdBy = actorId.toString(),
            createdAt = Instant.now()
        )

        mongo.save(thread)
        return thread.toResponse()
    }

    /**
     * الحصول على قائمة الخيوط
     */
    fun listThreads(groupId: String, channelId: String): List<ChannelThreadResponse> {
        return mongo.find(
            Query(Criteria.where("channelId").`is`(channelId).and("archived").`is`(false)),
            ChannelThread::class.java
        ).sortedByDescending { it.lastMessageAt ?: it.createdAt }
            .map { it.toResponse() }
    }

    /**
     * تثبيت رسالة في القناة
     */
    fun pinMessage(
        groupId: String,
        channelId: String,
        actorId: UUID,
        request: PinChannelMessageRequest
    ): ChannelPinnedMessageResponse {
        val channel = mongo.findById(channelId, GroupChannel::class.java)
            ?: throw NoSuchElementException("Channel not found")
        require(channel.groupId == groupId) { "Channel does not belong to this group" }

        val id = "$channelId:${System.currentTimeMillis()}"
        val pinnedMsg = ChannelPinnedMessage(
            id = id,
            channelId = channelId,
            groupId = groupId,
            messageUuid = request.messageUuid,
            senderId = "", // سيتم ملؤه من البيانات الفعلية
            pinnedBy = actorId.toString(),
            pinnedAt = Instant.now(),
            expiresAt = request.expiresAt
        )

        mongo.save(pinnedMsg)
        return pinnedMsg.toResponse()
    }

    /**
     * إلغاء تثبيت رسالة
     */
    fun unpinMessage(groupId: String, channelId: String, messageUuid: String) {
        val query = Query(
            Criteria.where("channelId").`is`(channelId)
                .and("messageUuid").`is`(messageUuid)
        )
        mongo.remove(query, ChannelPinnedMessage::class.java)
    }

    /**
     * الحصول على الرسائل المثبتة
     */
    fun listPinnedMessages(groupId: String, channelId: String): List<ChannelPinnedMessageResponse> {
        val now = Instant.now()
        return mongo.find(
            Query(Criteria.where("channelId").`is`(channelId)),
            ChannelPinnedMessage::class.java
        ).filter { it.expiresAt == null || it.expiresAt.isAfter(now) }
            .sortedByDescending { it.pinnedAt }
            .map { it.toResponse() }
    }

    /**
     * إعادة ترتيب القنوات
     */
    fun reorderChannels(groupId: String, channelIds: List<String>) {
        channelIds.forEachIndexed { index, channelId ->
            mongo.findById(channelId, GroupChannel::class.java)?.let { channel ->
                if (channel.groupId == groupId) {
                    mongo.save(channel.copy(position = index))
                }
            }
        }
    }

    private fun GroupChannel.toResponse() = GroupChannelResponse(
        id, groupId, name, description, channelType, private, createdBy, createdAt, updatedAt, messageCount, memberCount, topic, archived, position
    )

    private fun ChannelThread.toResponse() = ChannelThreadResponse(
        id, channelId, groupId, title, createdBy, createdAt, messageCount, lastMessageAt, archived, pinned
    )

    private fun ChannelPinnedMessage.toResponse() = ChannelPinnedMessageResponse(
        id, channelId, messageUuid, senderId, messagePreview, pinnedBy, pinnedAt, expiresAt
    )
}
