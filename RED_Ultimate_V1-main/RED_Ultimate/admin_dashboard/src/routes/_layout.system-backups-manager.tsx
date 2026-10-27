// ⚠️ 2026-09-26: بديل حديث لـ `/backups` (antd) — مُوصول لكن غير مفهرس في الشريط
// حتى تكتمل المكافئة (المفقود: دليل الاستعادة التشغيلي + عرض checksum). لا تنقل الفهرس قبلها.
import { createFileRoute } from '@tanstack/react-router';
import { BackupsPage } from '@/pages/system/BackupsPage';

export const Route = createFileRoute('/_layout/system-backups-manager')({
  component: BackupsPage,
});
