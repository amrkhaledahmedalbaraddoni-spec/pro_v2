// ⚠️ 2026-09-26: مسار موصول بلا فهرس في الشريط — الصفحة بياناتها وهمية بالكامل (لا Cache API).
import { createFileRoute } from '@tanstack/react-router';
import { CachePage } from '@/pages/system/CachePage';

export const Route = createFileRoute('/_layout/system-cache')({
  component: CachePage,
});
