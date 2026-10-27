import { createFileRoute } from '@tanstack/react-router';
import BrowserSettings from '@/pages/BrowserSettings';

export const Route = createFileRoute('/_layout/browser-settings')({
  component: BrowserSettings,
});
