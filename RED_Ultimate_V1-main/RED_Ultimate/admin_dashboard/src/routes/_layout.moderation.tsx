import { createFileRoute } from '@tanstack/react-router';
import { ModerationPage } from '@/pages/Moderation';

export const Route = createFileRoute('/_layout/moderation')({
  component: ModerationPage,
});