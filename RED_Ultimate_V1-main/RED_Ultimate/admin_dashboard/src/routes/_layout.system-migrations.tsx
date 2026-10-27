// ⚠️ 2026-09-26: مسار موصول بلا فهرس في الشريط — الصفحة بياناتها وهمية بالكامل (لا Migrations API).
import { createFileRoute } from '@tanstack/react-router';
import { MigrationsPage } from '@/pages/system/MigrationsPage';

export const Route = createFileRoute('/_layout/system-migrations')({
  component: MigrationsPage,
});
