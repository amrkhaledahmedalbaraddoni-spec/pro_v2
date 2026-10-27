# جرد الحالة والخلل — 2026-09-26 (جولة فحص ساكن)

> **نطاق هذا التقرير:** فحص ساكن (قراءة ملفات + grep) فقط. لم يُشغَّل أي بناء أو اختبار أو حاوية في هذه الجولة.
> **سبب عدم التشغيل:** أداة التنفيذ المفوضة (ZCode) فشلت مرتين هذه الجولة بـ `Model request failed`، ولم تُجرَ أي تعديلات.
> كل بند أدناه مصنّف: **Observed** (مرصود مباشرة بالمصدر) أو **Candidate** (احتمال يحتاج إثباتًا) أو **Unverified** (لم يُعد التحقق منه).

---

## 1. ما هو مؤكد أنه سليم (حتى لا نبني خطة على ادعاء خاطئ)

| البند | الدليل | الحالة |
|---|---|---|
| عقد الأدمن ↔ الباك اند سليم على المستوى الخشن | 38 مسار `/api` حرفي في `admin_dashboard/src` — **كلها** لها مقابل في 389 تعليق Mapping بالباك اند | Observed |
| التنقّل في لوحة الأدمن متوافق مع المسارات | 24 عنصر تنقّل في `src/routes/_layout.tsx` ↔ 26 ملف route في `src/routes/` | Observed |
| لا وسوم TODO/FIXME في `admin_dashboard/src` | عدّ حساس لحالة الأحرف | Observed |
| TODO حقيقي واحد فقط في الباك اند | `backend-server/.../social/ChannelService.kt:347` | Observed |
| مسار المكالمات موثّق ومفصول v1/v2 | `calls/v1/CallsV1Controller`, `UnifiedCallsControllerV2`, `ScheduledCallController` | Observed |

> **تصحيح ذاتي:** العدّ الأولي أظهر «27 TODO في الباك اند» — كان **خطأ أداة** (المطابقة الافتراضية غير حساسة لحالة الأحرف فطابقت `.toDouble()`). العدد الحقيقي = **1**.

---

## 2. العيوب المرصودة (Observed) — مرتّبة بالأثر

### 2.1 لوحة الأدمن: 11 وحدة صفحة «يتيمة» (كود ميت غير موصول)
ملفات موجودة في `admin_dashboard/src/pages/**` ولا يستوردها أي route ولا أي ملف آخر — أي **غير قابلة للوصول من الواجهة إطلاقًا**:

| # | الملف | ملاحظة |
|---|---|---|
| 1 | `src/pages/security/ApiKeysPage.tsx` | تنفيذ ثانٍ غير موصول |
| 2 | `src/pages/security/MFAPage.tsx` | تنفيذ ثانٍ غير موصول |
| 3 | `src/pages/security/RolesPage.tsx` | تنفيذ ثانٍ غير موصول |
| 4 | `src/pages/security/SessionsPage.tsx` | تنفيذ ثانٍ غير موصول |
| 5 | `src/pages/security/SSOPage.tsx` | تنفيذ ثانٍ غير موصول |
| 6 | `src/pages/system/AuditLogPage.tsx` | مكرر مع `pages/AuditLog.tsx` |
| 7 | `src/pages/system/BackupsPage.tsx` | مكرر مع `pages/Backups.tsx` |
| 8 | `src/pages/system/CachePage.tsx` | غير موصول |
| 9 | `src/pages/system/ConfigPage.tsx` | غير موصول |
| 10 | `src/pages/system/MigrationsPage.tsx` | غير موصول |
| 11 | `src/pages/settings/SettingsPage.tsx` | مكرر مع `pages/Settings.tsx` |

**الدليل:** مسح كل `src` لنمط `pages/` لم يُظهر أي استيراد لهذه الملفات؛ ولا توجد `import()` ديناميكية أو `lazy()` في المشروع.
**الأثر:** تعارض/تشتّت — تنفيذان لنفس الشاشة، واحد فقط حيّ. هذا يفسّر إحساس «كل شي متداخل وناقص».
**ملاحظة:** لا أعرف أيّهما «الصحيح» بعد؛ يجب مقارنة الزوجين المكرّرين قبل الحذف (لا حذف أعمى).

### 2.2 قاعدتا بيانات للهجرات (Flyway) في المشروع
`backend-server/src/main/resources/db/migration` (72 ملفًا، آخرها **V67**) + مجلد ثانٍ: `voicebridge/src/main/resources/db/migration/V1__create_stasis_app_config.sql`.
**الأثر:** خطر تعارض إصدارات/موقعين لنفس مُدار الهجرات. **Candidate** لخلل تشغيلي عند تفعيل voicebridge.

### 2.3 تعارض منافذ المضيف: coturn يستولي على 80/443
`docker-compose.yml`: خدمة `coturn` تنشر `80/tcp` و`443/tcp` و`3478` و`5349`، بينما `nginx` ينشر `${RED_HTTP_PORT:-8088}:80` و`${RED_HTTPS_PORT:-8443}:443`.
**الأثر:** على أي مضيف حقيقي، 80/443 محجوزان لـ coturn؛ لا يمكن لـ nginx استخدام المنافذ القياسية. **Observed** (تعارض تصميمي) — يحتاج قرار نشر.

### 2.4 تطبيق أندرويد: 14 علامة TODO/FIXME حقيقية — أبرزها ميزات غير مكتملة
| الملف:سطر | المعنى |
|---|---|
| `calls/LiveKitBridge.kt:116,133,145,156,160,166` | **جسر LiveKit وهمي/مُعلّق** — لا يوجد `io.livekit:livekit-android` ولا اتصال فعلي |
| `crypto/GroupCrypto.kt:49` | MLS **stub** لمجموعات E2EE |
| `features/channels/ChannelMode.kt:136,170` | بحث القنوات غير موصول بـ `GET /api/channels?search=` + debounce |
| `workers/SovereignWorkers.kt:21` | `MessageRetryWorker` ناقص |
| `core/Redis/…RedDao.kt:186`, `core/database/Entities.kt:90`, `core/RedConnectionService.kt:1163` | ربط المجموعات/المواضيع المحلية غير مكتمل |

هذه **مرشّحات مؤكدة** لـ«ميزات شكلها موجودة وعملها ناقص» — وهي بالضبط ما شكا منه المستخدم.

### 2.5 وزن ومخلفات المستودع
| المجلد | الحجم | ملاحظة |
|---|---|---|
| `red-app` | **4196.9 MB** | أغلبها `build/` |
| `admin_dashboard` | 339.3 MB | `node_modules` + `dist` |
| `backend-server` | 126.3 MB | `build/` |
| `prebuilt-backend` | **111.8 MB** | حزمة مبنية مسبقًا — مصدر انحراف (راجع مشكلة «الصورة القديمة» في تقرير 2026-09-26) |
| `backups` | 51.3 MB | نسخ مركونة |
| `archive`, `integration_patches`, `local-maven`, `kamailio`, `janus`, `demo` | صغيرة | وزن ميت/مرجعي |
| `docs` | 108 ملف md/txt بلا فهرسة موحّدة | تشتّت توثيق |

### 2.6 حالة لم يُعَد التحقق منها (Unverified)
`docs/AUDIT_2026-09-26.md` يدّعي: بناء `:app`، 327/327 اختبار وحدة، 307/307 اختبار باك اند، بناء admin كامل، 10 حاويات healthy، Flyway v67.
**لم يُعَد تشغيل أي من ذلك في هذه الجولة** — لذا يُعامل كـ **self-report** لا كحقيقة مُتحقَّق منها. أول مهمة في الخطة إعادة إنتاج هذه الأرقام.

---

## 3. فرضيات مرشّحة (Candidate) تحتاج إثباتًا قبل أي إصلاح
1. تفعيل MLS/جلسات المجموعات يفشل فعليًا لأن `GroupCrypto` stub → يظهر كفشل صامت في الواجهة.
2. مكالمات الفيديو عبر LiveKit لا تعمل لأن الجسر غير موصول (لكن بناء `:app` ينجح لأن الـ stub يترجم).
3. بحث القنوات في التطبيق لا يعمل (واجهة موجودة بلا نداء شبكي).
4. `voicebridge` عند تشغيله يُسقط Flyway بسبب `V1` مكرر.

## 4. فجوات أدلة
- لا جهاز/محاكي أندرويد → لا تحقق سلوكي للواجهات.
- لا تشغيل حاويات في هذه الجولة → لا تحقق حيّ من PG/Mongo/Redis/MinIO.
- لم تُقرأ قيم `.env` (سياسة أمن) → لا مقارنة مفاتيح فعلية.
- أيّ من الصفحات المكرّرة هو المعتمد (يحتاج مقرر بشري).
