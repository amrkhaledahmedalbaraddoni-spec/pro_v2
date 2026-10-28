# YOUNES / RED Sovereign

> 📋 **2026-10-28 — [تقرير التدقيق والتطوير الشامل](تقرير_التدقيق_والتطوير_الشامل_2026-10-28.md)** — تدقيق git/GitHub (لا فقدان لعمل مهم)، إصلاح الكاميرا في كل أنواع المكالمات، إصلاح الاتصال بالخادم على الشبكة المحلية، ونظام تصميم موحّد (خلفيات/ألوان/أيقونات/أنيميشن).

> 🚀 **اتصال سريع على LAN:** من `RED_Ultimate_V1-main/RED_Ultimate` شغّل `docker compose --profile lan-discovery up -d` ثم افتح شاشة الدخول في التطبيق واضغط **«تشخيص الاتصال»** أو أدخل `192.168.1.50:8088` مباشرة.

> The canonical project tree lives under [`RED_Ultimate_V1-main/`](RED_Ultimate_V1-main/README.md).

## Repository layout

| Component | Path | Notes |
|---|---|---|
| Android App | `RED_Ultimate_V1-main/RED_Ultimate/red-app/` | Kotlin + Jetpack Compose |
| Backend Server | `RED_Ultimate_V1-main/RED_Ultimate/backend-server/` | Spring Boot, REST + WebSocket APIs |
| Admin Dashboard | `RED_Ultimate_V1-main/RED_Ultimate/admin_dashboard/` | Vite + TypeScript |
| Shared Protocol | `RED_Ultimate_V1-main/RED_Ultimate/shared-proto/` | Protobuf schemas |
| Media SFU | `RED_Ultimate_V1-main/RED_Ultimate/media-sfu/` | Node.js WebRTC SFU |
| Runtime | `RED_Ultimate_V1-main/RED_Ultimate/docker-compose.yml` | Full stack containers |
| CI | `.github/workflows/` | `red-ultimate-ci.yml` is the canonical workflow (single CI since 2026-09-15) |

## Building

Android (from `RED_Ultimate_V1-main/RED_Ultimate`):

```
.\gradlew.bat :app:compileDebugKotlin --offline --console=plain   (Windows)
./gradlew :app:assembleDebug -PRED_SKIP_BUILD_LOGIC=true          (CI)
```

Backend:

```
.\gradlew.bat compileKotlin compileTestKotlin --offline          (Windows)
./gradlew test --no-daemon --stacktrace                          (CI)
```

Secrets are configured via `RED_Ultimate_V1-main/RED_Ultimate/.env` (never committed; `.env.example` is the template). The `secrets/` folder under it is git-ignored and holds locally generated identity certificates.
