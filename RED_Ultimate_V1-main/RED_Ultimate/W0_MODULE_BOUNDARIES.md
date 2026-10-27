# W0 — حدود الوحدات (القرار القانوني)

> أنشئ 2026-09-26 لإصلاح 4 روابط ميتة من `core/README` و`lib/README` و`feature/README` و`reproducible-builds/README`.

## ما يدخل البناء
- `:app` = `red-app/` + `:shared-proto` (من `settings.gradle.kts`)
- `backend-server/` بناء Gradle مستقل عمداً (له `settings.gradle.kts` خاص)
- `admin_dashboard/` بناء `npm` خارج Gradle
- `build-logic/` + `fast-lint/` مشروطان بمهام `qa/ci` فقط

## مرجع ميت — ممنوع إعادة التضمين بدون قرار معماري مكتوب
- `core/` (7 وحدات Signal) — مرجع، لا `include`
- `feature/` (camera/media-send/registration) — مرجع، التسجيل القانوني في `red-app+backend`
- `lib/` (~18 مكتبة Signal) — منجم مرجعي، لا يُبنى
- `janus/` + `kamailio/` — طبقة PSTN يتيمة (لا خدمة في الكومبوز الرئيسي)، تُؤرشف معاً
- `voicebridge/` — مشروع Maven مستقل (16 ملف java)، لا يُبنى مع Gradle
- `wire-handler/` — بناء مستقل + `jar` مرتكب (تحقق `SHA` قبل أي استعمال)
- `reproducible-builds/` — أدوات مقارنة APK موروثة، تُصلح لا تُؤرشف

## قاعدة النقل الآمن
نقل = `Move-Item` + ملف `ORIGIN.txt` + سطر في `docs/ARCHIVE_INVENTORY` — لا `Remove-Item` نهائي قبل 90 يوماً ونسختين.
يُمنع نقل: `red-app/ backend-server/ shared-proto/ admin_dashboard/ media-sfu/ infrastructure/ docker-compose*.yml nginx*.conf secrets/ backups/`.
