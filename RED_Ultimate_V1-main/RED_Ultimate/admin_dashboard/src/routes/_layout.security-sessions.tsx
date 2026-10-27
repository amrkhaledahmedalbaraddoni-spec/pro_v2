import { createFileRoute } from '@tanstack/react-router';
import { SessionsPage } from '@/pages/security/SessionsPage';

export const Route = createFileRoute('/_layout/security-sessions')({
  component: SessionsPage,
});
