package com.red.server

import com.red.server.admin.model.AdminAuditLog
import com.red.server.admin.repository.AdminAuditLogRepository
import com.red.server.agents.RedServerAgentController
import com.red.server.agents.ServerAgentRequest
import com.red.server.auth.LowStockDevice
import com.red.server.auth.OneTimePreKeyService
import com.red.server.auth.repository.UserDeviceRepository
import com.red.server.services.MasterStatsService
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.core.RowMapper
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.authority.SimpleGrantedAuthority
import java.util.UUID

/**
 * 2026-09-26: سد الثغرات — صلاحيات الإرسال + تنبيه مخزون المفاتيح.
 */
class AdminSecurityGapTest {

    private fun auth(role: String) = UsernamePasswordAuthenticationToken(
        UUID.randomUUID().toString(), null, listOf(SimpleGrantedAuthority("ROLE_$role"))
    )

    private fun dispatchController(): RedServerAgentController {
        val audit: AdminAuditLogRepository = mock()
        whenever(audit.save(any())).thenAnswer { (it.arguments[0] as AdminAuditLog) }
        return RedServerAgentController(audit, mock<MasterStatsService>())
    }

    @Test
    fun `dispatch rejects non-admin with 403`() {
        val res = dispatchController().dispatchAgent(
            ServerAgentRequest("g1", "a1", "hello"), auth("USER")
        )
        assertEquals(403, res.statusCode.value())
        assertEquals("ADMIN_ONLY", (res.body as Map<*, *>)["error"])
    }

    @Test
    fun `dispatch rejects unauthenticated with 403`() {
        val res = dispatchController().dispatchAgent(
            ServerAgentRequest("g1", "a1", "hello"), null
        )
        assertEquals(403, res.statusCode.value())
    }

    @Test
    fun `dispatch allows admin with valid fields`() {
        val res = dispatchController().dispatchAgent(
            ServerAgentRequest("g1", "a1", "hello"), auth("ADMIN")
        )
        assertEquals(200, res.statusCode.value())
    }

    @Test
    fun `dispatch validates blank fields with 400 for admin`() {
        val res = dispatchController().dispatchAgent(
            ServerAgentRequest("", "a1", "hello"), auth("ADMIN")
        )
        assertEquals(400, res.statusCode.value())
    }

    @Test
    fun `lowStockDevices returns rows from jdbc`() {
        val jdbc: JdbcTemplate = mock()
        val d1 = UUID.randomUUID()
        val u1 = UUID.randomUUID()
        @Suppress("UNCHECKED_CAST")
        whenever(
            jdbc.query(any<String>(), any<RowMapper<LowStockDevice>>(), any<Array<out Any>>())
        ).thenReturn(listOf(LowStockDevice(d1, u1, 3, 25)))
        val service = OneTimePreKeyService(jdbc, mock<UserDeviceRepository>())
        val rows = service.lowStockDevices(20)
        assertEquals(1, rows.size)
        assertEquals(d1, rows[0].deviceId)
        assertEquals(3, rows[0].ecAvailable)
        assertEquals(25, rows[0].kyberAvailable)
    }
}
