// ⚠️ 2026-09-26: بديل حديث لـ `/audit` (antd) — مُوصول لكن غير مفهرس في الشريط
// حتى تكتمل المكافئة (المفقود: تصدير CSV + تبويب تنبيهات الأمان). لا تنقل الفهرس قبلها.
import { createFileRoute } from '@tanstack/react-router';
import { AuditLogPage } from '@/pages/system/AuditLogPage';

export const Route = createFileRoute('/_layout/system-audit-log')({
  component: AuditLogPage,
});
