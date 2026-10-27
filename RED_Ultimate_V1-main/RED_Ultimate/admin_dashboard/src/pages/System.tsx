'use client';

import { useEffect, useState } from 'react';
import { Link } from '@tanstack/react-router';
import { Card, CardContent, CardHeader, CardTitle, CardDescription } from '@/components/ui/card';
import { Button } from '@/components/ui/button';
import { Badge } from '@/components/ui/badge';
import { Tabs, TabsList, TabsTrigger, TabsContent } from '@/components/ui/tabs';
import { Input } from '@/components/ui/input';
import { Switch } from '@/components/ui/switch';
import { useTranslation } from 'react-i18next';
import { Settings, Database, HardDrive, GitBranch, Zap, RefreshCw, Shield, ArrowLeft } from 'lucide-react';
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@/components/ui/table';
import { ErrorBanner, EmptyState, TABS_LIST_CLASS, TABS_TRIGGER_CLASS, TABS_WRAP_CLASS } from './_shared';
import { getFeatureFlags, updateFeatureFlag, getBackups } from '@/api';
import { useMigrations, useCacheOverview, mutations } from '@/api/queries';

function formatBytes(bytes: number): string {
  if (typeof bytes !== 'number' || !Number.isFinite(bytes) || bytes < 0) return '—';
  if (bytes >= 1e9) return `${(bytes / 1e9).toFixed(2)} GB`;
  if (bytes >= 1e6) return `${(bytes / 1e6).toFixed(2)} MB`;
  if (bytes >= 1e3) return `${(bytes / 1e3).toFixed(2)} KB`;
  return `${bytes} B`;
}

export function SystemPage() {
  const { t } = useTranslation();
  const [activeTab, setActiveTab] = useState<string>('flags');

  // ── أعلام الميزات: حية (GET/PUT /api/admin/feature-flags) ──
  const [flags, setFlags] = useState<any[]>([]);
  const [flagsLoading, setFlagsLoading] = useState(true);
  const [flagsError, setFlagsError] = useState<string | null>(null);

  // ── النسخ: حية للقراءة (GET /api/admin/backups) — الإجراءات في /backups ──
  const [backups, setBackups] = useState<any[]>([]);
  const [backupsLoading, setBackupsLoading] = useState(true);

  // ── الهجرات والكاش: حية عبر queries.ts ──
  const migrationsQuery = useMigrations();
  const cacheQuery = useCacheOverview();
  const invalidateCache = mutations.useInvalidateCache();
  const [cachePattern, setCachePattern] = useState('red:*');
  const [cacheMsg, setCacheMsg] = useState<string | null>(null);

  useEffect(() => {
    let alive = true;
    (async () => {
      try {
        const items = await getFeatureFlags();
        if (alive) setFlags(Array.isArray(items) ? items : []);
      } catch (e: any) {
        if (alive) setFlagsError(e?.message ?? 'تعذر التحميل');
      } finally {
        if (alive) setFlagsLoading(false);
      }
    })();
    (async () => {
      try {
        const res: any = await getBackups();
        const items = Array.isArray(res) ? res : res?.content ?? [];
        if (alive) setBackups(items);
      } catch {
        if (alive) setBackups([]);
      } finally {
        if (alive) setBackupsLoading(false);
      }
    })();
    return () => { alive = false; };
  }, []);

  const toggleFlag = async (flag: any) => {
    const name = flag.flagName ?? flag.key ?? flag.name;
    try {
      await updateFeatureFlag(name, { enabled: !flag.enabled });
      setFlags((prev) => prev.map((f) => ((f.flagName ?? f.key ?? f.name) === name ? { ...f, enabled: !f.enabled } : f)));
    } catch (e: any) {
      setFlagsError(e?.message ?? 'فشل التحديث');
    }
  };

  const flushCache = async () => {
    setCacheMsg(null);
    try {
      const res: any = await invalidateCache.mutateAsync({ pattern: cachePattern || 'red:*', limit: 1000 });
      const n = res?.removed ?? res?.revoked ?? res?.count ?? res?.done ?? '?';
      setCacheMsg(`تم إبطال ${n} مفتاحا`);
      void cacheQuery.refetch();
    } catch (e: any) {
      setCacheMsg(`فشل: ${e?.message ?? ''}`);
    }
  };

  const migrations = migrationsQuery.data;
  const cache = cacheQuery.data;

  return (
    <>
    <div className="space-y-6">
      {/* ✅ الجلسة 12: لافتة شرطية عند فشل المصدرين معًا — الكاش والهجرات حية (الجلسة 6) */}
      {migrationsQuery.isError && cacheQuery.isError && (
        <ErrorBanner message="تعذّر الوصول للخادم (الكاش والهجرات) — تحقق من الاتصال" />
      )}
      <div className="flex flex-col sm:flex-row sm:items-center sm:justify-between gap-4">
        <div>
          <h1 className="text-3xl font-bold tracking-tight">{t('system.title')}</h1>
          <p className="text-foreground/70">{t('system.subtitle')}</p>
        </div>
        <Button asChild><Link to="/featureflags">إدارة الأعلام الكاملة</Link></Button>
      </div>

      {/* روابط متقاطعة للصفحات التفصيلية */}
      <Card>
        <CardHeader>
          <CardTitle>الصفحات التفصيلية</CardTitle>
          <CardDescription>روابط متقاطعة — نفس البيانات، لا ازدواج منطقي</CardDescription>
        </CardHeader>
        <CardContent>
          <div className="flex flex-wrap gap-2">
            <Button asChild variant="outline" size="sm"><Link to="/system-cache">Cache</Link></Button>
            <Button asChild variant="outline" size="sm"><Link to="/system-config">Config</Link></Button>
            <Button asChild variant="outline" size="sm"><Link to="/system-migrations">Migrations</Link></Button>
            <Button asChild variant="outline" size="sm"><Link to="/system-audit-log">Audit Log</Link></Button>
            <Button asChild variant="outline" size="sm"><Link to="/system-backups-manager">Backups Manager</Link></Button>
          </div>
        </CardContent>
      </Card>

      <Tabs value={activeTab} onValueChange={setActiveTab} className="space-y-4">
        <div className={TABS_WRAP_CLASS}>
        <TabsList className={TABS_LIST_CLASS}>
          <TabsTrigger value="flags" className={TABS_TRIGGER_CLASS}><Zap className="h-4 w-4 mr-2" aria-hidden="true" /> {t('system.featureFlags')}</TabsTrigger>
          <TabsTrigger value="config" className={TABS_TRIGGER_CLASS}><Settings className="h-4 w-4 mr-2" aria-hidden="true" /> {t('system.configuration')}</TabsTrigger>
          <TabsTrigger value="audit" className={TABS_TRIGGER_CLASS}><Shield className="h-4 w-4 mr-2" aria-hidden="true" /> {t('system.auditLogs')}</TabsTrigger>
          <TabsTrigger value="backups" className={TABS_TRIGGER_CLASS}><HardDrive className="h-4 w-4 mr-2" aria-hidden="true" /> {t('system.backups')}</TabsTrigger>
          <TabsTrigger value="migrations" className={TABS_TRIGGER_CLASS}><GitBranch className="h-4 w-4 mr-2" aria-hidden="true" /> {t('system.migrations')}</TabsTrigger>
          <TabsTrigger value="cache" className={TABS_TRIGGER_CLASS}><Database className="h-4 w-4 mr-2" aria-hidden="true" /> {t('system.cache')}</TabsTrigger>
        </TabsList>
        </div>

        <TabsContent value="flags">
          <Card>
            <CardHeader className="flex flex-row items-center justify-between">
              <div>
                <CardTitle>{t('system.featureFlags')}</CardTitle>
                <CardDescription>مباشرة من الخادم — التبديل يُحفظ فورا</CardDescription>
              </div>
              <Button asChild variant="outline"><Link to="/featureflags">تحرير متقدم</Link></Button>
            </CardHeader>
            <CardContent>
              {flagsLoading && <p className="text-sm text-foreground/70">جار التحميل…</p>}
              {flagsError && <p role="alert" className="text-sm text-red-500">{flagsError}</p>}
              {!flagsLoading && !flagsError && flags.length === 0 && <EmptyState message="لا أعلام معرفة" />}
              <div className="space-y-4">
                {flags.map((flag) => {
                  const name = flag.flagName ?? flag.key ?? flag.name ?? '';
                  return (
                    <div key={name} className="flex items-center justify-between p-4 border rounded-lg">
                      <div className="flex-1">
                        <div className="flex items-center gap-2">
                          <p className="font-medium text-foreground">{name}</p>
                          {typeof flag.rolloutPercentage === 'number' && (
                            <code className="text-xs bg-muted px-2 py-1 rounded">{flag.rolloutPercentage}%</code>
                          )}
                        </div>
                        {flag.description && <p className="text-sm text-foreground/70">{flag.description}</p>}
                      </div>
                      <Switch checked={!!flag.enabled} onCheckedChange={() => void toggleFlag(flag)} aria-label={`Toggle flag ${name}`} />
                    </div>
                  );
                })}
              </div>
            </CardContent>
          </Card>
        </TabsContent>

        <TabsContent value="config">
          <Card>
            <CardHeader>
              <CardTitle>{t('system.configuration')}</CardTitle>
              <CardDescription>لا يوجد config API في الخادم بعد — تُدار الإعدادات من هنا</CardDescription>
            </CardHeader>
            <CardContent>
              <EmptyState message="لا يوجد مسار إعدادات خلفي — راجع /system-config عند توفره" />
              <Button asChild variant="outline" className="mt-4"><Link to="/system-config"><ArrowLeft className="h-4 w-4 mr-2" aria-hidden="true" /> صفحة الإعدادات</Link></Button>
            </CardContent>
          </Card>
        </TabsContent>

        <TabsContent value="audit">
          <Card>
            <CardHeader>
              <CardTitle>{t('system.auditLogs')}</CardTitle>
              <CardDescription>سجل التدقيق الحي</CardDescription>
            </CardHeader>
            <CardContent>
              <Button asChild><Link to="/audit">فتح سجل التدقيق الحي</Link></Button>
            </CardContent>
          </Card>
        </TabsContent>

        <TabsContent value="backups">
          <Card>
            <CardHeader className="flex flex-row items-center justify-between">
              <div>
                <CardTitle>{t('system.backups')}</CardTitle>
                <CardDescription>من الخادم مباشرة — الإنشاء والاستعادة في الصفحة الكاملة</CardDescription>
              </div>
              <Button asChild variant="outline"><Link to="/backups">إدارة كاملة</Link></Button>
            </CardHeader>
            <CardContent>
              {backupsLoading && <p className="text-sm text-foreground/70">جار التحميل…</p>}
              {!backupsLoading && backups.length === 0 && <EmptyState message="لا نسخ بعد" />}
              <div className="space-y-4">
                {backups.slice(0, 5).map((b: any) => (
                  <div key={String(b.id)} className="flex items-center justify-between p-4 border rounded-lg">
                    <div>
                      <p className="font-medium">{b.backupType ?? b.type ?? 'FULL'} — {b.startedAt ?? ''}</p>
                      <p className="text-sm text-muted-foreground">الحجم: {formatBytes(Number(b.sizeBytes ?? b.size ?? -1))} • الحالة: {b.status ?? '—'}</p>
                    </div>
                    <Badge variant={b.status === 'COMPLETED' || b.status === 'VERIFIED' ? 'success' : 'outline'}>{b.status ?? '—'}</Badge>
                  </div>
                ))}
              </div>
            </CardContent>
          </Card>
        </TabsContent>

        <TabsContent value="migrations">
          <Card>
            <CardHeader className="flex flex-row items-center justify-between">
              <div>
                <CardTitle>{t('system.migrations')}</CardTitle>
                <CardDescription>سجل Flyway الحقيقي {migrations?.currentVersion ? `(الحالية: ${migrations.currentVersion})` : ''}</CardDescription>
              </div>
              <Button asChild variant="outline"><Link to="/system-migrations">التفاصيل</Link></Button>
            </CardHeader>
            <CardContent>
              {migrationsQuery.isLoading && <p className="text-sm text-foreground/70">جار التحميل…</p>}
              {migrationsQuery.isError && <EmptyState message="تعذر تحميل الهجرات" />}
              {migrations && (
                <Table>
                  <TableHeader>
                    <TableRow>
                      <TableHead>Version</TableHead>
                      <TableHead>Description</TableHead>
                      <TableHead>Status</TableHead>
                      <TableHead>Executed At</TableHead>
                    </TableRow>
                  </TableHeader>
                  <TableBody>
                    {(migrations.migrations ?? []).slice(0, 8).map((m) => (
                      <TableRow key={m.id ?? m.version}>
                        <TableCell><code>{m.version}</code></TableCell>
                        <TableCell>{m.description}</TableCell>
                        <TableCell><Badge variant={m.status === 'SUCCESS' ? 'success' : 'destructive'}>{m.status}</Badge></TableCell>
                        <TableCell>{m.executedAt ?? '—'}</TableCell>
                      </TableRow>
                    ))}
                  </TableBody>
                </Table>
              )}
            </CardContent>
          </Card>
        </TabsContent>

        <TabsContent value="cache">
          <Card>
            <CardHeader className="flex flex-row items-center justify-between">
              <div>
                <CardTitle>{t('system.cache')}</CardTitle>
                <CardDescription>من Redis مباشرة {cache?.memory ? `— الذاكرة: ${formatBytes(cache.memory.used)}` : ''}</CardDescription>
              </div>
              <Button asChild variant="outline"><Link to="/system-cache">التفاصيل</Link></Button>
            </CardHeader>
            <CardContent>
              {cacheQuery.isLoading && <p className="text-sm text-foreground/70">جار التحميل…</p>}
              {cache?.memory && (
                <div className="grid gap-4 md:grid-cols-3 mb-4">
                  <Card className="p-4"><p className="text-sm text-muted-foreground">Keys</p><p className="text-3xl font-bold">{cache.memory.keys}</p></Card>
                  <Card className="p-4"><p className="text-sm text-muted-foreground">Memory Used</p><p className="text-3xl font-bold">{formatBytes(cache.memory.used)}</p></Card>
                  <Card className="p-4"><p className="text-sm text-muted-foreground">Evicted</p><p className="text-3xl font-bold">{cache.memory.evicted}</p></Card>
                </div>
              )}
              <div className="flex flex-col sm:flex-row gap-2">
                <Input value={cachePattern} onChange={(e) => setCachePattern(e.target.value)} placeholder="red:*" aria-label="Cache pattern" className="max-w-xs" />
                <Button variant="outline" onClick={() => void flushCache()} disabled={invalidateCache.isPending} aria-label="Invalidate cache by pattern">
                  <RefreshCw className="h-4 w-4 mr-2" aria-hidden="true" /> {invalidateCache.isPending ? 'جار الإبطال…' : 'إبطال بالنمط'}
                </Button>
              </div>
              {cacheMsg && <p role="status" className="mt-2 text-sm font-medium text-foreground">{cacheMsg}</p>}
            </CardContent>
          </Card>
        </TabsContent>
      </Tabs>
    </div>
    </>
  );
}
