# docs/ci — الحارس الحي الوحيد للـCI

> أنشئ 2026-09-26 لإصلاح رابط ميت (كانت 4 READMEs تحيل إلى `W0_MODULE_BOUNDARIES.md` و`docs/ci/` غير الموجودة).

## البوابات القانونية
```bash
# الأندرويد (من جذر RED_Ultimate)
./gradlew :app:testDebugUnitTest   # وحدة JVM — حالياً 327 اختبار، 3 فشل (SovereignGlassPolicyTest)
./gradlew :app:lintDebug
./gradlew :app:assembleDebug

# الباك-إند (مستقل عمداً)
cd backend-server && ./gradlew test  # حالياً 305 اختبار، 5 فشل (CallWebSocketHandlerTest)
```
- `qa/ci/qualityGate` في `build.gradle.kts` تجمع الاثنين + `ktlintCheck` + `:fast-lint`.
- `core/feature/lib` مستثناة عمداً (خارج `settings.gradle.kts`) — اختباراتها لا تُنفذ ولا تحسب في التغطية.
- لا نقل مجلدات قبل `EXIT=0` للبوابتين.

## قواعد الموارد
- JDK 21 + Node 24 + Postgres 16 + Mongo 8 + Redis 7 (مثبتة من Dockerfiles والكومبوز، لا 16/20).
- `monitoring/prometheus.yml` مهجور — Canonical هو `infrastructure/observability/prometheus/prometheus.yml`.
