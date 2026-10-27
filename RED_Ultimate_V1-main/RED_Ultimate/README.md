# RED Sovereign / YOUNES — منصة المراسلة السيادية

منصة مراسلة E2EE كاملة (تطبيق + باك-إند + لوحة أدمن + SFU) تنافس واتساب وتلجرام مع خصوصية أعلى. هذه الصفحة هي **نقطة الدخول الرسمية** — محدّثة 2026-09-26 بعد أول دورة تحقق حي كاملة (ترجمة + 327 اختباراً + أول APK).

## البنية

```text
red-app/            تطبيق Android (Jetpack Compose) — :app في settings.gradle.kts
backend-server/     Kotlin Spring Boot — بناء Gradle مستقل عمداً (له gradlew خاص)
admin_dashboard/    React 19 + TypeScript + Vite — 28 صفحة
media-sfu/          mediasoup SFU (مكالمات جماعية/بث)
shared-proto/       Protobuf الموحد بين الطرفين
nginx.conf          عكس الوكيل — 8088→80، 8443→443
```

مجلدات مرجعية خارج البناء (Signal التاريخية وغيرها): وجودها لا يعني أنها تدخل البناء — المرجع الحاسم `settings.gradle.kts`.

## المتطلبات

| الأداة | النسخة |
|---|---|
| JDK | 21 (Temurin مُتحقق) |
| Android SDK | عبر `local.properties` |
| Node | 16 يعمل (بحمولات توافق موثقة) — يُنصح 20 LTS |
| Docker | للتشغيل المجمّع |

## البناء والتشغيل (مجرَّب 2026-09-26)

```bash
# التطبيق
./gradlew :app:compileDebugKotlin :app:testDebugUnitTest   # 327 اختباراً
./gradlew :app:assembleDebug                                # APK debug
RED_TARGET_ABI=arm64-v8a ./gradlew :app:assembleRelease     # release نحيف موقّع تلقائياً

# الباك-إند (مستقل)
cd backend-server && ./gradlew compileKotlin                # أو bootRun

# لوحة الأدمن
cd admin_dashboard && npm install && npm run build          # tsc + dist/
```

## البورتات (سلسلة مدققة طرفاً-لطرف)

```text
التطبيق → :8088 → nginx(80/443) → backend:8080
                              └→ /sfu → media-sfu:4000 → WebRTC 40000-40100/udp
```

- الباك-إند داخلياً 8080 (`SERVER_PORT` يوحّد التعارض التاريخي 8088/8080)
- العميل يشتق `ws://IP:8088/sfu` → تذكرة موقعة بسر مشترك → WebRTC

## الميزات

| المجال | الحالة |
|---|---|
| المراسلة E2EE (Signal + Post-Quantum) | ✅ كاملة |
| المكالمات: فردية/جماعية/مؤتمر/بث/مساحات/P2P LAN | ✅ كاملة |
| الاستطلاعات المتقدمة: مجهول + متعدد + مؤقت — عبر E2EE | ✅ كاملة |
| نظام الزجاج السيادي 4-أبعاد (نمط/شدة/أداء/تباين) + Haze 1.7.3 | ✅ |
| الرسائل المؤقتة/المجدولة/التفاعلات/النجوم/التثبيت | ✅ |
| لوحة الأدمن 28 صفحة موصولة فعلياً + bulk + endpoints حية | ✅ |
| الأيقونة adaptive (زمرد/ذهبي + monochrome للماتيريال يو) | ✅ |
| Wear OS / MLS / SSO | ⏳ تتطلب مكتبات خارجية |

## الأمان

- **أسرار**: `.env` و`keystore.properties` و`secrets/` **غير مرفوعة** — يغطيها `.gitignore` الجديد. لا تحمّلها أبداً.
- **التوقيع**: release يوقَّع تلقائياً بمفتاح محلي (`red-app/signing/red-release.p12`)، أو عبر `RED_KEYSTORE_*` خارجياً.
- **أدوار الأدمن**: OWNER/ADMIN/MOD/VIEWER — كل مسارات `/api/admin/**` محمية `hasRole`.

## التوثيق

- `docs/reports/` — تقارير الجلسات الشاملة (أحدثها: الجزء 2 + الجزء 3)
- `docs/ci/` — الحارس الوحيد الحي للـCI + قواعد الموارد
- `docs/archive/` — كل شيء القديم محفوظ (لا حذف ضائع)
- `scripts/check-icon-integrity.sh` — حارس الأيقونة
