'use client';

import { useCallback, useEffect, useState } from 'react';
import { Card, CardContent, CardHeader, CardTitle, CardDescription } from '@/components/ui/card';
import { Button } from '@/components/ui/button';
import { Badge } from '@/components/ui/badge';
import { Tabs, TabsList, TabsTrigger, TabsContent } from '@/components/ui/tabs';
import { Input } from '@/components/ui/input';
import { Checkbox } from '@/components/ui/checkbox';
import { Dialog } from '@/components/ui/dialog';
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select';
import { useTranslation } from 'react-i18next';
import { Flag, CheckCircle, XCircle, Search, Eye, MoreVertical, Settings, BarChart3, Send, RefreshCw } from 'lucide-react';
import { cn } from '@/utils/cn';
import { DropdownMenu, DropdownMenuContent, DropdownMenuItem, DropdownMenuLabel, DropdownMenuSeparator, DropdownMenuTrigger } from '@/components/ui/dropdown-menu';
import { EmptyState, LoadingState, ErrorState, formatSafeDate, formatSafeTime, TABS_LIST_CLASS, TABS_TRIGGER_CLASS, TABS_WRAP_CLASS } from './_shared';
import { apiFetch, assignReport, createAnnouncement, publishAnnouncement } from '../api';

interface AdminReport {
  id: string;
  reporterRedId: string;
  reportedRedId: string | null;
  category: string;
  details: string | null;
  status: string;
  createdAt: string;
}

const STATUSES = ['OPEN', 'REVIEWING', 'RESOLVED', 'DISMISSED'] as const;

async function fetchReports(status: string): Promise<AdminReport[]> {
  const res = await apiFetch(`/api/admin/moderation/reports?status=${encodeURIComponent(status)}`);
  const data = await res.json().catch(() => []);
  if (!res.ok) {
    const err = (data && typeof data === 'object' ? (data as Record<string, unknown>).error : null) as string | null;
    throw new Error(err || `HTTP ${res.status}`);
  }
  return Array.isArray(data) ? (data as AdminReport[]) : [];
}

async function updateReport(reportId: string, status: string): Promise<void> {
  const res = await apiFetch(
    `/api/admin/moderation/reports/${encodeURIComponent(reportId)}?status=${encodeURIComponent(status)}`,
    { method: 'PATCH' }
  );
  if (!res.ok) {
    const data = await res.json().catch(() => ({}));
    const err = (data && typeof data === 'object' ? (data as Record<string, unknown>).error : null) as string | null;
    throw new Error(err || `HTTP ${res.status}`);
  }
}

/**
 * إجراء جماعي — نسخة من نمط Users.tsx.
 * لا يوجد endpoint تجميع، لذا نستدعي الموجود فرديًا بالتوالي مع حد 50.
 */
const BULK_MOD_LIMIT = 50;

async function bulkUpdateReports(ids: string[], status: string): Promise<number> {
  const batch = ids.slice(0, BULK_MOD_LIMIT);
  for (const id of batch) {
    await updateReport(id, status);
  }
  return batch.length;
}

async function bulkAssignModeration(ids: string[], adminId: string): Promise<number> {
  const batch = ids.slice(0, BULK_MOD_LIMIT);
  for (const id of batch) {
    await assignReport(id, adminId);
  }
  return batch.length;
}

export function ModerationPage() {
  const { t } = useTranslation();
  const [activeTab, setActiveTab] = useState<string>('queue');
  const [search, setSearch] = useState('');
  const [filterStatus, setFilterStatus] = useState<string>('OPEN');
  const [items, setItems] = useState<AdminReport[]>([]);
  const [loading, setLoading] = useState(false);
  const [loadError, setLoadError] = useState<string | null>(null);
  const [actingId, setActingId] = useState<string | null>(null);
  // Bulk — نسخة من نمط Users.tsx (تحديد صفوف + شريط Bulk Actions + POST جماعي)
  const [selected, setSelected] = useState<Set<string>>(new Set());
  const [bulkAction, setBulkAction] = useState<'assign' | 'resolve' | 'dismiss' | null>(null);
  const [bulkAdminId, setBulkAdminId] = useState('');
  const [bulkBusy, setBulkBusy] = useState(false);
  // إحصاءات حية للطابور (تُجلب عند فتح تبويب التحليلات)
  const [statusCounts, setStatusCounts] = useState<Record<string, number> | null>(null);
  const [countsLoading, setCountsLoading] = useState(false);
  // البث: إعلانات حقيقية (POST /api/admin/announcements + publish)
  const [bcTitle, setBcTitle] = useState('');
  const [bcBody, setBcBody] = useState('');
  const [bcType, setBcType] = useState('INFO');
  const [bcPublishNow, setBcPublishNow] = useState(true);
  const [bcBusy, setBcBusy] = useState(false);
  const [bcMsg, setBcMsg] = useState<string | null>(null);

  const load = useCallback(async (status = filterStatus) => {
    setLoading(true);
    setLoadError(null);
    try {
      setItems(await fetchReports(status));
    } catch (e) {
      setItems([]);
      setLoadError(e instanceof Error ? e.message : 'تعذّر تحميل البلاغات');
    } finally {
      setLoading(false);
    }
  }, [filterStatus]);

  useEffect(() => { load(); }, [load]);

  const loadCounts = useCallback(async () => {
    setCountsLoading(true);
    try {
      const rows = await Promise.all(STATUSES.map((s) => fetchReports(s).catch(() => [] as AdminReport[])));
      const counts: Record<string, number> = {};
      STATUSES.forEach((s, i) => { counts[s] = rows[i].length; });
      setStatusCounts(counts);
    } finally {
      setCountsLoading(false);
    }
  }, []);

  const submitBroadcast = async () => {
    if (!bcTitle.trim() || !bcBody.trim()) {
      setBcMsg('أدخل العنوان والنص');
      return;
    }
    setBcBusy(true);
    setBcMsg(null);
    try {
      const created: any = await createAnnouncement({
        title: bcTitle.trim(),
        body: bcBody.trim(),
        type: bcType,
        targetAudience: 'ALL',
        priority: bcType === 'ALERT' ? 1 : 5,
        isDismissible: true,
      });
      const id = created?.id ?? created?.announcement?.id;
      if (bcPublishNow && id) await publishAnnouncement(String(id));
      setBcMsg(bcPublishNow ? 'تم إنشاء الإعلان ونشره' : 'تم إنشاء الإعلان كمسودة');
      setBcTitle('');
      setBcBody('');
    } catch (e) {
      setBcMsg(`فشل: ${e instanceof Error ? e.message : ''}`);
    } finally {
      setBcBusy(false);
    }
  };

  const handleAction = async (action: 'review' | 'resolve' | 'dismiss', item: AdminReport) => {
    const next = action === 'review' ? 'REVIEWING' : action === 'resolve' ? 'RESOLVED' : 'DISMISSED';
    setActingId(item.id);
    try {
      await updateReport(item.id, next);
      await load();
    } catch (e) {
      setLoadError(e instanceof Error ? e.message : 'تعذّر تحديث البلاغ');
    } finally {
      setActingId(null);
    }
  };

  const toggleSelect = (id: string, checked: boolean) => {
    setSelected((prev) => {
      const next = new Set(prev);
      if (checked) next.add(id);
      else next.delete(id);
      return next;
    });
  };

  const toggleSelectAll = (checked: boolean) => {
    if (checked) setSelected(new Set(filteredData.map((i) => i.id)));
    else setSelected(new Set());
  };

  const submitBulk = async () => {
    if (!bulkAction || selected.size === 0) return;
    const ids = Array.from(selected);
    if (bulkAction === 'assign' && !bulkAdminId.trim()) {
      setLoadError('أدخل معرّف المسؤول (adminId) للإسناد الجماعي');
      return;
    }
    setBulkBusy(true);
    try {
      if (bulkAction === 'assign') await bulkAssignModeration(ids, bulkAdminId.trim());
      else if (bulkAction === 'resolve') await bulkUpdateReports(ids, 'RESOLVED');
      else await bulkUpdateReports(ids, 'DISMISSED');
      setBulkAction(null);
      setBulkAdminId('');
      setSelected(new Set());
      await load();
    } catch (e) {
      setLoadError(e instanceof Error ? e.message : 'تعذّر تنفيذ الإجراء الجماعي');
    } finally {
      setBulkBusy(false);
    }
  };

  const filteredData = items.filter((item) => {
    if (!search) return true;
    const q = search.toLowerCase();
    return (
      (item.details ?? '').toLowerCase().includes(q) ||
      item.reporterRedId.toLowerCase().includes(q) ||
      (item.reportedRedId ?? '').toLowerCase().includes(q) ||
      item.category.toLowerCase().includes(q)
    );
  });

  return (
    <>
    <div className="space-y-6">
      <div className="flex flex-col sm:flex-row sm:items-center sm:justify-between gap-4">
        <div>
          <h1 className="text-3xl font-bold tracking-tight">{t('moderation.title')}</h1>
          <p className="text-foreground/70">{t('moderation.subtitle')}</p>
        </div>
        <Badge variant="outline" className="text-sm">الطابور المحمل: {items.length} ({filterStatus})</Badge>
      </div>

      <Tabs value={activeTab} onValueChange={(v) => { setActiveTab(v); if (v === 'analytics' && statusCounts === null) void loadCounts(); }} className="space-y-4">
        <div className={TABS_WRAP_CLASS}>
        <TabsList className={TABS_LIST_CLASS}>
          <TabsTrigger value="queue" className={TABS_TRIGGER_CLASS}><Flag className="h-4 w-4 mr-2" aria-hidden="true" /> {t('moderation.queue')}</TabsTrigger>
          <TabsTrigger value="rules" className={TABS_TRIGGER_CLASS}><Settings className="h-4 w-4 mr-2" aria-hidden="true" /> {t('moderation.rules')}</TabsTrigger>
          <TabsTrigger value="analytics" className={TABS_TRIGGER_CLASS}><BarChart3 className="h-4 w-4 mr-2" aria-hidden="true" /> {t('moderation.analytics')}</TabsTrigger>
          <TabsTrigger value="broadcast" className={TABS_TRIGGER_CLASS}><Send className="h-4 w-4 mr-2" aria-hidden="true" /> {t('moderation.broadcast')}</TabsTrigger>
        </TabsList>
        </div>

        <TabsContent value="queue">
          <Card>
            <CardHeader>
              <div className="flex flex-col sm:flex-row sm:items-center sm:justify-between gap-4">
                <div>
                  <CardTitle>{t('moderation.queue')}</CardTitle>
                  <CardDescription>{t('moderation.queueDesc')}</CardDescription>
                </div>
                <div className="flex items-center gap-2">
                  <div className="relative">
                    <Search className="absolute left-3 top-1/2 -translate-y-1/2 h-4 w-4 text-foreground/70" aria-hidden="true" />
                    <Input placeholder={t('moderation.searchPlaceholder')} value={search} onChange={e => setSearch(e.target.value)} className="pl-10 w-[250px]" aria-label={t('moderation.searchPlaceholder')} />
                  </div>
                  <Select value={filterStatus} onValueChange={(v) => { setFilterStatus(v); load(v); }}>
                    <SelectTrigger className="w-[140px]" aria-label={t('moderation.statusFilter')}><SelectValue placeholder={t('moderation.statusFilter')} /></SelectTrigger>
                    <SelectContent>
                      {STATUSES.map((s) => (
                        <SelectItem key={s} value={s}>{s}</SelectItem>
                      ))}
                    </SelectContent>
                  </Select>
                </div>
              </div>
            </CardHeader>
            <CardContent>
              {loading ? (
                <LoadingState />
              ) : loadError ? (
                <ErrorState message={loadError} onRetry={() => load()} />
              ) : filteredData.length === 0 ? (
                <EmptyState message={t('common.noData')} />
              ) : (
              <>
              {/* Bulk Actions Bar — نسخة من نمط Users.tsx */}
              <div className="flex items-center justify-between gap-2 mb-3 flex-wrap">
                <label className="flex items-center gap-2 text-sm cursor-pointer">
                  <Checkbox
                    checked={filteredData.length > 0 && selected.size === filteredData.length}
                    indeterminate={selected.size > 0 && selected.size < filteredData.length}
                    onCheckedChange={(v) => toggleSelectAll(!!v)}
                    aria-label="تحديد الكل"
                  />
                  تحديد الكل ({selected.size} محدد — بحد أقصى 50 للتنفيذ)
                </label>
                {selected.size > 0 && (
                  <div className="flex items-center gap-2 flex-wrap">
                    <Button variant="outline" size="sm" onClick={() => setBulkAction('assign')}>إسناد</Button>
                    <Button variant="outline" size="sm" onClick={() => setBulkAction('resolve')}>حل جماعي</Button>
                    <Button variant="outline" size="sm" onClick={() => setBulkAction('dismiss')}>رفض جماعي</Button>
                    <Button variant="ghost" size="sm" onClick={() => setSelected(new Set())}>مسح</Button>
                  </div>
                )}
              </div>
              <div className="space-y-3">
                {filteredData.map((item) => (
                  <div key={item.id} className="border rounded-lg p-4">
                    <div className="flex items-start justify-between gap-4">
                      <Checkbox
                        checked={selected.has(item.id)}
                        onCheckedChange={(v) => toggleSelect(item.id, !!v)}
                        aria-label={`تحديد البلاغ ${item.id}`}
                        className="mt-1"
                      />
                      <div className="flex-1 min-w-0">
                        <div className="flex items-center gap-2 mb-2">
                          <Badge variant="outline">{item.category}</Badge>
                          <Badge
                            variant={item.status === 'OPEN' ? 'warning' : item.status === 'REVIEWING' ? 'info' : item.status === 'RESOLVED' ? 'success' : 'destructive'}
                          >
                            {item.status}
                          </Badge>
                        </div>
                        <p className="text-foreground/70 mb-2">{item.details || t('common.empty')}</p>
                        <div className="flex items-center gap-4 text-sm text-foreground/70">
                          <span>Reporter: {item.reporterRedId || '—'}</span>
                          <span>Reported: {item.reportedRedId ?? '—'}</span>
                          <span>{item.createdAt ? `${formatSafeDate(item.createdAt)} ${formatSafeTime(item.createdAt)}` : '—'}</span>
                        </div>
                      </div>
                      <DropdownMenu>
                        <DropdownMenuTrigger asChild>
                          <Button variant="ghost" size="icon" aria-label={`Moderation actions for report ${item.id}`} disabled={actingId === item.id}>
                            <MoreVertical className="h-4 w-4" aria-hidden="true" />
                          </Button>
                        </DropdownMenuTrigger>
                        <DropdownMenuContent align="end">
                          <DropdownMenuLabel>Actions</DropdownMenuLabel>
                          <DropdownMenuSeparator />
                          <DropdownMenuItem onClick={() => handleAction('review', item)} className="text-blue-600">
                            <Eye className="h-4 w-4 mr-2" /> Mark reviewing
                          </DropdownMenuItem>
                          <DropdownMenuItem onClick={() => handleAction('resolve', item)} className="text-green-600">
                            <CheckCircle className="h-4 w-4 mr-2" /> Resolve
                          </DropdownMenuItem>
                          <DropdownMenuItem onClick={() => handleAction('dismiss', item)} className="text-red-600">
                            <XCircle className="h-4 w-4 mr-2" /> Dismiss
                          </DropdownMenuItem>
                        </DropdownMenuContent>
                      </DropdownMenu>
                    </div>
                  </div>
                ))}
              </div>
              </>
              )}
            </CardContent>
          </Card>
        </TabsContent>

        <TabsContent value="rules">
          <Card>
            <CardHeader>
              <CardTitle>{t('moderation.rules')}</CardTitle>
              <CardDescription>لا يوجد rules API في الخادم — تُدار البلاغات من الطابور مباشرة</CardDescription>
            </CardHeader>
            <CardContent>
              <EmptyState message="القواعد الآلية غير مدعومة في الخادم بعد — استخدم إجراءات الطابور" />
            </CardContent>
          </Card>
        </TabsContent>

        <TabsContent value="analytics">
          <Card>
            <CardHeader className="flex flex-row items-center justify-between">
              <div>
                <CardTitle>{t('moderation.analytics')}</CardTitle>
                <CardDescription>عدّ حي من الخادم (كل الحالات)</CardDescription>
              </div>
              <Button variant="outline" size="sm" onClick={() => void loadCounts()} disabled={countsLoading}>
                <RefreshCw className="h-4 w-4 mr-2" aria-hidden="true" /> تحديث
              </Button>
            </CardHeader>
            <CardContent>
              {countsLoading && <p className="text-sm text-foreground/70">جار التحميل…</p>}
              {!countsLoading && statusCounts === null && <EmptyState message="اختر التبويب لتحميل العد" />}
              {statusCounts !== null && (
                <div className="grid gap-4 md:grid-cols-4">
                  {STATUSES.map((s) => (
                    <Card key={s} className="p-4">
                      <p className="text-sm text-muted-foreground">{s}</p>
                      <p className="text-3xl font-bold">{statusCounts[s] ?? 0}</p>
                    </Card>
                  ))}
                </div>
              )}
            </CardContent>
          </Card>
        </TabsContent>

        <TabsContent value="broadcast">
          <Card>
            <CardHeader>
              <CardTitle>{t('moderation.broadcast')}</CardTitle>
              <CardDescription>إعلان حقيقي للمستخدمين (POST /api/admin/announcements)</CardDescription>
            </CardHeader>
            <CardContent>
              <div className={cn('max-w-2xl space-y-3')}>
                <Input value={bcTitle} onChange={(e) => setBcTitle(e.target.value)} placeholder="عنوان الإعلان" aria-label="عنوان الإعلان" />
                <Input value={bcBody} onChange={(e) => setBcBody(e.target.value)} placeholder="نص الإعلان" aria-label="نص الإعلان" />
                <div className="flex items-center gap-2 flex-wrap">
                  <Select value={bcType} onValueChange={setBcType}>
                    <SelectTrigger className="w-[160px]" aria-label="نوع الإعلان"><SelectValue /></SelectTrigger>
                    <SelectContent>
                      <SelectItem value="INFO">INFO</SelectItem>
                      <SelectItem value="WARNING">WARNING</SelectItem>
                      <SelectItem value="ALERT">ALERT</SelectItem>
                    </SelectContent>
                  </Select>
                  <label className="flex items-center gap-2 text-sm cursor-pointer">
                    <Checkbox checked={bcPublishNow} onCheckedChange={(v) => setBcPublishNow(!!v)} aria-label="نشر فورا" />
                    نشر فورا
                  </label>
                  <Button onClick={() => void submitBroadcast()} disabled={bcBusy || !bcTitle.trim() || !bcBody.trim()}>
                    <Send className="h-4 w-4 mr-2" aria-hidden="true" /> {bcBusy ? 'جار الإرسال…' : t('moderation.sendBroadcast')}
                  </Button>
                </div>
                {bcMsg && <p role="status" className="text-sm font-medium text-foreground">{bcMsg}</p>}
              </div>
            </CardContent>
          </Card>
        </TabsContent>
      </Tabs>

      {/* Bulk Dialog — الموحد (assign/resolve/dismiss) */}
      <Dialog
        open={bulkAction !== null}
        onOpenChange={(open) => { if (!open) setBulkAction(null); }}
        title={
          bulkAction === 'assign' ? `إسناد ${selected.size} بلاغ`
          : bulkAction === 'resolve' ? `حل جماعي (${selected.size})`
          : `رفض جماعي (${selected.size})`
        }
      >
        <div className="space-y-4 py-2" dir="rtl">
          {bulkAction === 'assign' ? (
            <div className="space-y-2">
              <label className="text-sm font-medium">معرّف المسؤول (adminId) *</label>
              <Input
                value={bulkAdminId}
                onChange={(e) => setBulkAdminId(e.target.value)}
                placeholder="admin-id"
              />
            </div>
          ) : (
            <p className="text-sm text-muted-foreground">
              سيتم تنفيذ الإجراء على {selected.size} بلاغ (بحد أقصى 50) — نفس سلوك الإجراء الفردي.
            </p>
          )}
          <div className="flex items-center justify-end gap-2 pt-2">
            <Button variant="outline" onClick={() => setBulkAction(null)}>إلغاء</Button>
            <Button disabled={bulkBusy} onClick={submitBulk}>
              {bulkBusy ? 'جاري التنفيذ...' : 'تأكيد التنفيذ'}
            </Button>
          </div>
        </div>
      </Dialog>
    </div>
    </>
  );
}
