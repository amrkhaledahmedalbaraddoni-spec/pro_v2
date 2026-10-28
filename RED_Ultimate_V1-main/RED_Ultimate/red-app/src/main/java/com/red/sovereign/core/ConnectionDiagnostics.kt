package com.red.sovereign.core

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import android.security.NetworkSecurityPolicy
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.InetSocketAddress
import java.net.Socket
import java.net.URI
import java.util.concurrent.TimeUnit

/**
 * تشخيص الاتصال بالخادم — «أقوى تقنيات» استكشاف الأعطال بدل رسالة غامضة.
 *
 * المشكلة التي كانت تواجه المستخدم: «التطبيق لا يتصل بالسيرفر ويفشل في كل
 * مرة مع أنهما بنفس الشبكة». الأسباب المحتملة كثيرة (منفذ مغلق، حائط نارية،
 * VPN، خادم Node على 8080 بدل Nginx على 8088، نسخة release تمنع HTTP،
 * قواعد البيانات لم تجهز، شهادة TLS، مضيف قديم…). رسالة واحدة لا تكفي.
 *
 * هذا الملف يفحص كل طبقة على حدة ويعيد تقريراً عربياً بخطوات قابلة للتنفيذ:
 * 1) الإدخال وتطبيعه  2) وجود شبكة  3) سياسة النص الصريح (cleartext)
 * 4) منفذ TCP         5) /health/live  6) /health  7) بصمة هوية الخادم.
 */
data class DiagnosticStep(
    val title: String,
    val ok: Boolean,
    val detail: String,
    val hint: String = ""
)

data class ConnectionReport(
    val target: String,
    val steps: List<DiagnosticStep>,
    val summary: String
) {
    /** نجاح الاتصال = وصول /health/live أو /health ببصمة يونس صحيحة. */
    val serverReachable: Boolean get() = steps.any { it.title.contains("بصمة") && it.ok }
}

object ConnectionDiagnostics {
    private const val TAG = "YounesDiagnostics"

    suspend fun run(context: Context, rawUrl: String): ConnectionReport = withContext(Dispatchers.IO) {
        val steps = mutableListOf<DiagnosticStep>()

        // ── 1) تطبيع العنوان ────────────────────────────────────────────
        val parsed = runCatching { ServerEndpoint.normalizeUserInput(rawUrl) }
        if (parsed.isFailure) {
            val reason = parsed.exceptionOrNull()?.message ?: "عنوان غير صالح"
            steps += DiagnosticStep(
                "صيغة العنوان", false, reason,
                "استخدم مثالاً: 192.168.1.50:8088 أو http://192.168.1.50:8088"
            )
            return@withContext ConnectionReport(rawUrl, steps, "❌ $reason")
        }
        val normalized = parsed.getOrThrow()
        steps += DiagnosticStep("صيغة العنوان", true, normalized, "")
        val uri = URI(normalized)
        val host = uri.host ?: ""
        val port = if (uri.port > 0) uri.port else if (uri.scheme == "https") 443 else 80
        val isHttp = uri.scheme == "http"

        // ── 2) وجود شبكة نشطة ───────────────────────────────────────────
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
        val active = cm?.activeNetwork
        val caps = active?.let { cm.getNetworkCapabilities(it) }
        val hasNetwork = caps != null && caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) ||
            caps?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true ||
            caps?.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) == true
        steps += DiagnosticStep(
            "الشبكة", hasNetwork,
            if (hasNetwork) "الهاتف متصل بشبكة" else "لا توجد شبكة نشطة",
            if (hasNetwork) "" else "فعّل Wi-Fi وتأكد من أنه نفس شبكة الخادم (لا بيانات خلوية ولا VPN)."
        )

        // ── 3) سياسة النص الصريح (cleartext) ────────────────────────────
        val cleartextAllowed = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            runCatching {
                val policy = NetworkSecurityPolicy.getInstance()
                if (host.isBlank()) policy.isCleartextTrafficPermitted
                else policy.isCleartextTrafficPermitted(host)
            }.getOrDefault(true)
        } else true
        val cleartextStep = if (isHttp) {
            DiagnosticStep(
                "سياسة HTTP المحلي", cleartextAllowed,
                if (cleartextAllowed) "مسموح بالـ HTTP لهذا المضيف" else "النظام يمنع HTTP لهذا المضيف",
                if (cleartextAllowed) "" else "استخدم HTTPS (المنفذ 8443) أو ثبّت نسخة تسمح بـ LAN cleartext. " +
                    "راجع res/xml/network_security_config.xml."
            )
        } else DiagnosticStep("سياسة TLS", true, "الرابط يستخدم HTTPS", "")
        steps += cleartextStep

        // ── 4) منفذ TCP ────────────────────────────────────────────────
        val tcpOk = runCatching {
            Socket().use { s ->
                s.connect(InetSocketAddress(host, port), 1_200)
                true
            }
        }.getOrDefault(false)
        steps += DiagnosticStep(
            "المنفذ $port", tcpOk,
            if (tcpOk) "المنفذ مفتوح ويستقبل الاتصالات" else "لا يمكن فتح اتصال TCP",
                if (tcpOk) "" else "تحقق من: docker compose ps (حاوية red-proxy)، جدار الحماية في ويندوز، " +
                "RED_HTTP_PORT=8088، وأن الهاتف على نفس الـ Wi-Fi. جرّب فتح " +
                "http://$host:$port/health/live من متصفح الهاتف."
        )

        // ── 5/6/7) فحوص HTTP ───────────────────────────────────────────
        val client = OkHttpClient.Builder()
            .connectTimeout(2, TimeUnit.SECONDS)
            .readTimeout(4, TimeUnit.SECONDS)
            .callTimeout(6, TimeUnit.SECONDS)
            .retryOnConnectionFailure(false)
            .followRedirects(false)
            .build()

        if (tcpOk) {
            val live = probe(client, "$normalized${YounesServerSignature.LIVE_ENDPOINT}")
            steps += DiagnosticStep(
                "فحص الحياة /health/live", live.code in 200..299,
                "HTTP ${live.code} · ${live.latencyMs}ms",
                if (live.code in 200..299) "" else "إن ظهر 404 فأنت تتصل بخادم آخر (مثل Node على 8080). " +
                    "الخادم الصحيح خلف Nginx على المنفذ 8088."
            )
            val health = probe(client, "$normalized${YounesServerSignature.HEALTH_ENDPOINT}")
            steps += DiagnosticStep(
                "الجاهزية /health", health.code in 200..299,
                "HTTP ${health.code} · ${health.latencyMs}ms · ${health.statusLabel}",
                when {
                    health.code == 503 -> "قواعد البيانات لم تجهز بعد: راجع docker compose logs -f backend."
                    health.code in 200..299 -> ""
                    else -> "تعذر قراءة حالة الخادم."
                }
            )

            val liveOk = YounesServerSignature.isYounesLive(live.body.orEmpty())
            val healthOk = YounesServerSignature.isYounesServer(health.body.orEmpty(), "")
            steps += DiagnosticStep(
                "بصمة خادم يونس", liveOk || healthOk,
                if (liveOk || healthOk) "تم التحقق: 1.0.0-YOUNES ✅" else "الاستجابة لا تحمل بصمة يونس",
                if (liveOk || healthOk) "" else "أنت تتصل بخدمة أخرى على نفس المنفذ. شغّل الحزمة الصحيحة " +
                    "أو افتح /api/identity/authority للتحقق من المفتاح العام."
            )
        } else {
            steps += DiagnosticStep("فحص الحياة /health/live", false, "تم تخطيه — المنفذ مغلق", "")
            steps += DiagnosticStep("بصمة خادم يونس", false, "تم تخطيها — المنفذ مغلق", "")
        }

        val failed = steps.filterNot { it.ok }
        val summary = if (failed.isEmpty()) {
            "✅ الاتصال سليم. إن فشل تسجيل الدخول فالمشكلة في الحساب أو الجلسة لا في الشبكة."
        } else {
            "❌ أول نقطة فشل: ${failed.first().title} — ${failed.first().detail}\n" +
                failed.mapNotNull { it.hint.takeIf(String::isNotBlank) }.distinct().joinToString("\n• ", "• ")
        }
        ConnectionReport(normalized, steps, summary)
    }

    private data class ProbeResult(val code: Int, val body: String?, val latencyMs: Long) {
        val statusLabel: String
            get() = Regex("\"status\"\\s*:\\s*\"([^\"]+)\"", RegexOption.IGNORE_CASE)
                .find(body.orEmpty())?.groupValues?.get(1) ?: "غير معروف"
    }

    private fun probe(client: OkHttpClient, url: String): ProbeResult = runCatching {
        val started = android.os.SystemClock.elapsedRealtime()
        client.newCall(Request.Builder().url(url).get().build()).execute().use { response ->
            ProbeResult(
                response.code,
                runCatching { response.body?.string() }.getOrNull(),
                android.os.SystemClock.elapsedRealtime() - started
            )
        }
    }.getOrElse { e ->
        Log.w(TAG, "probe failed for $url: ${e.message}")
        ProbeResult(-1, null, 0L)
    }
}
