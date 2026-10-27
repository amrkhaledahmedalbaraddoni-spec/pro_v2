'use client';

import { useEffect, useState } from 'react';
import { Link } from '@tanstack/react-router';
import { Card, CardContent, CardHeader, CardTitle, CardDescription } from '@/components/ui/card';
import { Button } from '@/components/ui/button';
import { Tabs, TabsList, TabsTrigger, TabsContent } from '@/components/ui/tabs';
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select';
import { useTranslation } from 'react-i18next';
import {
  BarChart3,
  LineChart,
  Users,
  TrendingUp,
  Download,
  Calendar,
} from 'lucide-react';
import { ErrorBanner, EmptyState, TABS_LIST_CLASS, TABS_TRIGGER_CLASS, TABS_WRAP_CLASS } from './_shared';
import { getDashboardSummary, getSystemAnalytics, type DashboardSummary } from '@/api';

function rangeToDates(range: string): { start: string; end: string } {
  const end = new Date();
  const start = new Date(end);
  if (range === '1h') start.setHours(start.getHours() - 1);
  else if (range === '24h') start.setDate(start.getDate() - 1);
  else if (range === '30d' || range === '90d') start.setDate(start.getDate() - (range === '30d' ? 30 : 90));
  else start.setDate(start.getDate() - 7);
  return { start: start.toISOString(), end: end.toISOString() };
}

export function AnalyticsPage() {
  const { t } = useTranslation();
  const [timeRange, setTimeRange] = useState<string>('7d');
  const [summary, setSummary] = useState<DashboardSummary | null>(null);
  const [events, setEvents] = useState<any[]>([]);
  const [loading, setLoading] = useState(true);
  const [loadError, setLoadError] = useState<string | null>(null);

  useEffect(() => {
    let alive = true;
    (async () => {
      setLoading(true);
      setLoadError(null);
      try {
        const { start, end } = rangeToDates(timeRange);
        const [s, ev] = await Promise.all([getDashboardSummary(), getSystemAnalytics(start, end)]);
        if (!alive) return;
        setSummary(s as DashboardSummary | null);
        setEvents(Array.isArray(ev) ? ev : []);
      } catch (e: any) {
        if (alive) {
          setLoadError(e?.message ?? 'تعذر التحميل');
          setSummary(null);
          setEvents([]);
        }
      } finally {
        if (alive) setLoading(false);
      }
    })();
    return () => { alive = false; };
  }, [timeRange]);

  const a = summary?.analytics;

  return (
    <>
    <div className="space-y-6">
      {/* ✅ الجلسة 12: لافتة شرطية عند الفشل فقط — البيانات حية من /api/admin/analytics */}
      {loadError && <ErrorBanner message="تعذّر تحميل التحليلات من الخادم — المؤشرات أدناه تظهر — حتى ينجح الجلب" />}
      <div className="flex flex-col sm:flex-row sm:items-center sm:justify-between gap-4">
        <div>
          <h1 className="text-3xl font-bold tracking-tight">{t('analytics.title')}</h1>
          <p className="text-foreground/70">{t('analytics.subtitle')}</p>
        </div>
        <div className="flex items-center gap-2">
          <Select value={timeRange} onValueChange={setTimeRange}>
            <SelectTrigger className="w-[180px]">
              <SelectValue placeholder={t('analytics.selectRange')} />
            </SelectTrigger>
            <SelectContent>
              <SelectItem value="1h">{t('common.lastHour')}</SelectItem>
              <SelectItem value="24h">{t('common.last24Hours')}</SelectItem>
              <SelectItem value="7d">{t('common.last7Days')}</SelectItem>
              <SelectItem value="30d">{t('common.last30Days')}</SelectItem>
              <SelectItem value="90d">{t('common.last30Days')}</SelectItem>
            </SelectContent>
          </Select>
          <Button variant="outline" disabled title="لا يوجد مسار تصدير في الخادم" aria-label="تصدير (غير مدعوم في الخادم)"><Download className="h-4 w-4 mr-2" aria-hidden="true" /> {t('common.export')}</Button>
          <Button asChild variant="outline"><Link to="/dashboard">الرسوم الحية</Link></Button>
        </div>
      </div>

      {/* مؤشرات حية من الخادم */}
      <div className="grid gap-4 md:grid-cols-4">
        <Card className="p-4"><p className="text-sm text-muted-foreground">إجمالي المستخدمين</p><p className="text-3xl font-bold">{loading ? '…' : a?.totalUsers ?? '—'}</p></Card>
        <Card className="p-4"><p className="text-sm text-muted-foreground">جدد (24h)</p><p className="text-3xl font-bold">{loading ? '…' : a?.newUsers24h ?? '—'}</p></Card>
        <Card className="p-4"><p className="text-sm text-muted-foreground">بلاغات معلقة</p><p className="text-3xl font-bold">{loading ? '…' : summary?.pendingReports ?? '—'}</p></Card>
        <Card className="p-4"><p className="text-sm text-muted-foreground">أحداث ({timeRange})</p><p className="text-3xl font-bold">{loading ? '…' : events.length}</p></Card>
      </div>
      {loadError && <p role="alert" className="text-sm text-red-500">{loadError}</p>}

      {/* أحداث التحليلات الحية */}
      <Card>
        <CardHeader>
          <CardTitle className="flex items-center gap-2">
            <Calendar className="h-5 w-5" aria-hidden="true" />
            أحداث التحليلات ({timeRange})
          </CardTitle>
          <CardDescription>GET /api/admin/analytics — من الخادم مباشرة</CardDescription>
        </CardHeader>
        <CardContent>
          {loading && <p className="text-sm text-foreground/70">جار التحميل…</p>}
          {!loading && events.length === 0 && <EmptyState message={t('common.noData')} />}
          <div className="space-y-2">
            {events.slice(0, 20).map((ev: any, i: number) => (
              <div key={i} className="p-3 border rounded-lg text-sm">
                <p className="font-medium text-foreground">{ev?.event ?? ev?.type ?? ev?.name ?? `حدث ${i + 1}`}</p>
                <p className="text-foreground/70">{ev?.timestamp ?? ev?.createdAt ?? ''}</p>
              </div>
            ))}
          </div>
        </CardContent>
      </Card>

      {/* Cohort/Funnel/Retention: الرسوم الحية في /dashboard */}
      <Tabs defaultValue="cohort" className="space-y-4">
        <div className={TABS_WRAP_CLASS}>
        <TabsList className={TABS_LIST_CLASS}>
          <TabsTrigger value="cohort" className={TABS_TRIGGER_CLASS}><Users className="h-4 w-4 mr-2" aria-hidden="true" /> {t('analytics.cohortAnalysis')}</TabsTrigger>
          <TabsTrigger value="funnel" className={TABS_TRIGGER_CLASS}><LineChart className="h-4 w-4 mr-2" aria-hidden="true" /> {t('analytics.funnelAnalysis')}</TabsTrigger>
          <TabsTrigger value="retention" className={TABS_TRIGGER_CLASS}><TrendingUp className="h-4 w-4 mr-2" aria-hidden="true" /> {t('analytics.retentionCurves')}</TabsTrigger>
        </TabsList>
        </div>

        <TabsContent value="cohort">
          <Card>
            <CardContent className="p-6">
              <EmptyState message="تحليلات الأفواج في لوحة الرسوم الحية" />
              <Button asChild variant="outline" className="mt-4"><Link to="/dashboard"><BarChart3 className="h-4 w-4 mr-2" aria-hidden="true" /> فتح /dashboard</Link></Button>
            </CardContent>
          </Card>
        </TabsContent>

        <TabsContent value="funnel">
          <Card>
            <CardContent className="p-6">
              <EmptyState message="تحليلات القمع في لوحة الرسوم الحية" />
              <Button asChild variant="outline" className="mt-4"><Link to="/dashboard">فتح /dashboard</Link></Button>
            </CardContent>
          </Card>
        </TabsContent>

        <TabsContent value="retention">
          <Card>
            <CardContent className="p-6">
              <EmptyState message="منحنيات الاحتفاظ في لوحة الرسوم الحية" />
              <Button asChild variant="outline" className="mt-4"><Link to="/dashboard">فتح /dashboard</Link></Button>
            </CardContent>
          </Card>
        </TabsContent>
      </Tabs>
    </div>
    </>
  );
}
