# أرشيف: هيكل لوحة الإدارة الميت (`App.tsx`)

**تاريخ الأرشفة:** 2026-09-26
**الملف المؤرشف:** `admin_dashboard/src/App.tsx` (549 سطراً)
**السبب:** كان ميتاً تماماً *و* يكسر بناء اللوحة.

## الدليل (مُتحقَّق منه بالتشغيل، لا بالقراءة فقط)

1. **لا شيء يستورده.** نقطة الدخول الفعلية هي `admin_dashboard/index.html` → `/src/index.tsx`،
   و`index.tsx` يعرض `<RouterProvider router={router} />` من `src/router.ts` +
   `src/routeTree.gen.ts` (TanStack file routes). لا يوجد أي `import ... from './App'`
   ولا `<App` في المشروع (فحص `grep -rn` على كامل `src/` و`index.html`).
2. **يفشل فحص الأنواع.** كان يستورد 9 وحدات غير موجودة أصلاً:
   `pages/UserManagement`, `pages/NotificationsCenter`, `pages/SystemLogs`, `pages/MediaCenter`,
   `pages/MessagingCenter`, `pages/InfrastructureCenter`, `pages/ModerationCenter`,
   `pages/tabs/AuthorityTab`, `pages/tabs/SecurityTab`.
3. **يفشل بناء Docker.** `admin_dashboard/Dockerfile` يشغّل `npm run lint && npm run build`،
   و`npm run build` = `tsc --noEmit && vite build` ⇒ كانت الصورة `red-admin-ui:local`
   غير قابلة للبناء على Linux (خط الأساس `docs/reports/2026-09-26/baseline-admin.log`: `ADMIN_TS_EXIT=2`).

## لماذا الأرشفة وليس الحذف

طلب المشروع صريح: لا حذف لأي شيء. الملف محفوظ كما هو هنا، ويُستعاد بأمر واحد:

```bash
mv archive/admin-dead-shell/App.tsx admin_dashboard/src/App.tsx
```

## ما الذي حلّ مكانه

المسار الحيّ الوحيد للوحة هو TanStack Router:

- `src/routes/*.tsx` — الصفحات المُوجَّهة (كل ملف = مسار).
- `src/routes/_layout.tsx` — الهيكل والقائمة الجانبية (المصدر الوحيد للتنقّل).
- `src/routes/__root.tsx` — المزوّدات (QueryClientProvider, Theme, Auth, Sidebar, Socket).

أي «صفحة» لا تظهر في هذا الهيكل فهي غير قابلة للوصول، وذلك يُصلح بإضافة Route حقيقي
لا بإحياء هيكل موازٍ. `src/theme.ts` بقي في مكانه لأنه مستخدم فعلاً
(`pages/BrowserSettings.tsx` يستورد `YNS_STATUS_COLORS` منه).
