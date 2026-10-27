# فهرس التوثيق — الحالة الوحيدة الموثوقة (2026-09-26)

> القاعدة: `docs/` = ملفات `*.md` فقط. السجلات (`*.log/*.txt/*.patch`) مكانها `docs/reports/<date>/raw/`.
> أي وثيقة بلا تحقق بناء تُوسم **مرجع — غير متحقق**.

## وثائق الجذر القانونية (تُقرأ أولا)

| الوثيقة | الحالة | ملاحظة |
|---|---|---|
| `README.md` | قانونية (بوابة) | نقطة الدخول — محدثة 2026-09-26 |
| `RED_MASTER_PLAN_AR.md` | قانونية (التنفيذ) | سجل الجلسات + ما تحقق على القرص |
| `W0_MODULE_BOUNDARIES.md` | قانونية | حدود الوحدات (أُنشئت لإصلاح 4 روابط ميتة) |
| `API_REFERENCE.md` | قانونية | مولدة من الكنترولرز |
| `DEPLOY.md` | قانونية | النشر عبر `local-first-run` |
| `CURRENT_VERSION_AR.md` | مرجع | قاعدة الدمج ما زالت سارية |
| `STATUS_FINAL_UNIFIED.md` | مرجع — غير متحقق | أرقام قديمة (10 خدمات، 9 مسارات WS، PSTN) تجاوزها الكود |
| `CHANGELOG-ULTIMATE.md` / `GUIDE_AR.md` / `MASTER_GUIDE.md` / `CONTRIBUTING.md` | مرجع | تُدمج لاحقا في `STATUS.md` واحد |

## المجلدات

| المجلد | الحالة |
|---|---|
| `docs/ci/README.md` | قانونية (البوابات: 341 تطبيق + 324 باك خضراء) |
| `docs/reports/2026-09-26/` | سجلات التحقق (STATUS + لوغات الاختبار) |
| `docs/archive/compose-2026-09-26/` | أرشيف الميت (ORIGIN.txt يوثق كل نقل) |
| `docs/archive/` (الباقي) + `docs/reports/archive/` | أرشيف مكرر — يُدمج لاحقا |
| `docs/diagnostics/` (`*.log/*.patch`) | تشخيص عمليات — يُنقل إلى `reports/<date>/raw/` لاحقا |
| `docs/operations/` + `docs/recovered/` | شبه فارغين — يُدمجا لاحقا |
