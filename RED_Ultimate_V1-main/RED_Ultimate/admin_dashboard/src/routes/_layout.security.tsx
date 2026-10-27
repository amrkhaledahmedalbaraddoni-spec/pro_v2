import { createFileRoute } from '@tanstack/react-router';
import { SecurityPage } from '@/pages/Security';

export const Route = createFileRoute('/_layout/security')({
  component: SecurityPage,
});