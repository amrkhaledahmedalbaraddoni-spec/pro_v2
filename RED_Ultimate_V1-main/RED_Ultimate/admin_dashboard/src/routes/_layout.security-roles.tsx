// ⚠️ 2026-09-26: مسار موصول لكن غير مفهرس في الشريط لعدم وجود عقد في الباك اند
// (لا `roles` API). عند إنشائه: أضِف عنصرًا في `navItems` بمفتاح `navigation.*` في اللغتين.
import { createFileRoute } from '@tanstack/react-router';
import { RolesPage } from '@/pages/security/RolesPage';

export const Route = createFileRoute('/_layout/security-roles')({
  component: RolesPage,
});
