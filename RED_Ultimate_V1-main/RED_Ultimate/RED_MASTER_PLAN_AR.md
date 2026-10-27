# RED Sovereign — الخطة الرئيسية للتطوير الشامل
> نقطة الحقيقة الوحيدة للتنفيذ — محدثة 2026-09-26 بعد فحص الكود نفسه سطر بسطر (5 فرق فحص متوازية).
> المبدأ الحديدي: **أرشفة آمنة فقط، لا حذف نهائي قبل 90 يوماً ونسختين. لا تكرارات ولا تعارضات.**

## 0) ما تم فهمه بالتحقق الذاتي (وليس من الوثائق فقط)

| المكوّن | الحقيقة من الكود | الحالة |
|---|---|---|
| التطبيق `:app` | `red-app/` فقط (388 ملف kt). `app/` غير موجود. `core/feature/lib` خارج `settings.gradle.kts` = كود ميت مرجعي | يعمل، لكن 4 مصادر ألوان متضاربة + شاشات مكررة + 3 فشل اختبار من 327 |
| الباك-إند | `backend-server/` بناء مستقل (183 ملف kt). Postgres 16 + Mongo 8 + Redis 7 + MinIO. JWT access 15min + refresh 30d بتدوير إجباري. أدوار حساب `USER/ADMIN` فقط (لا OWNER/MOD/VIEWER عمومي) | الترجمة تنجح، الاختبار 5 فشل من 305. مروحة multi-device ناقصة لـ 1:1. transcode stub |
| لوحة الأدمن | 38 ملف pages + 26 route فعلي. `_layout` شقيق لا أب = الحماية والشريط لا يعملان معمارياً. 4 أخضرات متضاربة. ~37 زر comingSoon. 11 صفحة يتيمة. 7 سكربتات `update-*.cjs` بمسارات مطلقة خطرة | حية غالباً (Users/Dashboard/Approvals/...) + وهمية (System/Security/Analytics/Profile/Settings) |
| الدوكر/البورتات | الأساسي سليم: nginx `8088:80/8443:443` → backend `8080` داخلي → sfu `4000` → WebRTC `40000-40100`. Node 24 + JDK 21 موحد (لا أسطورة 16/20). `fix+host-debug` subset من `dev`. `lan` فارغ بعد إزالة Telecom. `temp-dc.yml` و`nginx-simple.conf` و`monitoring/prometheus.yml` موتى | يحتاج توحيد + loopback + تنظيف TURN prod |
| الهيكل/التوثيق/الاختبار | البناء = `red-app+shared-proto` (+باك مستقل). 91 ملف md مكرر. `docs/ci/` و`W0_MODULE_BOUNDARIES.md` مفقودان (4 روابط ميتة). الاختبارات حمراء (327→3 فشل، 305→5 فشل). `core/feature/lib` اختباراتها يتيمة لا تُنفذ | الترجمة تمر، البوابة لا تمر |

## 1) الهوية البصرية Canonical (مطبقة الآن)
- **Younes:** Primary `#14C79A` / Glow `#3DE8BC` / Accent `#E0B551` / Cobalt `#4D9FE8` / Void `#0A0F18` / نص داكن `#06090F`
- فاتح أولاً (لؤلؤي `#F7F8FA` + كحلي `#0F1B2D`)، داكن slate لا أسود خالص. ذهبي مطفي للشارات فقط.
- `radius 12/16` بدل `28/36`، ظل واحد ناعم بدل `glow 30px`. خط `IBM Plex Sans Arabic` فقط + `Inter` للأرقام.
- مطبق في: `admin_dashboard/tailwind.config.ts` + `src/styles.css` (موحد مع `src/theme.ts` + `red-app/.../RedTheme.kt`)

## 2) خارطة المراحل
### Phase 0 — تجميد وحماية (تم جزئياً)
- [x] نسخة `backups/RED_Ultimate_src_2026-09-26.tar.gz` موجودة
- [ ] `MANIFEST` + نسخة خارج القرص + `ARCHIVE_INVENTORY_NEW.csv`
- [ ] إصلاح الفشل الحالي أولاً: `SovereignGlassPolicyTest` + `CallWebSocketHandlerTest`

### Phase 1 — توحيد سريع آمن (بدأ الآن)
- [x] توحيد أخضر الأدمن على `#14C79A` + تخفيف التوهج + radius حديث
- [x] `docker-compose.dev`: mailhog/pgadmin → loopback + volumes `*-dev` لمنع تلوث prod
- [ ] إنشاء `docs/ci/README.md` + `W0_MODULE_BOUNDARIES.md` (إصلاح الروابط الميتة)
- [ ] أرشفة الميت إلى `docs/الأرشيف/compose-2026-09-26/` (لا حذف): `host-debug, lan, temp-dc, nginx-simple, monitoring/prometheus`

### Phase 2 — لوحة الأدمن (12 مساراً بدل 26)
`/overview` (دمج 3 لوحات) → `/users` (المرجع) → `/moderation` (طابور موحد + bulk) → `/content` → `/calls` (سجل+مباشر) → `/analytics` (الحية فقط) → `/system-health` → `/audit` → `/backups` → `/security-center` (تبويبات حية) → `/settings` (+profile). إصلاح `_layout` أباً + حذف `RequireAuth` المكررة + ربط/حذف 11 يتيمة + Bulk في Reports/Moderation + استبدال `prompt()` بـ Dialog.

### Phase 3 — التطبيق (الأهم)
توحيد مصدر الألوان (حذف XML الميت) + حذف `RedDashboard` القديم + دمج `AdvancedGroupChat` في `ChatThread` + `sovereignTabTransition()` بدل سكربت المسار المطلق + `SharedTransition` + `ECO` افتراضي لـ `<4GB` + تفعيل LiveKit خلف Flag أو حذفه + تنفيذ `topicId/GroupDocument/pagination` + ربط `isComingSoon` بخارطة علنية + معرض خلفيات واحد.

### Phase 4 — باك-إند + قواعد + مزامنة (مقارنة واتساب/تيليجرام)
`fan-out` خادمي لكل الأجهزة → تجميع `group receipts` → عامل `transcode` أو إزالة الادعاء → `Redis Streams` بدل pub/sub → تقسيم `device_sync_events` + `TTL` + فهارس `refresh_sessions` → تنبيه `prekey<20` + `pending>1000` → تدقيق `RedServerAgentController/dispatch` + `/lan-peers`.

### Phase 5 — توثيق واختبار
`docs/` = `*.md` فقط (السجلات → `reports/<date>/raw/`) + دمج الجذر في `STATUS.md` واحد + `INDEX.md` بجدول الحالة + Jacoco بعتبة 60% + عقود `/api/sync` و`/dashboard` كـ TDD + `connectedDebugAndroidTest` ليلي.

## 3) ما تم تنفيذه (يُحدَّث كل جلسة — كل بند متحقق منه على القرص)

### الجلسة 1 — فحص + توحيد سريع
1. فحص 5 مسارات متوازية بالكود نفسه
2. `admin_dashboard/tailwind.config.ts`: إصلاح `yn.green #B78A2E→#14C79A` + `success` + `radius 28/36→12/16` + تخفيف `glow 30→15px`
3. `admin_dashboard/src/styles.css`: توحيد `:root` + `[data-theme=light]` مع `theme.ts` والتطبيق + `.badge.green/blue/gold` (بني→زمردي/ذهبي/كوبالت صحيح)
4. `docker-compose.dev.yml`: `mailhog/pgadmin → 127.0.0.1` + `volumes *-dev` (تحقق: `docker compose config --quiet` ناجح)
5. إنشاء `docs/ci/README.md` + `W0_MODULE_BOUNDARIES.md` + هذا الملف

### الجلسة 2 — راوتر الأدمن + ألوان التطبيق (متحقق)
6. الأدمن: 25 راوت → `_layout.*` أبناء (26 ملف `_layout.*`) + إزالة `RequireAuth` المكررة من 24 صفحة + `routeTree` مولّد رسميا (تحقق: 26 ملف + 1 `RequireAuth` متبقٍّ في `_shared.tsx` فقط + `vite build` ناجح)
7. التطبيق: `Color.kt Dark.primary #D32F2F→YounesPrimary` + `colors.xml`→aliases (تحقق: `compileDebugKotlin SUCCESS 56s` + `ColorContrast 10/10`)

### الجلسة 3 — باك-إند + Bulk + ليلي (متحقق بأوامر مباشرة)
8. الباك-إند: `processIncomingFanout` (نسخة لكل جهاز APPROVED، حد 100، حظر متبادل، idempotent) + `upsertGroupReceipt(s)` + `getGroupReceiptCounts` — بدون V68 (الجدول من V66/V67). تحقق: `cleanTest test --tests "*MessageService*"` → **15/15 خضراء (3 ملفات، 0 فشل)**
9. الأدمن: Bulk في Reports/Moderation (تحديد متعدد + شريط + 3 دوال `bulk*Reports` بحد 50) + استبدال **20/20** من `prompt/confirm/Modal.confirm` بـ Dialog الموحد (المتبقي تعليقان توثيقيان فقط). تحقق: `npm run check` → **EXIT 0** + `vite build` ناجح
10. التطبيق الليلي: `values-night/colors.xml` → aliases (تحقق: **27/27 مرجعا تُحل**) + `Dark.inversePrimary` و`Light.primary` → `EmeraldDark #0A7A5E` (5.31:1 AA). تحقق: `ColorContrast` → **10/10 خضراء**

### الجلسة 4 — اليتيمة + البوابتان الكاملتان (متحقق بأوامر مباشرة)
11. الأدمن: 11 راوتر جديد (`security-*` ×5، `system-*` ×5، `settings-advanced`) → **37 ملف `_layout.*`** + `routeTree` مولّد رسميا، بلا طغيان على الحي (أسماء مميزة + `SettingsPage` مستعار). تحقق: `npm run check` → **EXIT 0** (شغلته بنفسي بعد التعديل)
12. البوابة الكاملة: التطبيق **341/0/0** (71 ملفا، كان 327/3فشل) + الباك **314/0/0** (56 ملفا، كان 305/5فشل) — تحقق: عدّ XML بنفسي. صفر ملفات معدلة (إصلاحات الجلسات السابقة هي ما أخضرّ البوابتين)

### الجلسة 5 — Streams + دمج ملاحي + أرشفة + فهرس (متحقق بأوامر مباشرة)
13. الباك-إند: `MessageStreamService.kt` جديد (Streams دائمة `XADD/XREADGROUP/XACK` + `XPENDING/XCLAIM` حد 100 + `MAXLEN 5000`) + `transcode` صادقة (`NOT_SUPPORTED` بدل `QUEUED` الأبدية) + إصلاح كامن (`?.code()` في spring-data-redis 4.0.6). تحقق: عديت XML بنفسي → **58 ملفا / 324 اختبارا / 0 فشل** (كان 314)
14. الأدمن: روابط متقاطعة ذهابا وعودة (11 أمامية في System/Security/Settings + 11 عودة) — بلا حذف. تحقق: `npm run check` → **EXIT 0** (شغلته بنفسي) + فحصت الروابط grep (11+11)
15. الأرشفة: 5 ملفات ميتة → `docs/archive/compose-2026-09-26/` + `ORIGIN.txt` (نقل موثق لا حذف). تحقق: `docker compose config` للأساسي و`+dev` → **كلاهما ناجح**
16. الفهرس: `docs/INDEX.md` (قانوني/مرجع/أرشيف لكل وثيقة) — `STATUS_FINAL_UNIFIED.md` موسومة مرجعا (أرقام PSTN تجاوزها الكود)

### الجلسة 6 — تفعيل System/Security (متحقق بأوامر مباشرة)
17. `System.tsx`: الأعلام حية (تبديل يُحفظ عبر PUT) + النسخ حية قراءة + الهجرات حية (Flyway) + الكاش حي (INFO + إبطال بنمط) + الباقي روابط صادقة. `Security.tsx`: الجلسات حية (جدول + إبطال بسبب مسجل) + الباقي EmptyState صادقة + روابط. تحقق: `tsc EXIT 0` + `comingSoon: 45→24` (شغلتهما بنفسي) — حُذفت كل البيانات الوهمية 2024 من الصفحتين بلا حذف ملفات

### الجلسة 7 — Analytics/Moderation/Profile/Settings (متحقق بأوامر مباشرة)
18. `Moderation.tsx`: البث صار إعلانات حقيقية (إنشاء + نشر) + تبويب التحليلات عدّ حي لكل الحالات + القواعد EmptyState صادقة + عدّاد الطابور الحي. `Analytics.tsx`: مؤشرات حية (ملخص + أحداث بالنطاق الزمني) + الجداول الوهمية (التقارير المجدولة) حُذفت + الألسنة تربط /dashboard.
19. `Profile.tsx`: الجلسات حية (مفلترة بحسابي) + النشاط حي من التدقيق + الأجهزة EmptyState صادقة + الحفظ موسوم محليا + كلمة المرور معطلة بسبب معلن. `Settings.tsx`: الجلسات رابط حي + الباقي معطل بأسباب معلنة (لا comingSoon).
20. تحقق: `tsc EXIT 0` + `vite build ناجح` + **`comingSoon: 24→0`** (شغلتها بنفسي) — صفر أزرار/نصوص comingSoon في اللوحة كلها

### الجلسة 8 — وجهات المكالمات: الحية مقابل الميتة (متحقق بأوامر مباشرة)
21. ثبت بالبحث: الحية = `ModernRedDashboard.ModernCallsScreen` (أذونات + حوارات + سجل + إعادة اتصال تعمل). الميتة: `RedDashboard.kt` (صفر مستدعين) + `CallsScreens.UnifiedCallsScreen` + `screens.CallsScreen` + `AdvancedGroupChatScreen` (عرض تجريبي ببيانات ثابتة، صفر مستدعين).
22. وسم الميت بـ `@Deprecated` تحذيري + ترويسة `RedDashboard` (بلا حذف — أي استعمال مستقبلي يُحذر آليا). `LiveKitSfuProvider`: صادق أصلا (fail-fast + `USE_LIVEKIT=false` + لا يُستدعى إنتاجيا) — يُبقى. `CallCaptionManager`: مربوط عبر `CallManagerIntegration` — حي، لا مساس.
23. تحقق: `compileDebugKotlin --rerun-tasks` → **BUILD SUCCESSFUL** + suites (Call/Sfu/Theme/Color) → **83/83 خضراء** (عدّ XML بنفسي)

### الجلسة 9 — المنشورات/القصص/الاستطلاعات: حية بالفحص (متحقق بأوامر مباشرة)
24. ثبت بالفحص: `FeedApi` مربوطة كاملا (نشر/تعديل/حذف/تفاعل/تصويت/إعادة/إبلاغ/كتم) + `StoryViewModel` (إنشاء/عرض/تفاعل/مشاهدون) + `PollsScreen/FeedViewModel` تستدعي `vote` — كلها على عقود خلفية مطابقة. الـ`onClick={}` الوحيدان (فقاعة الدردشة) تصميم مقصود (الضغط المطول هو الفعل).
25. الإحالات: **غير موجودة إطلاقا (صفر مرجع)** — لا باك ولا تطبيق. قرار صادق: تتطلب تصميم منتج + باك جديد، لا تُفبرك. تحقق: suites (Poll/Stor/Feed) → **34/34 خضراء** (عدّ XML بنفسي)

### الجلسة 10 — الحالات مثل واتساب: تكافؤ موثق + صف المكتومة (متحقق بأوامر مباشرة)
26. ثبت بالفحص أن الحالات بتكافؤ واتساب وأعلى: مؤلف 4 أنواع (صورة/فيديو/صوت/نص) + معاينة حية + ملصق 24h + جمهور (جهات/الجميع/محددون بمنتقٍ) ← كل المسارات تمرر visibleTo + الباك يفرض (حظر + تبادل + SELECTED) + TTL وتنظيف + عارض (تالٍ/سابق/تفاعل/مشاهدون/رد E2EE/حذف) + حلقات + كتم محفوظ.
27. الفجوة الوحيدة: المكتوم كان يختفي تماما (واتساب يعرضه بقسم). أضفت صف «مكتومة» بعدّاد في `FeedScreens` (عرض فقط بنفس العارض والفك). تحقق: `compileDebugKotlin` → **BUILD SUCCESSFUL** + suites (Stor) → **11/11 خضراء** (عدّ XML بنفسي)

### الجلسة 11 — ثغرات الباك-إند: سُدت (متحقق بأوامر مباشرة)
28. `POST /api/v1/agents/dispatch` كان يستدعيه أي مصادق (كتابة في سجل التدقيق): فحص كودي `ROLE_ADMIN` → 403 + قاعدة URL في `SecurityConfig` (دفاع بالعمق). اكتشاف مهم: لا `@EnableMethodSecurity` في المشروع — كل `@PreAuthorize` الحالية **تُتجاهل بصمت**، لذا الفحص الكودي هو الملزم.
29. `GET /api/admin/prekeys/low-stock` جديد (عتبة افتراضية 20): استعلام تجميعي واحد للأجهزة المعتمدة تحت الحد. `/lan-peers` تُرك للمصادقين بقرار موثق (بيانات ثابتة + ضروري لاكتشاف LAN).
30. تحقق: `AdminSecurityGapTest` → **5/5 خضراء** + الكاملة → **335/0/0** (عدّ XML بنفسي؛ تشمل مساهمات جلسات متوازية أخرى — لم أمس ملفاتها)

### الجلسة 12 — الهيكل + شارات الإعدادات (متحقق بأوامر مباشرة)
31. أرشفة: `core/feature/lib/janus/kamailio/voicebridge/wire-handler` (2240 ملفا، ~14MB) → `archive/reference-2026-09-26/` + `ORIGIN.txt` (نقل موثق). تحقق: البناء يعمل بعد النقل (الإعدادات لا تشملها أصلا).
32. ثبت بالبحث: كل `isComingSoon` (43) محصورة في `DeviceSettingsScreen` — وشاشتها غير قابلة للوصول (مستدعيها الوحيد `RedDashboard:503` الميتة؛ الحية `YounesSettingsSheet`). وُسمت `@Deprecated` (صفر خارجها — فحصته بنفسي). تحقق: `compileDebugKotlin` → **BUILD SUCCESSFUL**

### الجلسة 13 — تثبيت وإصلاح وإضافة (متحقق بأوامر مباشرة)
33. إصلاح: `@EnableMethodSecurity` في `SecurityConfig` — كل `@PreAuthorize` كانت تُتجاهل بصمت. تحقق: الكاملة → **335/0/0** (لا انحدار).
34. إضافة: فحص مخزون المفاتيح في `Diagnostics.tsx` (READY=الكل فوق الحد، ERROR=العدد تحت الحد) مربوط بالـendpoint الجديد. تحقق: `tsc EXIT 0`.
35. تثبيت: `:app:assembleDebug` → **BUILD SUCCESSFUL + APK مبني** (إثبات التركيب الكامل بعد كل الجلسات)

### الجلسة 14 — التثبيت على الهاتف (متحقق بأوامر مباشرة)
36. الجهاز: vivo V2322 (`10AE1Z114X001TB`) عبر `D:\Android\Sdk\platform-tools\adb.exe` (لا adb في PATH).
37. `install -r app-debug.apk` → **Success** + `pm list` يؤكد `com.red.sovereign v1.0.0-beta01 (code 3)` + `am start MainActivity` → **topResumedActivity حية (pid مثبت)** — التطبيق يعمل في المقدمة بلا كراش فوري

### الجلسة 15 — فحص الاستقرار على الهاتف (متحقق بأوامر مباشرة)
38. `logcat`: صفر `FATAL EXCEPTION` لحزمتنا + الـ pid حي — لا كراش منذ التثبيت.
39. ملاحظة خصوصية: المستخدم يستعمل الهاتف بنشاط (تطبيقات + مكالمة هاتفية `InCallActivity`) — أُوقف الالتقاط البصري أثناء مكالمته. التحقق البصري للواجهة يُترك لجلسة والمستخدم متاح.

### الجلسة 16 — تشخيص عدم الاتصال + APK شبكي (متحقق بأوامر مباشرة)
40. السبب الجذري: السيرفر سليم (10 حاويات healthy + 8088/8443 يستمعان + قواعد جدار RED 8088 مفعلة)، لكن الـAPK المثبت مبني بالقيمة الافتراضية `http://10.0.2.2:8088` (محاكي فقط) — الهاتف الحقيقي يحتاج IP الحاسب `192.168.1.67:8088`.
41. إصلاح `scripts/build-apk.ps1` (خطأ ترميز منع تحليله) → `SCRIPT-PARSE-OK` + بناء مباشر `-PRED_SERVER_URL=http://192.168.1.67:8088` → **BUILD SUCCESSFUL** + `BuildConfig.RED_SERVER_URL` مؤكد + نسخة `apk-output/red-debug-lan.apk` (274MB + SHA256).
42. معلق على المستخدم: (1) الهاتف على نفس Wi-Fi (192.168.1.x)، (2) قبول حوار RSA لـadb (صار unauthorized)، ثم التثبيت والتحقق من `/health` من الهاتف.

### الجلسة 17 — الاتصال يعمل (متحقق بأوامر مباشرة)
43. الهاتف `192.168.1.176` نفس الشبكة + ping للحاسب ناجح + `/health` من الهاتف: **`status UP` (postgres/mongo/redis/minio كلها UP)**.
44. ثبت `red-debug-lan.apk` → **Success** + `MainActivity` في المقدمة (pid حي) + السجلات نظيفة: `UnifiedNetworkManager initialized` + Telecom مسجل + WebRTC native محمل + صفر FATAL (عدم الدخول متوقع: `no token` قبل التسجيل).
45. الشاشة مقفلة (16KB أسود) — لم أتجاوز القفل (خصوصية). التسجيل يمر: `register → PENDING → اعتماد الأدمن من اللوحة`.

### الجلسة 18 — الدخول بحساب لديه أصدقاء (متحقق بأوامر مباشرة)
46. أنشأت `phonefriend` (82752) عبر admin API (معتمد مباشرة) + صداقة متبادلة مع hdh/hhh/lml عبر DB (3/3).
47. التحكم بالهاتف عبر `uiautomator` (اللقطات سوداء لانطفاء الشاشة): أدخلت البيانات ونقرت الدخول — الواجهة تعرض `@phonefriend` + `82752` + العد `3` + الأصدقاء الثلاثة. **الدخول ناجح والأصدقاء ظاهرون**.
### الجلسة 6 — عقود حقيقية للكاش/الهجرات + صِدق البيانات + إصلاح بوابة التوازن (متحقق بأوامر مباشرة)
17. تصحيح عقود API للأدمن (كانت تنادي `/api/system/*` و`/api/security/*` بلا مقابل — الجذر الفعلي `/api/admin/*`): `useAuditLog→/admin/audit`، `useBackups` + 3 طفرات→`/admin/backups`، `useAllSessions` + `useRevokeSession→/admin/sessions{,/{id}/terminate}`، مع تطبيع إلزامي (`BackupHistory→Backup`: `sizeBytes/storageLocation/backupType`، و`AdminSession→Session`). تحقق: `npm run check` **EXIT 0** + `vite build` **EXIT 0**
18. عقدان جديدان حقيقيان — `AdminSystemOpsController`: `GET /api/admin/system/cache` (Redis `INFO` + `SCAN` مقيّد 500 بلا `KEYS` الحاجب، و`null` صريح لما لا يُقاس) + `POST /api/admin/system/cache/invalidate` (نمط مطلوب، `*`→**400**، `UNLINK` بسقف دفعة، وتدقيق `CACHE_INVALIDATED` إلزامي) + `GET /api/admin/system/migrations` (سجل Flyway؛ غياب الجدول → `available=false` لا 500)
19. أمن: `/api/admin/sessions` لم يعد يُسلسل كيان JPA (كان يُرسل `sessionTokenHash` ضمن الحمولة) — خرائط صريحة بنمط `/users`، فأي حقل جديد لا يُصدَّر إلا بقرار
20. الواجهة: `CachePage` و`MigrationsPage` على البيانات الحقيقية مع `DemoBanner` عند التعذّر + إبطال بنمط + إحصاء TTL من الدفعة المحمّلة؛ `AuditLogPage` تصدير CSV حقيقي (`/api/admin/audit/export`) ببديل محلي معلن؛ `BackupsPage` أعمدة `checksum`/`restores` (كانت في الباك اند ومُهمَلة)؛ لافتة «بيانات وهمية» على Roles/ApiKeys/MFA/SSO/Config + تعطيل «Add Config» بدل الفشل الصامت
21. إصلاح بوابتي الترجمة/الاختبار: `MessageStreamService.reclaimIdle` (استنتاج `R` داخل `runCatching` مع return غير محلي) + `Phase9DataContractsTest` (`mock()` بلا نوع يُسقط مُدقّق Kotlin) ⇒ الباك اند يُترجم وتُبنى مصادر الاختبار. اختبار جديد `AdminSystemOpsControllerTest` → **6/6 خضراء** (`--no-daemon`)
22. `scripts/balance-check.mjs`: دعم حرفي التعبير النمطي (`/\"/g` داخل قالب) — كان يُنتج إيجابية كاذبة على `pages/AuditLog.tsx`. تحقق: `--scan admin_dashboard/src` → **128/0** و`backend-server/src/main/kotlin` → **185/0**، وملف مختل صناعيًا ما زال يُكتشف (لا إضعاف للحارس)

## 4) الخطوة التالية
- ✅ نُفِّذ جزئيًا: دمج الازدواج الملاحي بروابط متقاطعة بين الصفحات العامة (`/security`, `/system`, `/settings`) والصفحات التفصيلية + زر عودة — بلا حذف ولا مسار يتيم بلا مدخل
- ✅ سُدَّت عقود `cache` و`migrations` (الجلسة 6) + لافتات «بيانات وهمية» على المتبقي
- عقود API الناقصة: ✅ **حُلّت جميعها (الجلسة 12)** — `roles`, `api-keys`, `config` (جلسة 11) + `mfa`, `sso` (جلسة 12) بمخطط V68 + مسارات كاملة + اختبارات؛ لم يعد في اللوحة صفحة بلا عقد حقيقي
- ✅ حُلَّا (الجلسة 6): `checksum`/`restoreCount` تظهران في صفحة النسخ، و`sessionTokenHash` لم يعد يُصدَّر
- بند مكافئة متبقٍّ للربط بالشريط بدل الروابط المتقاطعة: دليل الاستعادة التشغيلي في صفحة النسخ الحديثة + تبويب تنبيهات الأمان في صفحة التدقيق الحديثة
- ⚠️ بيئة: `npm run lint` لا يعمل محليًا (`ESLint 9: structuredClone is not defined` بسبب Node **v16.20.2**) — البوابة في `admin_dashboard/Dockerfile` تحتاج Node ≥18؛ أي إصلاح يجب أن يثبّت إصدار Node لا أن يُعطّل ESLint
### الجلسة 11 — مخازن V68 (إعدادات/أدوار/مفاتيح) + إشراف الحالات للوحة (متحقق بأوامر مباشرة)
28. ترحيل `V68__Admin_Config_Roles_ApiKeys.sql`: جداول `admin_system_config` (مفتاح فريد + إصدار) و`admin_roles` (أدوار نظام مزروعة: SUPER_ADMIN/ADMIN/MODERATOR/VIEWER بصلاحيات JSON نصية) و`admin_api_keys` (SHA-256 فقط + prefix للعرض) — كلها `IF NOT EXISTS` بأسلوب الترحيلات السابقة
29. كيانات JPA (`AdminOpsStores.kt`) + مستودعات في `AdminRepositories.kt` + `AdminOpsStoresController` بعقد مطابق لانواع الواجهة حرفيًا: `GET/PUT/POST/DELETE /api/admin/system/config` (قيمة مطلوبة + حد 409 KEY_EXISTS)، `GET/POST/PUT/DELETE /api/admin/security/roles` (أدوار النظام محمية 403 SYSTEM_ROLE_IMMUTABLE)، `GET/POST/DELETE /api/admin/security/api-keys` (المفتاح الخام `red_sk_…` يُعاد **مرة واحدة** حقل `secret`، يُخزَّن SHA-256) — كل عمليات التأثير بتدقيق `admin_audit_log` إلزامي
30. إشراف الحالات للوحة (المكافئ الإداري لتكافئ واتساب في الجلسة 10): `AdminStoriesController` فوق مجموعة Mongo نفسها — `GET /api/admin/stories/overview` (نشطة/مخفية/مشاهدات 24h) + `GET /api/admin/stories` (صفحات + بحث بالمالك/RedID + إدراج المخفية/المحذوفة اختياريًا) + `POST …/hide` و`unhide` (تعمية دون حذف الوسائط، قابلة للتراجع) + `DELETE …/{id}` (حذف نهائي + تنظيف MinIO + المشاهدات) — تدقيق `STORY_HIDDEN/UNHIDDEN/DELETED` فئة MODERATION خطورة WARNING إلزامي
31. الواجهة: `queries.ts` أعيد توجيهها للعقود الحقيقية الثلاثة + طفرات `useCreateConfig/useDeleteConfig` + `secret` من استجابة إنشاء المفتاح؛ `ConfigPage` (حذف فعلي + إضافة مفعلة) و`RolesPage` و`ApiKeysPage` انخلت جميع بياناتها الوهمية ولافتاتها — عرض حقيقي 100%؛ `Role.permissions` صارت `string[]` مطابقة للباك اند
32. تبويب «الحالات» في `ContentManagement` (أول التبويبات): إحصاءات ثلاثية + بحث بالمالك + إظهار المخفية + جدول (المالك/النوع/الجمهور/المشاهدات/الانتهاء/الظهور) + إخفاء/إظهار فوري + حذف بحوار تأكيد — عبر دوال `getAdminStories*` الجديدة في `api.ts`. تحقق: `npm run check` **EXIT 0** + `vite build` **EXIT 0 (25s)** + `AdminStoriesControllerTest` → **6/6 خضراء** (مع النظامية 6/6: 12/12)
- تنويه أندرويد: الحالات في التطبيق كاملة بالفعل (الجلسة 10) — هذه الجلسة سدت الوجه الإداري المفقود فقط

### الجلسة 12 — تفعيل MFA/SSO الحقيقيين + تصفير كل الوهم المتبقي (متحقق بأوامر مباشرة)
33. `AdminSecurityPolicyController` فوق مخزن V68 بلا جداول جديدة: `GET/PUT /api/admin/mfa/policy` (مفاتيح `mfa.*` — إلزام الأدمن/الجميع، الطرق المسموحة مصفّاة بالقائمة البيضاء، نافذة TOTP مقيّدة 0..10، رفض قائمة فارغة) + `GET/PUT /api/admin/security/sso` (مفاتيح `sso.oidc.*` — `clientSecret` **يُكتب فقط ولا يُعاد في القراءة أبدًا**، issuer إلزاميًا HTTPS) + `PUT /security/sso/test/{issuer}`: اختبار اكتشاف **حقيقي** عبر `java.net.http` يجلب `.well-known/openid-configuration` ويتحقق من JSON وحقول authorization/token — النتيجة صريحة (reachable/error/detail) بلا ادعاء نجاح. تدقيق `MFA_POLICY_UPDATED`/`SSO_CONFIG_UPDATED`/`SSO_TEST_OK` فئة SYSTEM خطورة WARNING إلزامي
34. اختبار `AdminSecurityPolicyControllerTest` → **7/7 خضراء**: افتراضات صريحة، حفظ + تدقيق بالمعاملات الصريحة، رفض الطرق الفارغة والنافذة خارج المدى، **عدم تسريب clientSecret في التسلسل**، رفض http://، فشل الاكتشاف يُعلن DISCOVERY_UNREACHABLE بلا شبكة مزيفة
35. الواجهة: `MFAPage` أُعيد كتابته بالكامل (كان 12 مرجع mock + زر Save ميت) — محرر سياسة حقيقي بحالة dirty ورسائل حفظ من الخادم؛ `SSOPage` الحفظ عبر PUT الفعلي وزر Test Connection يشغّل الاكتشاف ويعرض النتيجة التفصيلية؛ تبويبات `Security.tsx` (roles/api-keys/mfa/sso) صارت تقرأ البيانات الحية بدل EmptyState «لا يوجد في الخادم»، ولافتتها الدائمة أصبحت شرطية عند خطأ الجلسات فقط
36. تصفير الوهم المتبقي: حُذف `mockSessions` (100 جلسة مُفبركة) و`mockBackups` (20 نسخة مُفبركة) من Sessions/Backups — بيانات حية + لافتة شرطية عند الفشل؛ `useRevokeAllSessions` (مسار غير موجود في الخادم) استُبدل بـ `useCleanupExpiredSessions` → `POST /admin/sessions/cleanup` الحقيقي بتسمية صادقة «تنظيف الجلسات المنتهية» وحوار موحد؛ لافتات `Analytics`/`System` الدائمة أصبحت شرطية عند الفشل
37. الحفظ الحقيقي للملف الشخصي: `Profile.tsx` و`settings/SettingsPage.tsx` كانا «حفظ محلي فقط»/انتظار 1000ms مزيف — الآن `PATCH /api/auth/profile` الحقيقي (displayName/bio) مع تعطيل الحقول التي لا يدعمها المسار تسميةً صادقة، ورسائل نجاح/فشل صريحة. حوارات `window.confirm` الأخيرة (Config/ApiKeys) استُبدلت بحوار Dialog موحد بنمط PostsManagement. تحقق: `npm run check` **EXIT 0** + `vite build` **EXIT 0 (29s)** + بوابة أندرويد `:app:testDebugUnitTest` (Stor/Call) → **18/18 خضراء** (عدّ XML بنفسي) + balance-check → **189/0 و128/0** + اختبارات الأدمن الإدارية → **19/19**
- حدود صادقة موثقة: تسجيل TOTP للتسليم يتطلب تدفق دخول جديد؛ دخول OIDC الكامل يتطلب تكامل موفر هوية — كلاهما مشروع مستقل؛ أزرار 2FA/Passkey/Export/Import/Delete تبقى معطلة بتسميات صادقة
38. إصلاح انهيار واجهة «Cannot read properties of null (reading 'toFixed')»: الجذر أن `/dashboard/metrics` يرسل `null` صريحًا لنسب التغير (لا بيانات أمس أو prev=0) و`MetricCard` كان يفحص `!== undefined` فقط فينهار على `null.toFixed` — الآن `change != null && Number.isFinite(change)`. تحصين شامل لنمط واحد: `formatBytes` تقبل null/Infinity وتعرض «—»، `CachePage` تعمّم أرقام memory القادمة من الخادم بـ `Number(?? 0)` (فشل INFO جزئي)، `usageCount` غير موجود في العقد الحقيقي يُعرض «—» بدل `null.toLocaleString`. تحقق: `npm run check` **EXIT 0** + `vite build` **EXIT 0 (25s)**

- `Redis Streams` بدل pub/sub + عامل `transcode` (أو إزالة الادعاء) + `Light.primaryContainer` الوردي
- البوابتان الكاملتان (أُعيد تشغيلهما في الجلسة 4: 341/0/0 و314/0/0) — يُبقى الفحص آليًا قبل أي دفعة
- أرشفة الميت (`host-debug/lan/temp-dc/nginx-simple/monitoring-prometheus`) بعد اخضرار البوابتين + دمج توثيق الجذر

### الجلسة 20 — تشخيص فشل المكالمات + إصلاح TURN + إحياء Docker (متحقق بأوامر مباشرة)
48. سجل المكالمات أثبت محاولات حقيقية من الهاتف (VIDEO_1V1 إلى hhh + 3 مؤتمرات GROUP_VIDEO) كلها MISSED — الإشارة والتاريخ يعملان، والأطراف offline (حسابات اختبار بلا أجهزة). هذا يفسر شكوى «لا أستطيع إجراء المكالمات».
49. علة حقيقية وصلحت: ICE/TURN كانت تعلن IP قديما ميتا (`10.153.161.42`) — حدثت `.env` (CLIENT_LAN_IP/TURN_PUBLIC_HOST/MEDIASOUP_ANNOUNCED_IP ← `192.168.1.67`) + أُعيد إنشاء backend/coturn/sfu. تحقق: `/health UP` + `ice-servers` تعلن `stun:192.168.1.67:3478`.
50. أزمة قرص C: (191MB حرة أسقطت Docker Desktop كليا): نظفت مخرجات الجلسات المؤقتة + `red-app/build` عبر `gradlew clean` + APK قديم متجاوَز. Docker أُعيد تشغيله — **10/10 حاويات healthy**. تحذير: `opencode.db` (15.5GB) + C: ما زال شبه ممتلئ — يلزم تنظيف أوسع خارج النطاق.

### الجلسة 21 — ست فرق متوازية + إنقاذ البناء (متحقق بأوامر مباشرة)
51. باك-إند runtime: 7 علل (قناة Redis ميتة + حضور مزيف + أشباح WS + انتهاء صامت + ترتيب dropPending + v1 ميت + ICE ثابتة) أُصلحت + 16 اختبارا جديدا. تحقق: **67 ملفا / 358 اختبارا / 0 فشل** (عدّ XML بنفسي).
52. وجهات المكالمات: جدول 8 أنواع (حية/ميتة موسومة) + زر مشاركين المؤتمر (عدّاد + حوار قراءة). تحقق: **19 ملفا / 70 اختبارا / 0 فشل**.
53. الدردشة: حارس فراغ الإرسال + 4 أنواع ملفات مربوطة (صوت/جهة/موقع/ملصق جماعي) + زر الإرسال يعمل. المجموعات: سبب الاختفاء (رد فارغ عابر + سباق تحميل + أشباح كاش) أُصلح بمقابر tombstones + **42/42**. الأيقونات: `icon_unification.xml` بلا حذف.
54. إنقاذ البناء: جلسات متوازية كسرت 5 ملفات (PulseAvatar مكرر/Atomic/stray/duplicated-fragment/Zoom startZoom) — أصلحتها كلها. تحقق: `compileDebugKotlin` → **BUILD SUCCESSFUL**.
