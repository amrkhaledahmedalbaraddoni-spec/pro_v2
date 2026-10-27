// ⚠️ 2026-09-26: مسار موصول بلا فهرس في الشريط — لا `api-keys` API في الباك اند بعد.
import { createFileRoute } from '@tanstack/react-router';
import { ApiKeysPage } from '@/pages/security/ApiKeysPage';

export const Route = createFileRoute('/_layout/security-api-keys')({
  component: ApiKeysPage,
});
