import { useState } from 'react';
import { Link } from '@tanstack/react-router';
import { Download } from 'lucide-react';
import { useAuditLog } from '@/api/queries';
import { formatRelativeTime } from '@/utils';
import { useTranslation } from 'react-i18next';
import { Button } from '@/components/ui/button';
import { SearchInput } from '@/components/ui/search-input';
import { Select } from '@/components/ui/select';
import { DateRangePicker } from '@/components/ui/date-range-picker';
import { DataTable } from '@/components/ui/data-table';
import { Pagination } from '@/components/ui/pagination';
import { Badge } from '@/components/ui/badge';
import { EmptyState, ErrorState } from '@/pages/_shared';
import { toast } from '@/hooks/useToast';
import { apiFetch } from '../../api';

const CATEGORY_OPTIONS = [
  { value: '', label: 'All Categories' },
  { value: 'USER_MANAGEMENT', label: 'User Management' },
  { value: 'CONTENT_MODERATION', label: 'Content Moderation' },
  { value: 'SYSTEM_CONFIG', label: 'System Config' },
  { value: 'SECURITY', label: 'Security' },
  { value: 'BACKUP', label: 'Backup' },
  { value: 'FEATURE_FLAGS', label: 'Feature Flags' },
];

const SEVERITY_OPTIONS = [
  { value: '', label: 'All Severities' },
  { value: 'INFO', label: 'Info' },
  { value: 'WARNING', label: 'Warning' },
  { value: 'ERROR', label: 'Error' },
  { value: 'CRITICAL', label: 'Critical' },
];

interface AuditRow {
  id: string;
  adminId?: string;
  adminUsername?: string;
  action?: string;
  category?: string;
  severity?: string;
  targetType?: string;
  targetId?: string;
  description?: string;
  ipAddress?: string;
  userAgent?: string;
  createdAt?: string;
}

export function AuditLogPage() {
  const { t } = useTranslation();
  const [page, setPage] = useState(0);
  const [size] = useState(25);
  const [search, setSearch] = useState('');
  const [category, setCategory] = useState('');
  const [severity, setSeverity] = useState('');
  const [startDate, setStartDate] = useState('');
  const [endDate, setEndDate] = useState('');
  const [exporting, setExporting] = useState(false);

  const { data, error, isLoading, refetch } = useAuditLog({
    page,
    size,
    search: search.trim() || undefined,
    category: category || undefined,
    severity: severity || undefined,
    startDate: startDate || undefined,
    endDate: endDate || undefined,
  });

  // لا بيانات وهمية: ما يعرضه الجدول هو ما أعاده الخادم حرفيًا. سجل فارغ يعني
  // سجلًا فارغًا فعلًا، وتعذّر الاتصال يعني خطأً ظاهرًا مع إعادة المحاولة.
  const logs = (data?.content ?? []) as AuditRow[];
  const totalPages = data?.totalPages ?? 0;
  const totalElements = data?.totalElements ?? 0;

  /** أي تغيير في الفلتر يُبطل رقم الصفحة الحالية، وإلا طلبنا صفحة خارج النطاق. */
  const withPageReset = <T,>(setter: (value: T) => void) => (value: T) => {
    setter(value);
    setPage(0);
  };

  const exportCsv = async () => {
    setExporting(true);
    try {
      const params = new URLSearchParams();
      if (category) params.set('category', category);
      if (severity) params.set('severity', severity);
      if (search.trim()) params.set('search', search.trim());
      if (startDate) params.set('startDate', startDate);
      if (endDate) params.set('endDate', endDate);
      const res = await apiFetch(`/api/admin/audit/export?${params.toString()}`);
      if (res.ok) {
        const blob = await res.blob();
        const url = URL.createObjectURL(blob);
        const a = document.createElement('a');
        a.href = url;
        a.download = `audit-${new Date().toISOString().slice(0, 10)}.csv`;
        a.click();
        URL.revokeObjectURL(url);
        toast.success('تم تصدير سجل التدقيق');
        return;
      }
      // فشل تصدير الخادم: نصدّر الصفحة الحقيقية المحمّلة فقط، ونقول ذلك صراحة.
      if (logs.length === 0) {
        throw new Error(`تعذّر تصدير الخادم (HTTP ${res.status}) ولا توجد صفوف محمّلة`);
      }
      const header = ['createdAt', 'adminUsername', 'action', 'category', 'severity', 'targetType', 'targetId', 'ipAddress', 'description'];
      const cell = (v: unknown) => `"${String(v ?? '').replace(/"/g, '""')}"`;
      const lines = [header.join(',')];
      for (const r of logs) {
        lines.push(
          [r.createdAt, r.adminUsername, r.action, r.category, r.severity, r.targetType, r.targetId, r.ipAddress, r.description]
            .map(cell)
            .join(',')
        );
      }
      const blob = new Blob(['\uFEFF' + lines.join('\n')], { type: 'text/csv;charset=utf-8' });
      const url = URL.createObjectURL(blob);
      const a = document.createElement('a');
      a.href = url;
      a.download = `audit-page-${page + 1}-${new Date().toISOString().slice(0, 10)}.csv`;
      a.click();
      URL.revokeObjectURL(url);
      toast.warning('تصدير الصفحة الحالية فقط', `تعذّر تصدير الخادم (HTTP ${res.status}) — صُدّرت ${logs.length} صفًا محمّلًا`);
    } catch (e: unknown) {
      toast.error('فشل التصدير', e instanceof Error ? e.message : 'تعذّر التصدير');
    } finally {
      setExporting(false);
    }
  };

  const columns = [
    {
      key: 'createdAt',
      title: 'Timestamp',
      cell: (log: AuditRow) => (
        <span className="text-yn-text-secondary font-mono text-sm" title={log.createdAt ?? ''}>
          {log.createdAt ? formatRelativeTime(log.createdAt) : '—'}
        </span>
      ),
    },
    {
      key: 'adminUsername',
      title: 'Admin',
      cell: (log: AuditRow) => {
        const name = log.adminUsername || log.adminId || 'system';
        return (
          <div className="flex items-center gap-2">
            <div className="w-6 h-6 rounded-full bg-yn-green/15 flex items-center justify-center text-xs text-yn-green font-bold">
              {name.slice(0, 1).toUpperCase()}
            </div>
            <span className="font-medium text-yn-text">{name}</span>
          </div>
        );
      },
    },
    {
      key: 'action',
      title: 'Action',
      cell: (log: AuditRow) => <Badge variant="blue">{log.action ?? '—'}</Badge>,
    },
    {
      key: 'category',
      title: 'Category',
      cell: (log: AuditRow) => <Badge variant="gold">{log.category ?? '—'}</Badge>,
    },
    {
      key: 'severity',
      title: 'Severity',
      cell: (log: AuditRow) => <Badge variant={getSeverityVariant(log.severity)}>{log.severity ?? 'INFO'}</Badge>,
    },
    {
      key: 'target',
      title: 'Target',
      cell: (log: AuditRow) => (
        <div>
          <p className="font-mono text-yn-text-secondary text-sm">{log.targetType ?? '—'}</p>
          <p className="text-xs text-yn-text-muted">{log.targetId ?? '—'}</p>
        </div>
      ),
    },
    {
      key: 'ipAddress',
      title: 'IP Address',
      cell: (log: AuditRow) => <span className="text-yn-text-secondary font-mono text-sm">{log.ipAddress ?? '—'}</span>,
    },
    {
      key: 'description',
      title: 'Details',
      cell: (log: AuditRow) => (
        <span className="text-sm text-yn-text-secondary line-clamp-2 max-w-[22rem]" title={log.description ?? ''}>
          {log.description || '—'}
        </span>
      ),
    },
  ];

  if (isLoading && !data) {
    return <div className="yn-loading"><div className="yn-spinner" /></div>;
  }

  return (
    <div className="yn-page space-y-6">
      {/* زر عودة للصفحة العامة — دمج الازدواج الملاحي بدون حذف */}
      <div className="flex flex-wrap gap-2">
        <Button asChild variant="secondary" size="sm"><Link to="/system">عودة إلى System</Link></Button>
      </div>

      <div className="flex flex-col sm:flex-row sm:items-center sm:justify-between gap-4">
        <div>
          <h1 className="font-heading text-2xl font-bold text-yn-text">{t('system.auditLog.title')}</h1>
          <p className="text-yn-text-secondary">{t('system.auditLog.subtitle')}</p>
        </div>
        <div className="flex items-center gap-3">
          <Button variant="secondary" size="sm" onClick={() => void exportCsv()} disabled={exporting || !data}>
            <Download className="w-4 h-4 mr-2" />
            {exporting ? 'Exporting…' : 'Export CSV'}
          </Button>
        </div>
      </div>

      <div className="yn-card yn-card-liquid yn-glass p-4">
        <div className="flex flex-col sm:flex-row gap-4">
          <SearchInput
            value={search}
            onChange={withPageReset(setSearch)}
            placeholder="بحث في المشرف/الإجراء/الوصف/الهدف…"
            className="flex-1 max-w-sm"
          />
          <Select value={category} onChange={withPageReset(setCategory)} options={CATEGORY_OPTIONS} placeholder="Category" className="w-full sm:w-40" />
          <Select value={severity} onChange={withPageReset(setSeverity)} options={SEVERITY_OPTIONS} placeholder="Severity" className="w-full sm:w-40" />
          <DateRangePicker
            from={startDate}
            to={endDate}
            onChange={(from, to) => {
              setStartDate(from);
              setEndDate(to);
              setPage(0);
            }}
          />
        </div>
      </div>

      <div className="yn-card yn-card-liquid yn-glass overflow-hidden">
        {error ? (
          <ErrorState
            message={`تعذّر قراءة سجل التدقيق من الخادم: ${error instanceof Error ? error.message : 'خطأ غير معروف'}`}
            onRetry={() => void refetch()}
          />
        ) : logs.length === 0 ? (
          <EmptyState
            title="لا توجد سجلات تدقيق"
            description={
              search || category || severity || startDate || endDate
                ? 'لا توجد نتائج مطابقة للفلاتر الحالية. جرّب توسيع النطاق الزمني أو إزالة فلتر.'
                : 'لم يُسجَّل أي إجراء إداري بعد.'
            }
          />
        ) : (
          <DataTable columns={columns} data={logs} isLoading={isLoading} rowKey="id" emptyMessage="No audit log entries" />
        )}

        <div className="flex flex-col sm:flex-row sm:items-center sm:justify-between gap-2 px-4 py-3 border-t border-yn-border">
          <span className="text-xs text-yn-text-muted">
            {totalElements.toLocaleString('ar-EG')} سجل — الصفحة {page + 1} من {Math.max(totalPages, 1)}
          </span>
          <Pagination page={page} totalPages={totalPages} onChange={setPage} />
        </div>
      </div>
    </div>
  );
}

function getSeverityVariant(severity: string | undefined): 'green' | 'gold' | 'blue' | 'danger' {
  switch (severity) {
    case 'INFO': return 'blue';
    case 'WARNING': return 'gold';
    case 'ERROR': return 'danger';
    case 'CRITICAL': return 'danger';
    default: return 'blue';
  }
}

export default AuditLogPage;
