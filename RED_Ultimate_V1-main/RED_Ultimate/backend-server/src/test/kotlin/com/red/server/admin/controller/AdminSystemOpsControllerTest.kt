package com.red.server.admin.controller

import com.red.server.admin.service.AdminService
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.dao.DataAccessResourceFailureException
import org.springframework.data.redis.core.RedisCallback
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.security.core.Authentication
import java.util.UUID

/**
 * عقد `AdminSystemOpsController` — المصدر الحقيقي لصفحتي الكاش والهجرات.
 * الاختبارات هنا تحرس ثلاث دعاوى: (1) السجل يُقرأ من Flyway فعلًا بالترتيب،
 * (2) غياب الجدول/Redis يُعلن صراحةً بدل الرقم الصفري الكاذب أو 500،
 * (3) الإبطال لا يقبل نمطًا شاملًا ولا يمرّ بلا تدقيق.
 */
class AdminSystemOpsControllerTest {
    private val jdbc: JdbcTemplate = mock()
    private val redis: StringRedisTemplate = mock()
    private val adminService: AdminService = mock()
    private val controller = AdminSystemOpsController(jdbc, redis, adminService)

    private val auth: Authentication = mock<Authentication>().also {
        whenever(it.name).thenReturn(UUID.randomUUID().toString())
        whenever(it.principal).thenReturn("admin-tester")
    }

    // ━━━━━━━━━━━━━━━━ 🧬 Migrations ━━━━━━━━━━━━━━━━

    @Test
    fun `migrations reads real flyway history and reports failure count`() {
        whenever(jdbc.queryForList(any<String>())).thenReturn(
            listOf(
                mapOf(
                    "installed_rank" to 67, "version" to "67", "description" to "Sync Optimization",
                    "type" to "SQL", "script" to "V67__Sync_Optimization_And_Chat_State_Indexes.sql",
                    "checksum" to 123456, "installed_by" to "red", "installed_on" to "2026-09-26 08:00:00",
                    "execution_time" to 412, "success" to true
                ),
                mapOf(
                    "installed_rank" to 66, "version" to "66", "description" to "Sync Engine",
                    "type" to "SQL", "script" to "V66__Sync_Engine_And_Offline_Capabilities.sql",
                    "checksum" to 999, "installed_by" to "red", "installed_on" to "2026-09-26 07:59:00",
                    "execution_time" to 150, "success" to false
                )
            )
        )

        val body = controller.migrations().body!!

        assertEquals(true, body["available"])
        assertEquals(2, body["total"])
        assertEquals(1, body["failed"])
        assertEquals("67", body["currentVersion"])

        @Suppress("UNCHECKED_CAST")
        val rows = body["migrations"] as List<Map<String, Any?>>
        assertEquals("67", rows.first()["version"])
        assertEquals("SUCCESS", rows.first()["status"])
        assertEquals("FAILED", rows[1]["status"])
        assertEquals(412L, rows.first()["executionTime"])
        assertTrue((rows.first()["script"] as String).startsWith("V67__"))
    }

    @Test
    fun `missing flyway history is explicit instead of a 500`() {
        whenever(jdbc.queryForList(any<String>()))
            .thenThrow(DataAccessResourceFailureException("relation flyway_schema_history does not exist"))

        val response = controller.migrations()

        assertEquals(200, response.statusCode.value())
        assertEquals(false, response.body!!["available"])
        assertEquals(0, response.body!!["total"])
        assertFalse((response.body!!["reason"] as String).isBlank())
    }

    // ━━━━━━━━━━━━━━━━ 🗄️ Cache ━━━━━━━━━━━━━━━━

    @Test
    fun `redis outage degrades honestly to empty keys and zeroed memory`() {
        whenever(redis.execute(any<RedisCallback<List<Map<String, Any?>>>>()))
            .thenThrow(DataAccessResourceFailureException("redis down"))
        whenever(redis.execute(any<RedisCallback<Long>>()))
            .thenThrow(DataAccessResourceFailureException("redis down"))

        val body = controller.cacheOverview(null, 50).body!!

        @Suppress("UNCHECKED_CAST")
        val keys = body["keys"] as List<*>?
        assertTrue(keys.isNullOrEmpty())
        assertEquals(false, body["truncated"])

        @Suppress("UNCHECKED_CAST")
        val memory = body["memory"] as Map<String, Any?>
        assertEquals(0L, memory["used"])
        assertEquals(0L, memory["keys"])
        assertEquals(0.0, memory["fragmentation"])
    }

    // ━━━━━━━━━━━━━━━━ 🔒 الأمان والتدقيق ━━━━━━━━━━━━━━━━

    @Test
    fun `wildcard invalidation is rejected and never audited`() {
        val response = controller.invalidateCache(mapOf("pattern" to "*"), auth)

        assertEquals(400, response.statusCode.value())
        assertEquals(false, response.body!!["success"])
        verify(redis, never()).execute(any<RedisCallback<Long>>())
        verify(adminService, never()).recordAudit(
            anyOrNull(), anyOrNull(), any(), any(), anyOrNull(), anyOrNull(), anyOrNull(), any(), anyOrNull(), anyOrNull(), any()
        )
    }

    @Test
    fun `blank pattern is rejected`() {
        val response = controller.invalidateCache(mapOf("pattern" to "   "), auth)
        assertEquals(400, response.statusCode.value())
        verify(adminService, never()).recordAudit(
            anyOrNull(), anyOrNull(), any(), any(), anyOrNull(), anyOrNull(), anyOrNull(), any(), anyOrNull(), anyOrNull(), any()
        )
    }

    @Test
    fun `scoped invalidation reports deleted count and is always audited`() {
        whenever(redis.execute(any<RedisCallback<Long>>())).thenReturn(7L)

        val response = controller.invalidateCache(mapOf("pattern" to "session:*", "limit" to "100"), auth)

        assertEquals(200, response.statusCode.value())
        assertEquals(true, response.body!!["success"])
        assertEquals(7L, response.body!!["deleted"])
        assertEquals("session:*", response.body!!["pattern"])
        verify(adminService, times(1)).recordAudit(
            anyOrNull(),
            anyOrNull(),
            eq("CACHE_INVALIDATED"),
            eq("SYSTEM"),
            eq("CACHE"),
            eq("session:*"),
            anyOrNull(),
            any(),
            anyOrNull(),
            anyOrNull(),
            eq("WARNING")
        )
    }
}
