import { createFileRoute, redirect } from '@tanstack/react-router';

/**
 * جذر اللوحة: إعادة توجيه واحدة إلى لوحة القيادة بدل شاشة 404.
 * المسار pathless تحت `_layout` كي يبقى الهيكل (الشريط الجانبي/الرأس)
 * ظاهرًا في أثناء التحويل.
 */
export const Route = createFileRoute('/_layout/')({
  beforeLoad: () => {
    throw redirect({ to: '/dashboard' });
  },
});
