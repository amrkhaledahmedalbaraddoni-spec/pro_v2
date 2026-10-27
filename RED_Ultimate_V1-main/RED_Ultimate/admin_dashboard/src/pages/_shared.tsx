'use client';

import { useEffect, type ReactNode } from 'react';
import { useNavigate } from '@tanstack/react-router';
import { useAuth } from '@/components/providers/AuthProvider';
import { useTranslation } from 'react-i18next';
import { AlertTriangle, Inbox, Loader2 } from 'lucide-react';
import { Button } from '@/components/ui/button';
import { cn } from '@/utils/cn';

/**
 * ✅ 2026-09-26 — حارس صفحات مشترك (احتياطي للصفحات اليتيمة فقط).
 * كل الصفحات المربوطة بالراوتر أصبحت أبناء `_layout` (ملفات `_layout.*`)،
 * فيحميها `beforeLoad` في `_layout` ويغلّفها بشريطه الجانبي عبر `Outlet`.
 * هذا الحارس يُستخدم فقط عند ربط الصفحات اليتيمة الـ11 لاحقاً لحين إدماجها.
 */
export function RequireAuth({ children }: { children: ReactNode }) {
  const { isAuthenticated, isLoading } = useAuth();
  const navigate = useNavigate();

  useEffect(() => {
    if (!isLoading && !isAuthenticated) {
      navigate({ to: '/login', replace: true });
    }
  }, [isLoading, isAuthenticated, navigate]);

  if (isLoading) {
    return (
      <div className="flex min-h-[40vh] items-center justify-center" role="status" aria-live="polite">
        <Loader2 className="h-8 w-8 animate-spin text-primary" aria-hidden="true" />
        <span className="sr-only">Loading</span>
      </div>
    );
  }
  if (!isAuthenticated) return null;
  return <>{children}</>;
}

/**
 * شريط خطأ مضمّن — للفشل الجزئي داخل صفحة قد تعرض باقي بياناتها الحقيقية.
 * كان الاسم `DemoBanner` ويضع شارة «بيانات تجريبية» فوق رسائل خطأ حقيقية،
 * وهذا تضليل صريح. الآن الشكل والدلالة خطأ، ولا وجود لأي بيانات وهمية.
 */
export function ErrorBanner({ message }: { message: string }) {
  return (
    <div
      role="alert"
      className="flex items-center gap-2 rounded-lg border border-yn-error/40 bg-yn-error/10 px-4 py-2 text-sm font-medium text-foreground"
    >
      <AlertTriangle className="h-4 w-4 shrink-0 text-yn-error" aria-hidden="true" />
      <span>{message}</span>
    </div>
  );
}

export function LoadingState({ label }: { label?: string }) {
  const { t } = useTranslation();
  return (
    <div className="flex items-center justify-center gap-2 py-12" role="status" aria-live="polite">
      <Loader2 className="h-5 w-5 animate-spin text-primary" aria-hidden="true" />
      <span className="text-sm font-medium text-foreground">{label ?? t('common.loading')}</span>
    </div>
  );
}

/**
 * حالة فراغ موحّدة.
 *
 * توسيع متوافق رجعياً: كانت تقبل `message/action` فقط، وصفحات كثيرة
 * (CallsMonitor, Dashboard, Users, Analytics, Moderation, System) تمرّر
 * `title/description/icon` فكان فحص الأنواع يفشل. الآن تقبل الشكلين
 * معاً بلا نسخة ثانية من المكوّن (منع التكرار).
 */
export function EmptyState({
  message,
  title,
  description,
  icon,
  action,
}: {
  message?: string;
  title?: string;
  description?: string;
  icon?: ReactNode;
  action?: ReactNode;
}) {
  const { t } = useTranslation();
  const heading = title ?? message ?? t('common.empty');
  return (
    <div className="flex flex-col items-center gap-2 py-12 text-center">
      {icon ?? <Inbox className="h-10 w-10 text-foreground/60" aria-hidden="true" />}
      <p className="font-medium text-foreground">{heading}</p>
      {description && <p className="text-sm text-foreground/70">{description}</p>}
      {action}
    </div>
  );
}

export function ErrorState({ message, onRetry }: { message?: string; onRetry?: () => void }) {
  const { t } = useTranslation();
  return (
    <div className="flex flex-col items-center gap-3 py-12 text-center" role="alert">
      <AlertTriangle className="h-10 w-10 text-red-600" aria-hidden="true" />
      <p className="font-medium text-foreground">{message ?? t('common.loadError')}</p>
      {onRetry && (
        <Button variant="outline" size="sm" onClick={onRetry}>
          {t('common.retry')}
        </Button>
      )}
    </div>
  );
}

/** نص آمن — يرجع '—' بدل undefined/null/فارغ عند الحقول الناقصة. */
export function formatSafeText(value: unknown): string {
  if (typeof value === 'number') return Number.isFinite(value) ? String(value) : '—';
  const s = String(value ?? '').trim();
  return s || '—';
}

/** رقم آمن — يرجع '—' بدل 0 المضلل أو NaN عند الحقول الناقصة. */
export function formatSafeNumber(value: unknown): string {
  return typeof value === 'number' && Number.isFinite(value) ? value.toLocaleString() : '—';
}

/** تنسيق تاريخ آمن — يرجع '—' بدل Invalid Date عند الحقول الناقصة/التالفة. */
export function formatSafeDate(value: unknown, locale = 'ar-YE'): string {
  if (!value) return '—';
  const d = new Date(String(value));
  if (!Number.isFinite(d.getTime())) return '—';
  try {
    return d.toLocaleDateString(locale);
  } catch {
    return '—';
  }
}

/** نص ثانوي بتباين آمن (بدل text-muted-foreground على خلفيات muted). */
export const SAFE_SUBTLE_TEXT = 'text-foreground/70';

/** وقت آمن — يرجع '—' بدل Invalid Date عند الحقول الناقصة/التالفة. */
export function formatSafeTime(value: unknown, locale = 'ar-EG'): string {
  if (!value) return '—';
  const d = new Date(String(value));
  if (!Number.isFinite(d.getTime())) return '—';
  try {
    return d.toLocaleTimeString(locale, { hour: '2-digit', minute: '2-digit' });
  } catch {
    return '—';
  }
}

/**
 * ✅ 2026-09-24 — إصلاح التبويبات للهاتف (390px) مع RTL:
 * التفاف + تمرير أفقي عند الحاجة، والأزرار لا تُقص.
 */
export const TABS_WRAP_CLASS = 'overflow-x-auto pb-1 -mx-1 px-1';
export const TABS_LIST_CLASS = cn(
  'h-auto max-w-full flex-wrap justify-start gap-1 overflow-x-auto',
  '[direction:inherit]',
);
export const TABS_TRIGGER_CLASS = 'shrink-0 whitespace-nowrap';
