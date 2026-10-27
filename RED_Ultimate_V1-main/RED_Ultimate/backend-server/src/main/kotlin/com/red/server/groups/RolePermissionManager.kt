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
 * نموذج دور مخصص — أدوار إضافية بجانب OWNER, ADMIN, MODERATOR, MEMBER
 */
@Document("group_custom_roles")
@CompoundIndex(name = "group_role_unique", def = "{'groupId': 1, 'name': 1}", unique = true)
data class GroupCustomRole(
    @Id val id: String = "",
    @Indexed val groupId: String = "",
    val name: String = "",
    val description: String? = null,
    val permissions: Set<String> = emptySet(),
    val color: String? = null,
    val createdAt: Instant = Instant.now(),
    val createdBy: String = ""
)

/**
 * نموذج الصلاحيات — أذونات قابلة للتخصيص للأدوار
 */
@Document("group_role_permissions")
data class GroupRolePermissions(
    @Id val id: String = "",
    @Indexed val groupId: String = "",
    val role: String = "", // OWNER, ADMIN, MODERATOR, MEMBER, أو دور مخصص
    val permissions: Set<String> = emptySet(),
    val updatedAt: Instant = Instant.now(),
    val updatedBy: String = ""
)

data class CreateCustomRoleRequest(
    @field:NotBlank val name: String,
    val description: String? = null,
    val permissions: Set<String> = emptySet(),
    val color: String? = null
)

data class UpdateCustomRoleRequest(
    val description: String? = null,
    val permissions: Set<String>? = null,
    val color: String? = null
)

data class UpdateRolePermissionsRequest(
    val role: String,
    val permissions: Set<String>
)

data class GroupCustomRoleResponse(
    val id: String,
    val groupId: String,
    val name: String,
    val description: String?,
    val permissions: Set<String>,
    val color: String?,
    val createdAt: Instant
)

data class GroupRolePermissionsResponse(
    val id: String,
    val groupId: String,
    val role: String,
    val permissions: Set<String>,
    val updatedAt: Instant
)

/**
 * قائمة الصلاحيات الممكنة في المجموعة
 */
object GroupPermissions {
    const val SEND_MESSAGES = "SEND_MESSAGES"
    const val SEND_MEDIA = "SEND_MEDIA"
    const val EDIT_MESSAGES = "EDIT_MESSAGES"
    const val DELETE_OWN_MESSAGES = "DELETE_OWN_MESSAGES"
    const val DELETE_ANY_MESSAGES = "DELETE_ANY_MESSAGES"
    const val PIN_MESSAGES = "PIN_MESSAGES"
    const val UNPIN_MESSAGES = "UNPIN_MESSAGES"
    const val MENTION_EVERYONE = "MENTION_EVERYONE"
    const val ADD_MEMBERS = "ADD_MEMBERS"
    const val REMOVE_MEMBERS = "REMOVE_MEMBERS"
    const val EDIT_GROUP_INFO = "EDIT_GROUP_INFO"
    const val MANAGE_ROLES = "MANAGE_ROLES"
    const val MANAGE_INVITES = "MANAGE_INVITES"
    const val MANAGE_POLLS = "MANAGE_POLLS"
    const val CREATE_CHANNELS = "CREATE_CHANNELS"
    const val DELETE_CHANNELS = "DELETE_CHANNELS"
    const val MANAGE_MUTES = "MANAGE_MUTES"
    const val MANAGE_RESTRICTIONS = "MANAGE_RESTRICTIONS"
    const val MANAGE_BANS = "MANAGE_BANS"
    const val VIEW_ANALYTICS = "VIEW_ANALYTICS"
    const val CHANGE_SETTINGS = "CHANGE_SETTINGS"
    const val CREATE_THREADS = "CREATE_THREADS"
    const val USE_VOICE_CALL = "USE_VOICE_CALL"

    fun defaultPermissionsFor(role: String): Set<String> = when (role) {
        "OWNER" -> setOf(
            SEND_MESSAGES, SEND_MEDIA, EDIT_MESSAGES, DELETE_OWN_MESSAGES, DELETE_ANY_MESSAGES,
            PIN_MESSAGES, UNPIN_MESSAGES, MENTION_EVERYONE, ADD_MEMBERS, REMOVE_MEMBERS,
            EDIT_GROUP_INFO, MANAGE_ROLES, MANAGE_INVITES, MANAGE_POLLS, CREATE_CHANNELS,
            DELETE_CHANNELS, MANAGE_MUTES, MANAGE_RESTRICTIONS, MANAGE_BANS, VIEW_ANALYTICS,
            CHANGE_SETTINGS, CREATE_THREADS, USE_VOICE_CALL
        )
        "ADMIN" -> setOf(
            SEND_MESSAGES, SEND_MEDIA, EDIT_MESSAGES, DELETE_OWN_MESSAGES, DELETE_ANY_MESSAGES,
            PIN_MESSAGES, UNPIN_MESSAGES, MENTION_EVERYONE, ADD_MEMBERS, REMOVE_MEMBERS,
            EDIT_GROUP_INFO, MANAGE_INVITES, MANAGE_POLLS, CREATE_CHANNELS, MANAGE_MUTES,
            MANAGE_RESTRICTIONS, MANAGE_BANS, VIEW_ANALYTICS, CREATE_THREADS, USE_VOICE_CALL
        )
        "MODERATOR" -> setOf(
            SEND_MESSAGES, SEND_MEDIA, EDIT_MESSAGES, DELETE_OWN_MESSAGES, DELETE_ANY_MESSAGES,
            PIN_MESSAGES, MENTION_EVERYONE, ADD_MEMBERS, MANAGE_MUTES, MANAGE_RESTRICTIONS,
            CREATE_THREADS, USE_VOICE_CALL
        )
        "MEMBER" -> setOf(
            SEND_MESSAGES, SEND_MEDIA, EDIT_MESSAGES, DELETE_OWN_MESSAGES,
            CREATE_THREADS, USE_VOICE_CALL
        )
        else -> emptySet()
    }
}

/**
 * خدمة إدارة الأدوار والصلاحيات المتقدمة
 */
@Service
class RolePermissionManager(private val mongo: MongoTemplate) {

    /**
     * إنشاء دور مخصص
     */
    fun createCustomRole(
        groupId: String,
        actorId: UUID,
        request: CreateCustomRoleRequest
    ): GroupCustomRoleResponse {
        val name = request.name.trim()
        require(name.length in 2..50) { "Role name must be 2-50 characters" }

        val id = "$groupId:${System.currentTimeMillis()}"
        val role = GroupCustomRole(
            id = id,
            groupId = groupId,
            name = name,
            description = request.description?.trim(),
            permissions = request.permissions,
            color = request.color,
            createdAt = Instant.now(),
            createdBy = actorId.toString()
        )

        mongo.save(role)
        return role.toResponse()
    }

    /**
     * تحديث دور مخصص
     */
    fun updateCustomRole(
        groupId: String,
        roleId: String,
        request: UpdateCustomRoleRequest
    ): GroupCustomRoleResponse {
        val role = mongo.findById(roleId, GroupCustomRole::class.java)
            ?: throw NoSuchElementException("Custom role not found")
        require(role.groupId == groupId) { "Role does not belong to this group" }

        val updated = role.copy(
            description = request.description ?: role.description,
            permissions = request.permissions ?: role.permissions,
            color = request.color ?: role.color
        )

        mongo.save(updated)
        return updated.toResponse()
    }

    /**
     * حذف دور مخصص
     */
    fun deleteCustomRole(groupId: String, roleId: String) {
        val role = mongo.findById(roleId, GroupCustomRole::class.java)
            ?: throw NoSuchElementException("Custom role not found")
        require(role.groupId == groupId) { "Role does not belong to this group" }

        mongo.remove(Query(Criteria.where("id").`is`(roleId)), GroupCustomRole::class.java)
    }

    /**
     * الحصول على قائمة الأدوار المخصصة
     */
    fun listCustomRoles(groupId: String): List<GroupCustomRoleResponse> {
        return mongo.find(
            Query(Criteria.where("groupId").`is`(groupId)),
            GroupCustomRole::class.java
        ).sortedByDescending { it.createdAt }
            .map { it.toResponse() }
    }

    /**
     * تحديث صلاحيات دور
     */
    fun updateRolePermissions(
        groupId: String,
        actorId: UUID,
        request: UpdateRolePermissionsRequest
    ): GroupRolePermissionsResponse {
        val id = "$groupId:${request.role}"

        val permissions = GroupRolePermissions(
            id = id,
            groupId = groupId,
            role = request.role,
            permissions = request.permissions,
            updatedAt = Instant.now(),
            updatedBy = actorId.toString()
        )

        mongo.save(permissions)
        return permissions.toResponse()
    }

    /**
     * الحصول على صلاحيات دور محدد
     */
    fun getRolePermissions(groupId: String, role: String): GroupRolePermissionsResponse {
        val id = "$groupId:$role"
        val existing = mongo.findById(id, GroupRolePermissions::class.java)

        return if (existing != null) {
            existing.toResponse()
        } else {
            val defaultPerms = GroupPermissions.defaultPermissionsFor(role)
            val permsDoc = GroupRolePermissions(
                id = id,
                groupId = groupId,
                role = role,
                permissions = defaultPerms,
                updatedAt = Instant.now()
            )
            mongo.save(permsDoc)
            permsDoc.toResponse()
        }
    }

    /**
     * فحص هل يملك العضو صلاحية معينة
     */
    fun hasPermission(groupId: String, role: String, permission: String): Boolean {
        val perms = getRolePermissions(groupId, role)
        return perms.permissions.contains(permission)
    }

    /**
     * الحصول على جميع صلاحيات الدور
     */
    fun listPermissions(groupId: String, role: String): Set<String> {
        return getRolePermissions(groupId, role).permissions
    }

    /**
     * إعادة تعيين الصلاحيات الافتراضية لدور
     */
    fun resetPermissionsToDefault(groupId: String, role: String): GroupRolePermissionsResponse {
        val defaultPerms = GroupPermissions.defaultPermissionsFor(role)
        return updateRolePermissions(
            groupId,
            UUID.randomUUID(),
            UpdateRolePermissionsRequest(role, defaultPerms)
        )
    }

    private fun GroupCustomRole.toResponse() = GroupCustomRoleResponse(
        id, groupId, name, description, permissions, color, createdAt
    )

    private fun GroupRolePermissions.toResponse() = GroupRolePermissionsResponse(
        id, groupId, role, permissions, updatedAt
    )
}
