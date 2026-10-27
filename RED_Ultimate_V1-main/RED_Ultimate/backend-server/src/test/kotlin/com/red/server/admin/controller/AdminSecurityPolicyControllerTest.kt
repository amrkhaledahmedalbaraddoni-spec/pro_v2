package com.red.server.admin.controller

import tools.jackson.databind.ObjectMapper
import com.red.server.admin.model.AdminSystemConfig
import com.red.server.admin.repository.AdminSystemConfigRepository
import com.red.server.admin.service.AdminService
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.argThat
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.security.core.Authentication
import java.util.Optional
import java.util.UUID

/**
 * عقد `AdminSecurityPolicyController` — سياسة MFA وإعداد SSO فوق مخزن V68.
 * يُغطي: (1) القراءة تعيد الافتراضات الصريحة، (2) الكتابة تتحقق وتُدقِّق،
 * (3) رفض الطرق الفارغة/النافذة خارج المدى، (4) issuer غير HTTPS يُرفض،
 * (5) فشل الاكتشاف يُعاد صراحةً بلا ادعاء نجاح.
 */
class AdminSecurityPolicyControllerTest {
    private val configRepo: AdminSystemConfigRepository = mock()
    private val adminService: AdminService = mock()
    private val controller = AdminSecurityPolicyController(configRepo, adminService, ObjectMapper())

    private val adminId = UUID.randomUUID()
    private val auth: Authentication = mock<Authentication>().also {
        whenever(it.name).thenReturn(adminId.toString())
        whenever(it.principal).thenReturn("admin-tester")
    }

    @Test
    fun `mfa policy returns explicit defaults when store empty`() {
        whenever(configRepo.findByConfigKey(any())).thenReturn(null)

        val body = controller.getMfaPolicy().body!!

        assertEquals(true, body["enforceForAdmins"])
        assertEquals(false, body["enforceForAll"])
        assertEquals(1, body["totpWindow"])
        @Suppress("UNCHECKED_CAST")
        val methods = body["allowedMethods"] as List<String>
        assertEquals(listOf("TOTP", "WEB_AUTHN", "BACKUP_CODES"), methods)
    }

    @Test
    fun `mfa policy update persists keys and audits`() {
        whenever(configRepo.findByConfigKey(any())).thenReturn(null)
        whenever(configRepo.save(any<AdminSystemConfig>())).thenAnswer { it.getArgument<AdminSystemConfig>(0) }

        val response = controller.putMfaPolicy(
            mapOf(
                "enforceForAdmins" to true,
                "enforceForAll" to false,
                "allowedMethods" to listOf("TOTP", "BACKUP_CODES"),
                "totpWindow" to 2
            ),
            auth
        )

        assertEquals(200, response.statusCode.value())
        verify(configRepo).save(argThat { entry -> entry.configKey == "mfa.enforce_for_admins" && entry.configValue == "true" })
        verify(configRepo).save(argThat { entry -> entry.configKey == "mfa.allowed_methods" && entry.configValue.contains("TOTP") })
        verify(adminService).recordAudit(
            eq(adminId), eq("admin-tester"), eq("MFA_POLICY_UPDATED"), eq("SYSTEM"),
            eq("SECURITY_POLICY"), eq("mfa.policy"), any(), eq(emptyMap<String, Any?>()), anyOrNull(), anyOrNull(), eq("WARNING")
        )
    }

    @Test
    fun `mfa policy update rejects empty methods and window out of range`() {
        val empty = controller.putMfaPolicy(mapOf("allowedMethods" to emptyList<String>()), auth)
        assertEquals(400, empty.statusCode.value())

        val badWindow = controller.putMfaPolicy(
            mapOf("allowedMethods" to listOf("TOTP"), "totpWindow" to 99),
            auth
        )
        assertEquals(400, badWindow.statusCode.value())
        assertTrue((badWindow.body as Map<*, *>)["error"] == "TOTP_WINDOW_OUT_OF_RANGE")
    }

    @Test
    fun `sso config get never leaks client secret`() {
        whenever(configRepo.findByConfigKey("sso.oidc.enabled")).thenReturn(
            AdminSystemConfig(configKey = "sso.oidc.enabled", configValue = "true")
        )
        whenever(configRepo.findByConfigKey("sso.oidc.issuer")).thenReturn(
            AdminSystemConfig(configKey = "sso.oidc.issuer", configValue = "https://id.example.com")
        )
        whenever(configRepo.findByConfigKey("sso.oidc.client_secret")).thenReturn(
            AdminSystemConfig(configKey = "sso.oidc.client_secret", configValue = "super-secret")
        )

        val raw = controller.getSsoConfig().body!!
        val serialized = ObjectMapper().writeValueAsString(raw)

        assertTrue(!serialized.contains("super-secret"), "client secret must never be serialized")
        assertEquals(true, ((raw["oidc"] as Map<*, *>)["enabled"]))
    }

    @Test
    fun `sso config update rejects non-https issuer`() {
        val response = controller.putSsoConfig(mapOf("issuer" to "http://id.example.com"), auth)

        assertEquals(400, response.statusCode.value())
        assertTrue((response.body as Map<*, *>)["error"] == "ISSUER_MUST_BE_HTTPS")
    }

    @Test
    fun `sso test rejects non-https issuer before any network call`() {
        val response = controller.testSso("bad", mapOf("issuer" to "http://id.example.com"), auth)

        assertEquals(400, response.statusCode.value())
        assertTrue((response.body as Map<*, *>)["reachable"] == false)
    }

    @Test
    fun `sso test reports unreachable explicitly for dead issuer`() {
        val response = controller.testSso("dead", mapOf("issuer" to "https://sso.invalid.nonexistent.example"), auth)
            .body as Map<*, *>

        assertEquals(false, response["success"])
        assertEquals(false, response["reachable"])
        assertEquals("DISCOVERY_UNREACHABLE", response["error"])
    }

    private fun eq(value: String): String = org.mockito.kotlin.eq(value)
}
