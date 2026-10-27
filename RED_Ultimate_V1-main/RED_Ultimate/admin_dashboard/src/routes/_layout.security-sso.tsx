// ⚠️ 2026-09-26: مسار موصول بلا فهرس في الشريط — ينادي `/api/admin/security/sso` وهو غير موجود في الباك اند.
import { createFileRoute } from '@tanstack/react-router';
import { SSOPage } from '@/pages/security/SSOPage';

export const Route = createFileRoute('/_layout/security-sso')({
  component: SSOPage,
});
