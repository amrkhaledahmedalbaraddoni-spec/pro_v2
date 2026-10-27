package com.red.server.groups

import org.springframework.data.annotation.Id
import org.springframework.data.mongodb.core.index.CompoundIndex
import org.springframework.data.mongodb.core.index.Indexed
import org.springframework.data.mongodb.core.mapping.Document
import org.springframework.data.mongodb.core.query.Criteria
import org.springframework.data.mongodb.core.query.Query
import org.springframework.data.mongodb.core.query.Update
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.stereotype.Service
import jakarta.validation.constraints.NotBlank
import java.time.Instant
import java.util.UUID

/**
 * نموذج Mute الأعضاء — منع الكتابة مؤقتاً
 */
@Document("group_mutes")
@CompoundIndex(name = "group_user_mute_unique", def = "{'groupId': 1, 'userId': 1}", unique = true)
data class GroupMute(
    @Id val id: String = "",
    @Indexed val groupId: String = "",
    @Indexed val userId: String = "",
    val redId: String = "",
    val reason: String? = null,
    val mutedBy: String = "",
    val mutedAt: Instant = Instant.now(),
    val expiresAt: Instant? = null,
    val permanent: Boolean = false
)

/**
 * نموذج تقييد الأعضاء — منع الإجراءات المحددة
 */
@Document("group_restrictions")
@CompoundIndex(name = "group_user_restrict_unique", def = "{'groupId': 1, 'userId': 1}", unique = true)
data class GroupRestriction(
    @Id val id: String = "",
    @Indexed val groupId: String = "",
    @Indexed val userId: String = "",
    val redId: String = "",
    val restrictions: Set<String> = emptySet(), // "POST", "MENTION", "SHARE_MEDIA", "REACT"
    val reason: String? = null,
    val restrictedBy: String = "",
    val restrictedAt: Instant = Instant.now(),
    val expiresAt: Instant? = null,
    val permanent: Boolean = false
)

/**
 * نموذج تنبيه العضو — تحذير قبل الحظر
 */
@Document("group_member_warnings")
data class GroupMemberWarning(
    @Id val id: String = "",
    @Indexed val groupId: String = "",
    @Indexed val userId: String = "",
    val redId: String = "",
    val reason: String = "",
    val warnedBy: String = "",
    val warnedAt: Instant = Instant.now(),
    val count: Int = 1
)

data class MuteGroupMemberRequest(
    @field:NotBlank val userId: String,
    val reason: String? = null,
    val durationSeconds: Long? = null,
    val permanent: Boolean = false
)

data class RestrictGroupMemberRequest(
    @field:NotBlank val userId: String,
    val restrictions: Set<String> = emptySet(),
    val reason: String? = null,
    val durationSeconds: Long? = null,
    val permanent: Boolean = false
)

data class WarnGroupMemberRequest(
    @field:NotBlank val userId: String,
    @field:NotBlank val reason: String
)

data class GroupMuteResponse(
    val id: String,
    val groupId: String,
    val userId: String,
    val redId: String,
    val reason: String?,
    val mutedAt: Instant,
    val expiresAt: Instant?,
    val permanent: Boolean
)

data class GroupRestrictionResponse(
    val id: String,
    val groupId: String,
    val userId: String,
    val redId: String,
    val restrictions: Set<String>,
    val reason: String?,
    val restrictedAt: Instant,
    val expiresAt: Instant?,
    val permanent: Boolean
)

data class GroupMemberWarningResponse(
    val id: String,
    val groupId: String,
    val userId: String,
    val redId: String,
    val reason: String,
    val warnedAt: Instant,
    val count: Int
)

/**
 * خدمة إدارة العضوية المتقدمة — mute, restrict, warn
 */
@Service
class MembershipManager(private val mongo: MongoTemplate) {

    /**
     * Mute عضو — منع الكتابة مؤقتاً أو دائماً
     */
    fun muteUser(
        groupId: String,
        userId: UUID,
        actorId: UUID,
        request: MuteGroupMemberRequest
    ): GroupMuteResponse {
        val id = "$groupId:$userId"
        val expiresAt = if (request.permanent) null
        else request.durationSeconds?.let { Instant.now().plusSeconds(it) }

        val mute = GroupMute(
            id = id,
            groupId = groupId,
            userId = userId.toString(),
            redId = request.userId,
            reason = request.reason,
            mutedBy = actorId.toString(),
            mutedAt = Instant.now(),
            expiresAt = expiresAt,
            permanent = request.permanent
        )

        mongo.save(mute)
        return mute.toResponse()
    }

    /**
     * إلغاء Mute عضو
     */
    fun unmuteUser(groupId: String, userId: UUID) {
        mongo.remove(Query(Criteria.where("id").`is`("$groupId:$userId")), GroupMute::class.java)
    }

    /**
     * الحصول على قائمة الأعضاء المكتومين
     */
    fun listMutes(groupId: String): List<GroupMuteResponse> {
        val now = Instant.now()
        return mongo.find(
            Query(Criteria.where("groupId").`is`(groupId)),
            GroupMute::class.java
        ).filter { it.permanent || (it.expiresAt != null && it.expiresAt.isAfter(now)) }
            .sortedByDescending { it.mutedAt }
            .map { it.toResponse() }
    }

    /**
     * فحص هل العضو مكتوم
     */
    fun isMuted(groupId: String, userId: UUID): Boolean {
        val mute = mongo.findById("$groupId:$userId", GroupMute::class.java) ?: return false
        if (mute.permanent) return true
        if (mute.expiresAt != null && mute.expiresAt.isBefore(Instant.now())) {
            runCatching { mongo.remove(Query(Criteria.where("id").`is`(mute.id)), GroupMute::class.java) }
            return false
        }
        return true
    }

    /**
     * تقييد عضو — منع إجراءات محددة
     */
    fun restrictUser(
        groupId: String,
        userId: UUID,
        actorId: UUID,
        request: RestrictGroupMemberRequest
    ): GroupRestrictionResponse {
        val id = "$groupId:$userId"
        val expiresAt = if (request.permanent) null
        else request.durationSeconds?.let { Instant.now().plusSeconds(it) }

        val restriction = GroupRestriction(
            id = id,
            groupId = groupId,
            userId = userId.toString(),
            redId = request.userId,
            restrictions = request.restrictions,
            reason = request.reason,
            restrictedBy = actorId.toString(),
            restrictedAt = Instant.now(),
            expiresAt = expiresAt,
            permanent = request.permanent
        )

        mongo.save(restriction)
        return restriction.toResponse()
    }

    /**
     * إلغاء تقييد عضو
     */
    fun unrestrictUser(groupId: String, userId: UUID) {
        mongo.remove(Query(Criteria.where("id").`is`("$groupId:$userId")), GroupRestriction::class.java)
    }

    /**
     * الحصول على قائمة الأعضاء المقيدين
     */
    fun listRestrictions(groupId: String): List<GroupRestrictionResponse> {
        val now = Instant.now()
        return mongo.find(
            Query(Criteria.where("groupId").`is`(groupId)),
            GroupRestriction::class.java
        ).filter { it.permanent || (it.expiresAt != null && it.expiresAt.isAfter(now)) }
            .sortedByDescending { it.restrictedAt }
            .map { it.toResponse() }
    }

    /**
     * فحص هل لدى العضو تقييد محدد
     */
    fun hasRestriction(groupId: String, userId: UUID, restriction: String): Boolean {
        val restrict = mongo.findById("$groupId:$userId", GroupRestriction::class.java) ?: return false
        if (restrict.permanent && restrict.restrictions.contains(restriction)) return true
        if (restrict.expiresAt != null && restrict.expiresAt.isBefore(Instant.now())) {
            runCatching { mongo.remove(Query(Criteria.where("id").`is`(restrict.id)), GroupRestriction::class.java) }
            return false
        }
        return restrict.restrictions.contains(restriction)
    }

    /**
     * تنبيه عضو — تحذير قبل الحظر
     */
    fun warnMember(
        groupId: String,
        userId: UUID,
        actorId: UUID,
        request: WarnGroupMemberRequest
    ): GroupMemberWarningResponse {
        val id = "$groupId:$userId"
        val existing = mongo.findById(id, GroupMemberWarning::class.java)

        val warning = if (existing != null) {
            existing.copy(count = existing.count + 1)
        } else {
            GroupMemberWarning(
                id = id,
                groupId = groupId,
                userId = userId.toString(),
                redId = request.userId,
                reason = request.reason,
                warnedBy = actorId.toString(),
                warnedAt = Instant.now()
            )
        }

        mongo.save(warning)
        return warning.toResponse()
    }

    /**
     * الحصول على عدد التحذيرات
     */
    fun getWarningCount(groupId: String, userId: UUID): Int {
        return mongo.findById("$groupId:$userId", GroupMemberWarning::class.java)?.count ?: 0
    }

    /**
     * حذف التحذيرات
     */
    fun clearWarnings(groupId: String, userId: UUID) {
        mongo.remove(Query(Criteria.where("id").`is`("$groupId:$userId")), GroupMemberWarning::class.java)
    }

    /**
     * الحصول على قائمة التحذيرات
     */
    fun listWarnings(groupId: String): List<GroupMemberWarningResponse> {
        return mongo.find(
            Query(Criteria.where("groupId").`is`(groupId)),
            GroupMemberWarning::class.java
        ).sortedByDescending { it.warnedAt }
            .map { it.toResponse() }
    }

    private fun GroupMute.toResponse() = GroupMuteResponse(
        id, groupId, userId, redId, reason, mutedAt, expiresAt, permanent
    )

    private fun GroupRestriction.toResponse() = GroupRestrictionResponse(
        id, groupId, userId, redId, restrictions, reason, restrictedAt, expiresAt, permanent
    )

    private fun GroupMemberWarning.toResponse() = GroupMemberWarningResponse(
        id, groupId, userId, redId, reason, warnedAt, count
    )
}
