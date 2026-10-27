// ⚠️ 2026-09-26: مسار موصول بلا فهرس في الشريط — لا `config` API في الباك اند بعد.
import { createFileRoute } from '@tanstack/react-router';
import { ConfigPage } from '@/pages/system/ConfigPage';

export const Route = createFileRoute('/_layout/system-config')({
  component: ConfigPage,
});
