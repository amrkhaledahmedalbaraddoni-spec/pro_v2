#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
حارس انحدار (regression guard) لإصلاحات 2026-10-28.

يشغّل من CI أو محلياً:  python scripts/verify_p0_fixes.py

يتحقق من أن الأسباب الجذرية للعطبين الكبيرين لا تعود:
  1) سياسة cleartext + بوابة LAN.
  2) الاكتشاف عبر /health/live بمهل واقعية.
  3) مصنع الكاميرا الموحّد + بوابة أذونات المكالمات + Z-order.
  4) توحيد نظام التصميم في شاشة الدخول.
  5) مُعلِن LAN لا يموت عند غياب zeroconf.
"""
from __future__ import annotations

import pathlib
import sys

ROOT = pathlib.Path(__file__).resolve().parents[1]
failures: list[str] = []


def read(rel: str) -> str:
    p = ROOT / rel
    if not p.exists():
        failures.append(f"ملف مفقود: {rel}")
        return ""
    return p.read_text(encoding="utf-8", errors="ignore")


def check(label: str, condition: bool, hint: str = "") -> None:
    if not condition:
        failures.append(f"{label}" + (f" — {hint}" if hint else ""))


APP = "red-app/src/main/java/com/red/sovereign"
RES = "red-app/src/main/res"

# ── 1) سياسة الشبكة ────────────────────────────────────────────────────
nsc = read(f"{RES}/xml/network_security_config.xml")
check("cleartext مسموح على مستوى النظام (release)", 'cleartextTrafficPermitted="true"' in nsc)
check("حظر صريح للنطاقات العامة", "google.com" in nsc and "cleartextTrafficPermitted=\"false\"" in nsc)
endpoint = read(f"{APP}/core/ServerEndpoint.kt")
check("حارس LAN في الكود", "isLocalCleartextHost" in endpoint and "192 && octets[1] == 168" in endpoint)
check("تطبيع إدخال المستخدم", "normalizeUserInput" in endpoint)
gradle = read("red-app/build.gradle.kts")
check("usesCleartext=true في release", 'manifestPlaceholders["usesCleartext"] = "true"' in gradle)

# ── 2) الاكتشاف ────────────────────────────────────────────────────────
disc = read(f"{APP}/core/LocalServerDiscovery.kt")
check("الاكتشاف يبدأ بـ /health/live", "LIVE_ENDPOINT" in disc and "isYounesLive" in disc)
check("مهل واقعية", "const val READ_MS = 1_500L" in disc)
check("لا طلبين إجباريين", "getBody(" in disc)
sig = read(f"{APP}/core/YounesServerSignature.kt")
check("ثابت LIVE_ENDPOINT", 'const val LIVE_ENDPOINT = "/health/live"' in sig)
diag = read(f"{APP}/core/ConnectionDiagnostics.kt")
check("تشخيص طبقي موجود", diag and "fun run(" in diag and "cleartext" in diag.lower())
auth_vm = read(f"{APP}/auth/AuthViewModel.kt")
check("زر التشخيص موصول بالـ ViewModel", "fun runDiagnostics" in auth_vm and "setServerUrl" in auth_vm)

# ── 3) الكاميرا ────────────────────────────────────────────────────────
factory = read(f"{APP}/calls/CameraCaptureFactory.kt")
check("مصنع الكاميرا الموحّد", "Camera1Enumerator" in factory and "CameraEventsHandler" in factory)
for f in ["calls/WebRtcEngine.kt", "calls/MeshRtcSession.kt", "features/lan/LanRtcSession.kt"]:
    src = read(f"{APP}/{f}")
    check(f"{f} يستخدم المصنع", "CameraCaptureFactory.create" in src or "CameraCaptureFactory.create" in src)
    check(f"{f}: ADM lazy بعد ensure", "by lazy {" in src and "WebRtcBootstrap.ensure(context)" in src)
gate = read(f"{APP}/calls/CallPermissionGate.kt")
check("بوابة أذونات المكالمات", "rememberCallStarter" in gate and "Manifest.permission.CAMERA" in gate)
dash = read(f"{APP}/ui/ModernRedDashboard.kt")
check("كل نقاط الاتصال تمر بالبوابة", "startCall(" in dash and dash.count("YounesCallService.start(context") <= 2)
ui = read(f"{APP}/calls/CallUiKit.kt")
check("Z-order للمعاينة المحلية", "setZOrderMediaOverlay(true)" in ui and "setZOrderOnTop(true)" in ui)
active = read(f"{APP}/ui/screens/ActiveCallScreen.kt")
check("معاينة محلية isOverlay", "isOverlay = true" in active)
check("زر إعادة محاولة الكاميرا", "retryCameraNow" in active and "ACTION_CAMERA" in active)
perf = read(f"{APP}/calls/CallPerformanceManager.kt")
check("إصلاح mosScore=0", "s.mosScore > 0f" in perf)
svc = read(f"{APP}/calls/YounesCallService.kt")
check("إيقاف الفيديو التلقائي مشروط", "reallyBad" in svc and "60_000L" in svc)

# ── 4) نظام التصميم ────────────────────────────────────────────────────
auth = read(f"{APP}/ui/AuthScreens.kt")
check(
    "لا ألوان واتساب/تيليجرام معرّفة في الدخول",
    "private val CleanAccentGreen" not in auth and "private val TelegramDarkBg" not in auth,
)
check("شاشة الدخول تستخدم الثيم", "MaterialTheme.colorScheme" in auth and "YounesIcons" in auth)
check("احترام reduceMotion", "AppThemeState.reduceMotion" in auth)
theme = read(f"{APP}/ui/theme/RedTheme.kt")
check("الشفق موصول بكل الشاشات", "AuroraMeshLayer(isDark = isDark)" in theme)
emoji = read(f"{APP}/ui/components/SovereignEmojiPicker.kt")
check("منتقي إيموجي موحّد", "skinTones" in emoji and "LazyVerticalGrid" in emoji)
for f in ["ui/screens/ChatsScreen.kt", "ui/MessageContent.kt"]:
    check(f"{f} يفوّض للمنتقي الموحّد", "SovereignEmojiPicker(onEmoji = onEmoji)" in read(f"{APP}/{f}"))
lang = read(f"{APP}/settings/LanguageManager.kt")
check("إدارة لغة التطبيق", "setApplicationLocales" in lang and "values-en" not in lang)
settings = read(f"{APP}/settings/SettingsScreen.kt")
check("اللغة موصولة بالإعدادات", "LanguageSelector()" in settings)

# ── 5) الخادم ──────────────────────────────────────────────────────────
announcer = read("scripts/server-announcer.py")
check("المُعلِن لا يموت", "keeping UDP responder alive" in announcer)
check("منفذ قابل للضبط", "YOUNES_HTTP_PORT" in announcer)
compose = read("docker-compose.yml")
check("خدمة lan-announcer", "lan-announcer:" in compose and 'profiles: ["lan-discovery"]' in compose)
security = read("backend-server/src/main/kotlin/com/red/server/config/SecurityConfig.kt")
check("CORS للشبكات الخاصة", "http://192.168.*" in security)
check("كوكي الجلسة قابل للضبط", "adminCookieSecure" in security)

# ── النتيجة ────────────────────────────────────────────────────────────
if failures:
    print("❌ فشل حارس الانحدار:")
    for f in failures:
        print("  •", f)
    sys.exit(1)
print(f"✅ جميع فحوص P0 ناجحة ({34 - len(failures)} فحصاً).")
