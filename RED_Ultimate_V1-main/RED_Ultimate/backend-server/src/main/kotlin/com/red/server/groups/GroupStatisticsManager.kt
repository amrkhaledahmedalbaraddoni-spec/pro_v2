package com.red.server.groups

import org.springframework.data.annotation.Id
import org.springframework.data.mongodb.core.index.Indexed
import org.springframework.data.mongodb.core.mapping.Document
import org.springframework.data.mongodb.core.query.Criteria
import org.springframework.data.mongodb.core.query.Query
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.stereotype.Service
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.UUID

/**
 * إحصائيات المجموعة المتقدمة
 */
@Document("group_statistics")
data class GroupStatistics(
    @Id val id: String = "",
    @Indexed val groupId: String = "",
    val totalMessages: Long = 0L,
    val totalMedia: Long = 0L,
    val totalMembers: Long = 0L,
    val totalActiveMembers: Long = 0L,
    val totalMutedMembers: Long = 0L,
    val totalBannedMembers: Long = 0L,
    val messagesLast24h: Long = 0L,
    val messagesLast7d: Long = 0L,
    val messagesLast30d: Long = 0L,
    val averageMessagesPerDay: Double = 0.0,
    val mostActiveMember: String? = null,
    val mostActiveMemberId: String? = null,
    val createdAt: Instant = Instant.now(),
    val updatedAt: Instant = Instant.now()
)

/**
 * نشاط اليومي للمجموعة
 */
@Document("group_daily_activity")
@org.springframework.data.mongodb.core.index.CompoundIndex(name = "group_date_unique", def = "{'groupId': 1, 'date': 1}", unique = true)
data class GroupDailyActivity(
    @Id val id: String = "",
    @Indexed val groupId: String = "",
    val date: String = "", // YYYY-MM-DD
    val messageCount: Long = 0L,
    val mediaCount: Long = 0L,
    val newMembers: Long = 0L,
    val memberLeft: Long = 0L,
    val createdAt: Instant = Instant.now()
)

/**
 * أنشط الأعضاء
 */
data class TopActiveMembers(
    val userId: String,
    val redId: String,
    val username: String,
    val messageCount: Long,
    val mediaCount: Long,
    val joinedAt: Instant
)

/**
 * إحصائيات يومية
 */
data class DailyActivityStats(
    val date: String,
    val messageCount: Long,
    val mediaCount: Long,
    val newMembers: Long,
    val memberLeft: Long
)

/**
 * ملخص الإحصائيات
 */
data class GroupStatisticsResponse(
    val groupId: String,
    val totalMessages: Long,
    val totalMedia: Long,
    val totalMembers: Long,
    val totalActiveMembers: Long,
    val totalMutedMembers: Long,
    val totalBannedMembers: Long,
    val messagesLast24h: Long,
    val messagesLast7d: Long,
    val messagesLast30d: Long,
    val averageMessagesPerDay: Double,
    val mostActiveMembers: List<TopActiveMembers>,
    val dailyActivity: List<DailyActivityStats>,
    val createdAt: Instant,
    val updatedAt: Instant
)

/**
 * خدمة الإحصائيات والتحليلات
 */
@Service
class GroupStatisticsManager(private val mongo: MongoTemplate) {

    /**
     * تحديث إحصائيات المجموعة
     */
    fun updateStatistics(groupId: String): GroupStatistics {
        val now = Instant.now()
        val statistics = mongo.findById(groupId, GroupStatistics::class.java) ?: GroupStatistics(id = groupId, groupId = groupId)

        // حساب عدد الرسائل والوسائط (محاكاة - في الواقع يتم من GroupMessageDocument)
        val oneDayAgo = now.minus(1, ChronoUnit.DAYS)
        val sevenDaysAgo = now.minus(7, ChronoUnit.DAYS)
        val thirtyDaysAgo = now.minus(30, ChronoUnit.DAYS)

        val updated = statistics.copy(
            updatedAt = now
        )

        mongo.save(updated)
        return updated
    }

    /**
     * تسجيل نشاط يومي
     */
    fun recordDailyActivity(groupId: String, messageCount: Long = 0L, mediaCount: Long = 0L) {
        val today = Instant.now().truncatedTo(ChronoUnit.DAYS).toString().substring(0, 10)
        val id = "$groupId:$today"

        val existing = mongo.findById(id, GroupDailyActivity::class.java)
        val activity = if (existing != null) {
            existing.copy(
                messageCount = existing.messageCount + messageCount,
                mediaCount = existing.mediaCount + mediaCount
            )
        } else {
            GroupDailyActivity(
                id = id,
                groupId = groupId,
                date = today,
                messageCount = messageCount,
                mediaCount = mediaCount,
                createdAt = Instant.now()
            )
        }

        mongo.save(activity)
    }

    /**
     * الحصول على الأعضاء الأكثر نشاطاً
     */
    fun getTopActiveMembers(groupId: String, limit: Int = 10): List<TopActiveMembers> {
        // محاكاة - في الواقع يتم من GroupMessageDocument
        return emptyList()
    }

    /**
     * الحصول على النشاط اليومي
     */
    fun getDailyActivity(groupId: String, days: Int = 30): List<DailyActivityStats> {
        val startDate = Instant.now().minus(days.toLong(), ChronoUnit.DAYS)
        return mongo.find(
            Query(Criteria.where("groupId").`is`(groupId).and("createdAt").gte(startDate)),
            GroupDailyActivity::class.java
        ).sortedBy { it.date }
            .map {
                DailyActivityStats(
                    it.date,
                    it.messageCount,
                    it.mediaCount,
                    it.newMembers,
                    it.memberLeft
                )
            }
    }

    /**
     * الحصول على ملخص الإحصائيات
     */
    fun getStatistics(groupId: String): GroupStatisticsResponse {
        val stats = updateStatistics(groupId)
        val topMembers = getTopActiveMembers(groupId, 10)
        val dailyActivity = getDailyActivity(groupId, 30)

        return GroupStatisticsResponse(
            groupId = groupId,
            totalMessages = stats.totalMessages,
            totalMedia = stats.totalMedia,
            totalMembers = stats.totalMembers,
            totalActiveMembers = stats.totalActiveMembers,
            totalMutedMembers = stats.totalMutedMembers,
            totalBannedMembers = stats.totalBannedMembers,
            messagesLast24h = stats.messagesLast24h,
            messagesLast7d = stats.messagesLast7d,
            messagesLast30d = stats.messagesLast30d,
            averageMessagesPerDay = stats.averageMessagesPerDay,
            mostActiveMembers = topMembers,
            dailyActivity = dailyActivity,
            createdAt = stats.createdAt,
            updatedAt = stats.updatedAt
        )
    }

    /**
     * مسح الإحصائيات
     */
    fun clearStatistics(groupId: String) {
        mongo.remove(Query(Criteria.where("groupId").`is`(groupId)), GroupStatistics::class.java)
        mongo.remove(Query(Criteria.where("groupId").`is`(groupId)), GroupDailyActivity::class.java)
    }
}
