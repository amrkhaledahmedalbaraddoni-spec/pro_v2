import { createFileRoute } from '@tanstack/react-router';
import { CallsMonitorPage } from '@/pages/CallsMonitor';

export const Route = createFileRoute('/_layout/calls-monitor')({
  component: CallsMonitorPage,
});