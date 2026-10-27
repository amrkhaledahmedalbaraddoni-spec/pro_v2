// ⚠️ 2026-09-26: بديل حديث لـ `/settings` (محلي بالكامل بلا API) — مُوصول لكن غير مفهرس
// حتى تُحسم المكافئة (المفقود: لوحة الألوان + الإشعارات + تصدير/استيراد البيانات). لا تنقل الفهرس قبلها.
import { createFileRoute } from '@tanstack/react-router';
import { SettingsPage as AdvancedSettingsPage } from '@/pages/settings/SettingsPage';

export const Route = createFileRoute('/_layout/settings-advanced')({
  component: AdvancedSettingsPage,
});
