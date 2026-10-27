import { createFileRoute } from '@tanstack/react-router';
import { AnalyticsPage } from '@/pages/Analytics';

export const Route = createFileRoute('/_layout/analytics')({
  component: AnalyticsPage,
});