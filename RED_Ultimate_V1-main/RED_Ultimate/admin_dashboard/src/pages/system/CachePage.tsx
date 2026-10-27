import { useMemo, useState } from 'react';
import { Link } from '@tanstack/react-router';
import { Server, Trash2, RefreshCw, Database, Zap, AlertTriangle, ChevronLeft, ChevronRight } from 'lucide-react';
import { useTranslation } from 'react-i18next';
import { cn, formatBytes } from '@/utils';
import { Button } from '@/components/ui/button';
import { SearchInput } from '@/components/ui/search-input';
import { DataTable } from '@/components/ui/data-table';
import { Badge } from '@/components/ui/badge';
import { Dialog } from '@/components/ui/dialog';
import { Tabs } from '@/components/ui/tabs';
import { useCacheOverview, mutations, type CacheKeyRow } from '@/api/queries';
import { ErrorState } from '@/pages/_shared';
import { toast } from '@/hooks/useToast';

const TABS = [
  { value: 'keys', label: 'Keys Browser' },
  { value: 'memory', label: 'Memory Analysis' },
  { value: 'ttl', label: 'TTL Management' },
];

/**
 * المصدر الوحيد هو `GET /api/admin/system/cache` (Redis INFO + SCAN مقيّد في
 * `AdminSystemOpsController`). لا بيانات وهمية: تعذّر Redis يُظهر خطأً صريحًا
 * مع إعادة المحاولة، وكاش فارغ يعني صفر مفاتيح فعلًا.
 */
/** يحوّل نص البحث إلى نمط Redis (glob): بحث نصي = `*نص*`، ونمط صريح يُمرَّر كما هو. */
function toPattern(search: string): string {
  const s = search.trim();
  if (!s) return '*';
  return /[*?[\]]/.test(s) ? s : `*${s}*`;
}

export function CachePage() {
  const { t } = useTranslation();
  const [activeTab, setActiveTab] = useState('keys');
  const [search, setSearch] = useState('');
  const [page, setPage] = useState(0);
  const [size] = useState(25);
  const [flushDialogOpen, setFlushDialogOpen] = useState(false);
  const [flushPattern, setFlushPattern] = useState('');
  const [flushLimit, setFlushLimit] = useState(500);
  const [confirmChecked, setConfirmChecked] = useState(false);

  const pattern = useMemo(() => toPattern(search), [search]);
  const { data, error, isLoading, refetch } = useCacheOverview({ pattern, count: 200 });
  const invalidate = mutations.useInvalidateCache();

  // الخادم قد يرسل حقول null عند فشل INFO جزئي — تعميم صريح بدل الانهيار.
  const memory = {
    used: Number(data?.memory?.used ?? 0),
    peak: Number(data?.memory?.peak ?? 0),
    limit: Number(data?.memory?.limit ?? 0),
    fragmentation: Number(data?.memory?.fragmentation ?? 0),
    keys: Number(data?.memory?.keys ?? 0),
    expired: Number(data?.memory?.expired ?? 0),
    evicted: Number(data?.memory?.evicted ?? 0),
  };
  const allKeys = useMemo<CacheKeyRow[]>(() => data?.keys ?? [], [data?.keys]);
  const truncated = data?.truncated ?? false;

  const keys = allKeys.slice(page * size, (page + 1) * size);
  const totalPages = Math.max(1, Math.ceil(allKeys.length / size));

  const ttlStats = useMemo(() => {
    const noExpiry = allKeys.filter((k) => k.ttl === -1).length;
    const underHour = allKeys.filter((k) => k.ttl > 0 && k.ttl < 3600).length;
    const underDay = allKeys.filter((k) => k.ttl >= 3600 && k.ttl < 86400).length;
    const soonest = [...allKeys]
      .filter((k) => k.ttl > 0)
      .sort((a, b) => a.ttl - b.ttl)
      .slice(0, 10);
    return { noExpiry, underHour, underDay, soonest };
  }, [allKeys]);

  const usedPercent = memory.limit > 0 ? Math.min(100, (memory.used / memory.limit) * 100) : 0;
  const flushValid = flushPattern.trim().length > 0 && !/^\*+$/.test(flushPattern.trim()) && confirmChecked;

  const handleInvalidate = async () => {
    try {
      const res: any = await invalidate.mutateAsync({ pattern: flushPattern.trim(), limit: flushLimit });
      toast.success('تم إبطال الكاش', `حُذف ${res?.deleted ?? 0} مفتاحًا بنمط ${flushPattern.trim()}`);
      setFlushDialogOpen(false);
      setFlushPattern('');
      setConfirmChecked(false);
      void refetch();
    } catch (e: any) {
      toast.error('فشل الإبطال', e?.message ?? 'تعذّر تنفيذ الإبطال');
    }
  };

  const keyColumns = [
    {
      key: 'key',
      title: 'Key',
      cell: (k: CacheKeyRow) => <span className="font-mono text-yn-text-secondary text-sm max-w-xs truncate block">{k.key}</span>,
    },
    {
      key: 'type',
      title: 'Type',
      cell: (k: CacheKeyRow) => <Badge variant="blue">{k.type}</Badge>,
    },
    {
      key: 'size',
      title: 'Size',
      cell: (k: CacheKeyRow) => <span className="text-yn-text-secondary">{k.size == null ? '—' : `${k.size} bytes`}</span>,
    },
    {
      key: 'ttl',
      title: 'TTL',
      cell: (k: CacheKeyRow) => (
        <span className="text-yn-text-secondary">
          {k.ttl === -1 ? 'No expiry' : `${Math.floor(k.ttl / 3600)}h ${Math.floor((k.ttl % 3600) / 60)}m`}
        </span>
      ),
    },
    {
      key: 'memory',
      title: 'Memory',
      cell: (k: CacheKeyRow) => (
        // MEMORY USAGE غير مكشوف في واجهة Spring Data الحالية — «—» أصدق من رقم مُختلق
        <span className="text-yn-text-secondary">{k.memory == null ? '—' : formatBytes(k.memory)}</span>
      ),
    },
    {
      key: 'actions',
      title: 'Actions',
      cell: () => (
        <Button
          variant="ghost"
          size="sm"
          disabled
          title="حذف مفتاح واحد معطّل عمدًا: Redis هنا يُدار بنمط مقيّد فقط عبر «Invalidate»"
          className="text-yn-error/50"
        >
          <Trash2 className="w-4 h-4" />
        </Button>
      ),
    },
  ];

  const header = (
    <>
      {/* زر عودة للصفحة العامة — دمج الازدواج الملاحي بدون حذف */}
      <div className="flex flex-wrap gap-2">
        <Button asChild variant="secondary" size="sm"><Link to="/system">عودة إلى System</Link></Button>
      </div>
      <div className="flex flex-col sm:flex-row sm:items-center sm:justify-between gap-4">
        <div>
          <h1 className="font-heading text-2xl font-bold text-yn-text">{t('system.cache.title')}</h1>
          <p className="text-yn-text-secondary">{t('system.cache.subtitle')}</p>
        </div>
        <div className="flex items-center gap-3">
          <Button variant="secondary" size="sm" onClick={() => setFlushDialogOpen(true)} disabled={!!error}>
            <Trash2 className="w-4 h-4 mr-2" />
            Invalidate by Pattern
          </Button>
          <Button variant="secondary" size="sm" onClick={() => void refetch()}>
            <RefreshCw className="w-4 h-4 mr-2" />
            Refresh
          </Button>
        </div>
      </div>
    </>
  );

  if (isLoading && !data) {
    return <div className="yn-page space-y-6">{header}<div className="yn-loading"><div className="yn-spinner" /></div></div>;
  }

  if (error) {
    return (
      <div className="yn-page space-y-6">
        {header}
        <div className="yn-card yn-card-liquid yn-glass">
          <ErrorState
            message={`تعذّر قراءة كاش Redis من الخادم: ${error instanceof Error ? error.message : 'خطأ غير معروف'}`}
            onRetry={() => void refetch()}
          />
        </div>
      </div>
    );
  }

  return (
    <div className="yn-page space-y-6">
      {header}

      {/* Memory Overview — أرقام Redis الحقيقية */}
      <div className="grid grid-cols-1 md:grid-cols-4 gap-4">
        <StatCard label="Used Memory" value={formatBytes(memory.used)} icon={Server} color="blue" />
        <StatCard label="Peak Memory" value={formatBytes(memory.peak)} icon={Zap} color="gold" />
        <StatCard label="Memory Limit" value={memory.limit > 0 ? formatBytes(memory.limit) : 'بلا حد'} icon={Server} color="blue" />
        <StatCard label="Fragmentation" value={`${memory.fragmentation.toFixed(2)}x`} icon={AlertTriangle} color="gold" />
      </div>

      <div className="grid grid-cols-1 md:grid-cols-3 gap-4">
        <StatCard label="Total Keys" value={memory.keys.toLocaleString()} icon={Database} color="green" />
        <StatCard label="Expired Keys" value={memory.expired.toLocaleString()} icon={AlertTriangle} color="gold" />
        <StatCard label="Evicted Keys" value={memory.evicted.toLocaleString()} icon={Trash2} color="danger" />
      </div>

      <Tabs value={activeTab} onChange={setActiveTab} tabs={TABS} />

      {activeTab === 'keys' && (
        <div className="yn-card yn-card-liquid yn-glass">
          <div className="p-4 border-b border-yn-border space-y-2">
            <SearchInput
              value={search}
              onChange={(value) => { setSearch(value); setPage(0); }}
              placeholder="نمط Redis… مثال: session:*"
              className="max-w-sm"
            />
            <p className="text-xs text-yn-text-muted">
              النمط المُرسل: <span className="font-mono text-yn-text-secondary">{pattern}</span>
              {truncated && ' — النتيجة مقطوعة عند سقف الدفعة (200)، ضيّق النمط لرؤية الباقي'}
            </p>
          </div>
          <DataTable columns={keyColumns} data={keys} rowKey="key" emptyMessage="No keys found" />

          <div className="p-4 border-t border-yn-border flex items-center justify-between">
            <span className="text-sm text-yn-text-secondary">
              Showing {allKeys.length === 0 ? 0 : page * size + 1}–{Math.min((page + 1) * size, allKeys.length)} of {allKeys.length}
            </span>
            <div className="flex gap-2">
              <Button variant="ghost" size="sm" onClick={() => setPage((p) => Math.max(0, p - 1))} disabled={page === 0}>
                <ChevronLeft className="w-4 h-4" />
              </Button>
              <Button variant="ghost" size="sm" onClick={() => setPage((p) => Math.min(totalPages - 1, p + 1))} disabled={page >= totalPages - 1}>
                <ChevronRight className="w-4 h-4" />
              </Button>
            </div>
          </div>
        </div>
      )}

      {activeTab === 'memory' && (
        <div className="yn-card yn-card-liquid yn-glass p-6">
          <div className="grid grid-cols-1 sm:grid-cols-3 gap-4 text-center">
            <div className="p-4 bg-yn-navy rounded-lg">
              <p className="text-2xl font-bold text-yn-green">
                {memory.limit > 0 ? `${usedPercent.toFixed(1)}%` : formatBytes(memory.used)}
              </p>
              <p className="text-sm text-yn-text-muted">{memory.limit > 0 ? 'Memory Usage (used/limit)' : 'Used (بلا سقف)'}</p>
            </div>
            <div className="p-4 bg-yn-navy rounded-lg">
              <p className="text-2xl font-bold text-yn-gold">{memory.fragmentation.toFixed(2)}x</p>
              <p className="text-sm text-yn-text-muted">Fragmentation Ratio</p>
            </div>
            <div className="p-4 bg-yn-navy rounded-lg">
              <p className="text-2xl font-bold text-yn-blue">{memory.keys.toLocaleString()}</p>
              <p className="text-sm text-yn-text-muted">Total Keys (DBSIZE)</p>
            </div>
          </div>
          <div className="mt-6 h-2 w-full rounded-full bg-yn-navy overflow-hidden">
            <div className="h-full bg-gradient-to-r from-yn-green to-yn-gold" style={{ width: `${memory.limit > 0 ? usedPercent : 0}%` }} />
          </div>
          <p className="mt-2 text-xs text-yn-text-muted">
            المصدر: Redis <span className="font-mono">INFO memory / INFO stats / DBSIZE</span>
          </p>
        </div>
      )}

      {activeTab === 'ttl' && (
        <div className="yn-card yn-card-liquid yn-glass p-6 space-y-4">
          <div className="grid grid-cols-1 sm:grid-cols-3 gap-4 text-center">
            <div className="p-4 bg-yn-navy rounded-lg">
              <p className="text-2xl font-bold text-yn-blue">{ttlStats.noExpiry}</p>
              <p className="text-sm text-yn-text-muted">بلا انتهاء (No TTL)</p>
            </div>
            <div className="p-4 bg-yn-navy rounded-lg">
              <p className="text-2xl font-bold text-yn-gold">{ttlStats.underHour}</p>
              <p className="text-sm text-yn-text-muted">تنتهي خلال ساعة</p>
            </div>
            <div className="p-4 bg-yn-navy rounded-lg">
              <p className="text-2xl font-bold text-yn-green">{ttlStats.underDay}</p>
              <p className="text-sm text-yn-text-muted">تنتهي خلال يوم</p>
            </div>
          </div>
          <DataTable
            columns={[
              { key: 'key', title: 'أقرب المفاتيح انتهاءً', cell: (k: CacheKeyRow) => <span className="font-mono text-sm text-yn-text-secondary">{k.key}</span> },
              { key: 'type', title: 'Type', cell: (k: CacheKeyRow) => <Badge variant="blue">{k.type}</Badge> },
              { key: 'ttl', title: 'تبقّى', cell: (k: CacheKeyRow) => <span className="text-yn-text-secondary">{Math.floor(k.ttl / 60)} min</span> },
            ]}
            data={ttlStats.soonest}
            rowKey="key"
            emptyMessage="لا مفاتيح بمهلة انتهاء ضمن الدفعة المحمّلة"
          />
          <p className="text-xs text-yn-text-muted">
            الإحصاءات محسوبة على الدفعة المحمّلة ({allKeys.length} مفتاحًا) لا على القاعدة كاملة — لا ندّعي أكثر مما قِسناه.
          </p>
        </div>
      )}

      <Dialog open={flushDialogOpen} onClose={() => setFlushDialogOpen(false)} title="Invalidate Cache by Pattern">
        <p className="text-sm text-yn-text-secondary mb-4">
          إبطال مقيّد بنمط Redis — لا يوجد مسح شامل (FLUSHALL/FLUSHDB) في هذه اللوحة عمدًا.
        </p>
        <div className="space-y-4">
          <div>
            <label className="yn-label">Pattern (مطلوب)</label>
            <input
              value={flushPattern}
              onChange={(e) => setFlushPattern(e.target.value)}
              placeholder="session:*"
              className="yn-input font-mono"
            />
            <p className="mt-1 text-xs text-yn-text-muted">«*» وحده مرفوض من الخادم (400) — حدّد البادئة دائمًا.</p>
          </div>
          <div>
            <label className="yn-label">سقف الدفعة</label>
            <select value={flushLimit} onChange={(e) => setFlushLimit(Number(e.target.value))} className="yn-select">
              <option value={100}>100 مفتاح</option>
              <option value={500}>500 مفتاح</option>
              <option value={1000}>1000 مفتاح</option>
              <option value={2000}>2000 مفتاح</option>
            </select>
          </div>
          <div className="flex items-center gap-2">
            <input
              type="checkbox"
              id="confirm"
              checked={confirmChecked}
              onChange={(e) => setConfirmChecked(e.target.checked)}
              className="w-4 h-4 rounded border-yn-border bg-yn-navy text-yn-green focus:ring-yn-green"
            />
            <label htmlFor="confirm" className="text-sm text-yn-text-secondary">
              أُقرّ بأن الحذف نهائي — ويُسجَّل في سجل التدقيق
            </label>
          </div>
          <Button variant="primary" disabled={!flushValid || invalidate.isPending} onClick={() => void handleInvalidate()} className="w-full">
            {invalidate.isPending ? 'جارٍ الإبطال…' : 'تنفيذ الإبطال'}
          </Button>
        </div>
      </Dialog>
    </div>
  );
}

function StatCard({ label, value, icon: Icon, color }: { label: string; value: string; icon: React.ComponentType<{ className?: string }>; color: string }) {
  return (
    <div className="yn-card yn-card-liquid yn-glass p-4">
      <div className="flex items-center justify-between">
        <div>
          <p className="text-sm font-medium text-yn-text-secondary">{label}</p>
          <p className="text-xl font-bold text-yn-text mt-1">{value}</p>
        </div>
        <div className={cn('w-10 h-10 rounded-lg flex items-center justify-center', `bg-yn-${color}/15`)} >
          <Icon className={cn('w-5 h-5', `text-yn-${color}`)} aria-hidden="true" />
        </div>
      </div>
    </div>
  );
}

export default CachePage;
