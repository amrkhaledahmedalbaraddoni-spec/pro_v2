// ⚠️ 2026-09-26: مسار موصول بلا فهرس في الشريط — الصفحة بياناتها وهمية بالكامل (لا MFA API).
import { createFileRoute } from '@tanstack/react-router';
import { MFAPage } from '@/pages/security/MFAPage';

export const Route = createFileRoute('/_layout/security-mfa')({
  component: MFAPage,
});
