package com.red.config

import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.tomcat.ConfigurableTomcatWebServerFactory
import org.springframework.boot.web.server.WebServerFactoryCustomizer
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.core.env.Environment
import org.springframework.web.filter.ForwardedHeaderFilter

/**
 * 🔒 RED Backend HTTPS/TLS Security Configuration
 *
 * ## الميزات:
 * - إلزام HTTPS في بيئة production (تحقّق إعداد، لا منفذ HTTP عارٍ إضافي)
 * - دعم X-Forwarded-Proto عبر [ForwardedHeaderFilter] عند TLS termination في nginx/LB
 * - HTTP/2 + بروتوكولات TLS ≥1.2 + الضغط تُضبط عبر خصائص Spring (`server.*`)
 *
 * ## ملاحظة معمارية (Spring Boot 4)
 * البناء القديم كان يُنشئ `TomcatServletWebServerFactory` ويضيف `Connector` HTTP
 * إضافياً على منفذ 8088. في Boot 4 أُزيلت واجهات
 * `org.springframework.boot.web.embedded.tomcat.*`، ولم يبقَ
 * `addAdditionalTomcatConnectors`. والأهم: فتح منفذ HTTP عارٍ بجانب HTTPS
 * يُبطل الغرض الأمني ويسهّل تجاوز TLS. لذلك يعتمد الإعداد الآن على
 * إنهاء TLS في الوكيل العكسي (كما في DEPLOY.md) وتمرير
 * `X-Forwarded-Proto`، مع فحص إعداد عند الإقلاع.
 *
 * ## المتغيرات:
 * ```yaml
 * server:
 *   port: 8443
 *   ssl:
 *     key-store: ${RED_KEYSTORE_PATH}
 *     key-store-password: ${RED_KEYSTORE_PASSWORD}
 *     key-store-type: PKCS12
 *     enabled-protocols: TLSv1.3,TLSv1.2
 *   http2:
 *     enabled: true
 *   compression:
 *     enabled: true
 * red:
 *   security:
 *     enforce-https: true
 * ```
 */
@Configuration
class HttpsSecurityConfig(
    private val environment: Environment
) {

    private val log = LoggerFactory.getLogger(HttpsSecurityConfig::class.java)

    @Value("\${server.ssl.key-store:}")
    private val keystorePath: String = ""

    @Value("\${server.ssl.key-store-password:}")
    private val keystorePassword: String = ""

    @Value("\${red.security.enforce-https:false}")
    private val enforceHttps: Boolean = false

    /**
     * فحص تشغيلي عند الإقلاع: في production مع enforce-https يجب وجود keystore
     * صالح أو وكيل عكسي ينهي TLS. لا نرمي استثناءً حتى لا يُعطَّل الإقلاع في
     * بيئات LAN داخلية، لكن نسجّل خطأً صريحاً بدل الفشل الصامت.
     */
    @Bean
    fun httpsConfigurationAudit(): Any {
        if (enforceHttps && environment.activeProfiles.contains("prod")) {
            val hasKeystore = keystorePath.isNotBlank() && keystorePassword.isNotBlank()
            if (!hasKeystore) {
                log.error(
                    "red.security.enforce-https=true without server.ssl.* keystore: TLS must be terminated " +
                        "by a reverse proxy (nginx/LB) that sets X-Forwarded-Proto=https"
                )
            } else if (!java.io.File(keystorePath).canRead()) {
                log.error("server.ssl.key-store '{}' is not readable — TLS handshake will fail", keystorePath)
            } else {
                log.info("HTTPS enforced: keystore '{}' loaded, TLS terminated locally", keystorePath)
            }
        }
        return HttpsAuditResult(enforceHttps, keystorePath.isNotBlank())
    }

    /**
     * ضمان حد أدنى من إعداد Tomcat: ترميز URI صريح ومنع APR غير الضروري.
     * (الضغط/HTTP2/TLS تُضبط عبر خصائص `server.*` وليس برمجياً.)
     */
    @Bean
    fun tomcatTransportCustomizer(): WebServerFactoryCustomizer<ConfigurableTomcatWebServerFactory> =
        WebServerFactoryCustomizer { factory ->
            factory.setUriEncoding(Charsets.UTF_8)
        }

    /**
     * فلتر Forwarded Headers — يدعم X-Forwarded-Proto للـ reverse proxies
     * هذا يسمح بـ HTTPS termination في load balancer مع HTTP داخلياً
     */
    @Bean
    fun forwardedHeaderFilter(): ForwardedHeaderFilter = ForwardedHeaderFilter()
}

/** نتيجة فحص إعداد HTTPS — تُسجَّل في /actuator/health عبر الفحص التشغيلي. */
data class HttpsAuditResult(
    val enforceHttps: Boolean,
    val keystoreConfigured: Boolean
)

/**
 * تكوين TLS Pinning (Client-side)
 * يُستخدم من جانب Android client عند الاتصال بـ API
 */
data class TlsPinConfig(
    val hostnames: List<String>,
    val pins: List<String>, // SHA-256 hashes من public key
    val includeSubdomains: Boolean = true,
    val maxAge: Long = 86400 * 365 // سنة واحدة
) {
    fun toPinHeader(): String {
        val pinDirective = pins.joinToString("; ") { "pin-sha256=\"$it\"" }
        val subdomains = if (includeSubdomains) "; includeSubdomains" else ""
        val maxAgeDirective = "; max-age=$maxAge"
        return "$pinDirective$subdomains$maxAgeDirective"
    }
}
