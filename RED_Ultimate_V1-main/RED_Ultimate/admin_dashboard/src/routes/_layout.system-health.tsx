import { createFileRoute } from '@tanstack/react-router';
import { SystemHealthPage } from '@/pages/SystemHealth';

export const Route = createFileRoute('/_layout/system-health')({
  component: SystemHealthPage,
});