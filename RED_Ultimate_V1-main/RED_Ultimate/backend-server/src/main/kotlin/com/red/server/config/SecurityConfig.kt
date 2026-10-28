package com.red.server.config

import com.red.server.auth.security.JwtAuthenticationFilter
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.HttpMethod
import org.springframework.http.MediaType
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.http.SessionCreationPolicy
import org.springframework.security.crypto.argon2.Argon2PasswordEncoder
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.security.web.SecurityFilterChain
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter
import org.springframework.web.cors.CorsConfiguration
import org.springframework.web.cors.CorsConfigurationSource
import org.springframework.web.cors.UrlBasedCorsConfigurationSource

/**
 * YOUNES Sovereign Security Configuration
 * - Argon2id password hashing
 * - JWT authentication filter
 * - CORS configuration
 * - Stateless session management
 */
@Configuration
@EnableMethodSecurity
// 2026-09-26: تفعيل أمن الميثود — كانت كل @PreAuthorize في المشروع
// (CommunitiesController وغيرها) تُتجاهل بصمت لانعدامها. التفعيل لا يغير
// السلوك الفعلي (المسارات نفسها محمية بقواعد URL)، لكنه يجعل الحراس
// المعلنة ملزمة بدل الصمت.
class SecurityConfig(
    private val jwtFilter: JwtAuthenticationFilter,
    @Value("\${red.security.allowed-origins:http://localhost,http://127.0.0.1}")
    private val configuredAllowedOrigins: String,
    @Value("\${red.admin-cookie.secure:true}")
    private val adminCookieSecure: Boolean = true
) {
    @Bean
    fun passwordEncoder(): PasswordEncoder = Argon2PasswordEncoder.defaultsForSpringSecurity_v5_8()

    @Bean
    fun securityFilterChain(http: HttpSecurity): SecurityFilterChain {
        http
            .csrf { it.disable() }
            .cors { it.configurationSource(corsConfigurationSource()) }
            .sessionManagement { it.sessionCreationPolicy(SessionCreationPolicy.STATELESS) }
            .exceptionHandling { exceptions ->
                exceptions
                    .authenticationEntryPoint { _, response, _ ->
                        response.status = 401
                        response.contentType = MediaType.APPLICATION_JSON_VALUE
                        response.writer.write("{\"error\":\"AUTHENTICATION_REQUIRED\"}")
                    }
                    .accessDeniedHandler { _, response, _ ->
                        response.status = 403
                        response.contentType = MediaType.APPLICATION_JSON_VALUE
                        response.writer.write("{\"error\":\"ADMIN_ROLE_REQUIRED\"}")
                    }
            }
            .authorizeHttpRequests { auth ->
                auth
                    // Public endpoints
                    .requestMatchers(HttpMethod.POST, "/api/auth/register", "/api/auth/login", "/api/auth/refresh", "/api/auth/logout", "/api/auth/recover").permitAll()
                    // سلطة التوقيع وحدها عامة: مفتاح عام يجب أن يصل إلى
                    // العميل قبل أن يملك جلسة، وهو غير حساس بطبيعته.
                    .requestMatchers(HttpMethod.GET, "/api/identity/authority").permitAll()

                    // ⚠️ `/api/identity/directory` **ليست عامة**.
                    //
                    // كانت `permitAll()`، والمسار بلا شرطة مائلة نهائية لا
                    // يطابق `/api/identity/directory/{redId}`، غير أن ترك
                    // القاعدة هنا يوحي بأن الدليل عام ويغري بتوسيعها إلى
                    // `/**`. والمسار يكشف حزم المفاتيح العامة كاملةً
                    // (identityKey، البصمة، شهادة التخويل) لكل جهاز معتمد.
                    //
                    // الأخطر مسار `…/{deviceId}/prekey`: كل نداء **يستهلك**
                    // مفتاحًا لمرة واحدة استهلاكًا ذرّيًا. بلا مصادقة يستطيع
                    // أي طرف استنزاف مخزون مفاتيح أي مستخدم بحلقة بسيطة،
                    // فتتدهور جلسات Signal الجديدة إلى المفتاح الموقّع
                    // وحده — تعطيل خدمة يمسّ سرّية التشفير المستقبلية.
                    //
                    // ومع اختصار معرّف يونس إلى خمسة أرقام صار فضاء
                    // المعرّفات (89,999) قابلًا للتعداد الكامل، فما كان
                    // صعبًا عمليًا صار زحفًا مباشرًا على الدليل كله.
                    // ── V2 groups/calls: authenticated only; identity ALWAYS from
                    // Authentication (JWT principal = user UUID). The legacy
                    // `X-RED-ID` request header is NEVER trusted for identity —
                    // controllers must take `Authentication` and ignore that header.
                    .requestMatchers("/api/groups/v2/**").authenticated()
                    .requestMatchers("/api/calls/v2/**").authenticated()
                    .requestMatchers(HttpMethod.GET, "/api/identity/directory/**").authenticated()
                    // SFU والدليل والهوية: مصادَقة دائمًا والهوية من Authentication
                    // حصرًا (قواعد صريحة قبل /api/** حتى لا يُعيد permitAll لاحق الانحدار).
                    .requestMatchers("/api/sfu/**").authenticated()
                    .requestMatchers("/api/identity/**").authenticated()
                    .requestMatchers("/api/directory/**").authenticated()
                    // التفاصيل الكاملة لقواعد البيانات والمضيف للمسؤولين فقط؛
                    // المساران العامان أدناه يُبقيان الحالة وحدها.
                    .requestMatchers("/health/detailed").hasRole("ADMIN")
                    .requestMatchers("/health", "/health/live", "/actuator/health", "/actuator/info", "/actuator/prometheus").permitAll()
                    .requestMatchers("/ws/**").permitAll()
                    // ── مشاركة المستخدم في المحتوى ──
                    //
                    // ⚠️ هذه المسارات تقع تحت `/api/admin/content` بحكم
                    // `@RequestMapping` في `ContentController`، لكنها
                    // **أفعال مشارِك لا أفعال مسؤول**: كلٌّ منها يأخذ
                    // `authentication.name` — أي هوية المستدعي نفسه —
                    // ويسجّل صوته أو حضوره به.
                    //
                    // بقاؤها تحت قاعدة `/api/admin/**` كان يعني أن **لا
                    // مستخدم عادي يستطيع التصويت في استطلاع ولا تأكيد
                    // حضور فعالية**: 403 دائمًا. والتطبيق يستدعيها فعلًا
                    // (`EventsApi.rsvp/checkin`, `PollsApi.vote`).
                    //
                    // تسبق قاعدة ADMIN لأن الأسبقية للأول المطابق.
                    .requestMatchers(HttpMethod.POST, "/api/admin/content/polls/*/vote").authenticated()
                    .requestMatchers(HttpMethod.POST, "/api/admin/content/events/*/rsvp").authenticated()
                    .requestMatchers(HttpMethod.POST, "/api/admin/content/events/*/checkin").authenticated()
                    // قراءة المحتوى المنشور متاحة لكل مصادَق؛ الإنشاء
                    // والتعديل والحذف تبقى إدارية عبر القاعدة التالية.
                    .requestMatchers(HttpMethod.GET, "/api/admin/content/polls", "/api/admin/content/polls/active").authenticated()
                    .requestMatchers(HttpMethod.GET, "/api/admin/content/polls/*").authenticated()
                    .requestMatchers(HttpMethod.GET, "/api/admin/content/events", "/api/admin/content/events/live", "/api/admin/content/events/upcoming").authenticated()
                    .requestMatchers(HttpMethod.GET, "/api/admin/content/events/*").authenticated()
                    // الملصقات: استعراض المنشورة + ملصقات الحزمة + المثبّتة (للمستخدم)
                    .requestMatchers(HttpMethod.GET, "/api/admin/content/sticker-packs/published", "/api/admin/content/sticker-packs/installed", "/api/admin/content/sticker-packs/*/stickers").authenticated()
                    .requestMatchers(HttpMethod.POST, "/api/admin/content/sticker-packs/*/install").authenticated()
                    .requestMatchers(HttpMethod.DELETE, "/api/admin/content/sticker-packs/*/install").authenticated()

                    // 2026-09-26: إرسال الوكلاء فعل إداري (يكتب في admin_audit_log).
                    // كان تحت /api/** العامة فيستدعيه أي مصادق — مغلق على ADMIN هنا
                    // إضافةً للفحص الكودي داخل RedServerAgentController (دفاع بالعمق).
                    .requestMatchers(HttpMethod.POST, "/api/v1/agents/dispatch").hasRole("ADMIN")
                    .requestMatchers("/api/v1/agents/**").authenticated()
                    // Admin endpoints (including the legacy live-stream admin namespace)
                    .requestMatchers("/api/admin/**", "/api/master/admin/**", "/api/master/v1/**", "/api/live/admin/**").hasRole("ADMIN")
                    // Social features
                    .requestMatchers(HttpMethod.GET, "/api/social/status/**").authenticated()
                    .requestMatchers(HttpMethod.PUT, "/api/social/status", "/api/social/privacy").authenticated()
                    .requestMatchers(HttpMethod.GET, "/api/social/privacy", "/api/social/online-contacts").authenticated()
                    .requestMatchers("/api/notifications/**").authenticated()
                    // Telemetry aggregates are an operations signal, not public user data.
                    // Keep the upload authenticated, but restrict the aggregated admin view.
                    .requestMatchers(HttpMethod.GET, "/api/calls/telemetry/stats").hasRole("ADMIN")
                    .requestMatchers("/api/calls/**", "/api/calls/ice-servers", "/api/calls/telemetry").authenticated()
                    .requestMatchers("/api/recordings/**").authenticated()
                    .requestMatchers("/api/stories/**").authenticated()
                    .requestMatchers("/api/feed/**", "/api/posts/**", "/api/social/**").authenticated()
                    .requestMatchers("/api/groups/**").authenticated()
                    // V26: ميزات جديدة — قنوات، تثبيت، اختفاء مرن
                    .requestMatchers("/api/channels/**").authenticated()
                    .requestMatchers("/api/messages/pins/**").authenticated()
                    .requestMatchers("/api/messages/**", "/api/contacts/**", "/api/devices/**").authenticated()
                    .requestMatchers("/api/media/**").authenticated()
                    .requestMatchers("/api/**").authenticated()
                    .anyRequest().authenticated()
            }
            // ✅ HttpOnly + SameSite for refresh cookie — CSRF enabled for admin panel
            // Admin dashboard uses Bearer JWT (stateless), but future HttpOnly cookie will set SameSite=Strict
            // NOTE: Spring CSRF stays disabled because mobile Bearer flows are stateless;
            // browser admin-cookie flows (refresh/logout) enforce custom double-submit
            // CSRF via CsrfTokenValidator (cookie red_admin_csrf vs header X-RED-CSRF).
            // X-RED-ID is deliberately absent from allowedHeaders: it must never
            // become an identity source; JwtAuthenticationFilter is the sole binder.
            .headers { headers ->
                headers
                    .xssProtection { it.disable() }
                    .contentSecurityPolicy { it.policyDirectives("default-src 'self'; script-src 'self'; style-src 'self' 'unsafe-inline'; img-src 'self' data: blob:; connect-src 'self' ws: wss:; font-src 'self' data:; media-src 'self' blob:; frame-ancestors 'none'") }
                    .referrerPolicy { it.policy(ReferrerPolicyHeaderWriter.ReferrerPolicy.NO_REFERRER) }
            }
            // JWT filter must come before UsernamePasswordAuthenticationFilter (always present in filter chain)
            .addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter::class.java)

        return http.build()
    }

    /**
     * 🍪 Helper for future HttpOnly refresh cookie (10min SFU ticket already uses short-lived JWT)
     * When migrating from Bearer to Cookie, set:
     *   ResponseCookie.from("refreshToken", token).httpOnly(true).secure(true).sameSite("Strict").path("/api/auth").maxAge(30*24*3600).build()
     */
    fun buildRefreshCookie(token: String, maxAgeDays: Long = 30): String {
        // P0 2026-10-28: `Secure` الصلب كان يُسقط الكوكي فوق HTTP المحلي
        // (المنفذ 8088) فتنكسر جلسة لوحة الإدارة بلا رسالة. الإعداد الافتراضي
        // في application.yml عبر red.admin-cookie.secure، وتضبطه .env
        // (RED_ADMIN_COOKIE_SECURE=false للتطوير على LAN).
        val secure = if (adminCookieSecure) "; Secure" else ""
        return "refreshToken=$token; Path=/api/auth; Max-Age=${maxAgeDays*24*3600}; HttpOnly$secure; SameSite=Strict"
    }

    @Bean
    fun corsConfigurationSource(): CorsConfigurationSource {
        val configuration = CorsConfiguration().apply {
            // P0 2026-10-28: CORS الافتراضي كان localhost فقط. لوحة الإدارة تُفتح
            // من هاتف/حاسوب آخر على LAN عبر http://192.168.x.x:8088 أو من
            // Vite dev server على :5173 — فتُرفض كل طلباتها بـ 403 صامت وتظهر
            // «لوحة الأدمن لا تتزامن مع قاعدة البيانات». نضيف أنماط النطاقات
            // الخاصة RFC1918 (لا wildcard عام للإنترنت) مع الإبقاء على ما يضبطه
            // المستخدم في ALLOWED_ORIGINS.
            val lanPatterns = buildList {
                addAll(listOf(
                    "http://localhost:*", "https://localhost:*",
                    "http://127.0.0.1:*", "https://127.0.0.1:*",
                    "http://10.*", "http://192.168.*"
                ))
                addAll((16..31).map { "http://172.$it.*" })
                addAll((16..31).map { "https://172.$it.*" })
                add("https://10.*")
                add("https://192.168.*")
            }
            val configured = configuredAllowedOrigins.split(',').map(String::trim).filter(String::isNotEmpty)
            allowedOriginPatterns = (configured + lanPatterns).distinct()
            allowedMethods = listOf("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS")
            allowedHeaders = listOf("Authorization", "Content-Type", "X-Requested-With", "X-Device-Id", "X-RED-Admin-Web", "X-RED-CSRF")
            exposedHeaders = listOf("Location", "X-Total-Count")
            allowCredentials = true
            maxAge = 3600
        }
        return UrlBasedCorsConfigurationSource().also {
            it.registerCorsConfiguration("/**", configuration)
        }
    }
}