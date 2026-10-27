import { GitBranch, AlertTriangle, CheckCircle, XCircle, RefreshCw, Download, MoreVertical, Eye } from 'lucide-react';
import { Link } from '@tanstack/react-router';
import { useTranslation } from 'react-i18next';
import { cn } from '@/utils';
import { Button } from '@/components/ui/button';
import { DataTable } from '@/components/ui/data-table';
import { Badge } from '@/components/ui/badge';
import { DropdownMenu } from '@/components/ui/simple-dropdown-menu';
import { useMigrations, type MigrationRow } from '@/api/queries';
import { EmptyState, ErrorState } from '@/pages/_shared';
import { toast } from '@/hooks/useToast';

/**
 * المصدر الوحيد: `GET /api/admin/system/migrations` عبر `flyway_schema_history`
 * (`AdminSystemOpsController`). لا صفوف وهمية: إن تعذّر السجل أو كان غير متاح
 * تظهر حالة خطأ صادقة مع السبب وإعادة المحاولة. «المعلّق» يبقى «—» لأن Flyway
 * لا يعرف الملفات غير المنفّذة من جدول السجل — لا نخترع رقمًا.
 */
export function MigrationsPage() {
  const { t } = useTranslation();
  const { data, error, isLoading, refetch } = useMigrations();

  const available = !!data?.available;
  const rows: MigrationRow[] = data?.migrations ?? [];
  const total = data?.total ?? rows.length;
  const failed = data?.failed ?? rows.filter((m) => m.status === 'FAILED').length;
  const applied = Math.max(0, total - failed);

  const exportReport = () => {
    if (rows.length === 0) {
      toast.warning('لا توجد هجرات للتصدير', 'السجل فارغ أو غير متاح');
      return;
    }
    const header = ['version', 'description', 'type', 'status', 'checksum', 'executedBy', 'executedAt', 'executionTime'];
    const cell = (v: unknown) => `"${String(v ?? '').replace(/"/g, '""')}"`;
    const lines = [header.join(',')];
    for (const m of rows) {
      lines.push([m.version, m.description, m.type, m.status, m.checksum, m.executedBy, m.executedAt, m.executionTime].map(cell).join(','));
    }
    const blob = new Blob(['\uFEFF' + lines.join('\n')], { type: 'text/csv;charset=utf-8' });
    const url = URL.createObjectURL(blob);
    const a = document.createElement('a');
    a.href = url;
    a.download = `migrations-${new Date().toISOString().slice(0, 10)}.csv`;
    a.click();
    URL.revokeObjectURL(url);
    toast.success('تم تصدير تقرير الهجرات', `${rows.length} صفًا حقيقيًا`);
  };

  const columns = [
    {
      key: 'version',
      title: 'Version',
      cell: (m: MigrationRow) => <span className="font-mono font-medium text-yn-text">{m.version}</span>,
    },
    {
      key: 'description',
      title: 'Description',
      cell: (m: MigrationRow) => <span className="text-yn-text-secondary">{m.description}</span>,
    },
    {
      key: 'type',
      title: 'Type',
      cell: (m: MigrationRow) => <Badge variant="blue">{m.type}</Badge>,
    },
    {
      key: 'status',
      title: 'Status',
      cell: (m: MigrationRow) => <Badge variant={getStatusVariant(m.status)}>{m.status}</Badge>,
    },
    {
      key: 'checksum',
      title: 'Checksum',
      cell: (m: MigrationRow) => <span className="font-mono text-yn-text-secondary text-sm">{m.checksum}</span>,
    },
    {
      key: 'executedBy',
      title: 'Executed By',
      cell: (m: MigrationRow) => <span className="text-yn-text-secondary">{m.executedBy}</span>,
    },
    {
      key: 'executedAt',
      title: 'Executed At',
      cell: (m: MigrationRow) =>
        m.executedAt ? (
          <span className="text-yn-text-secondary">{new Date(m.executedAt).toLocaleString()}</span>
        ) : (
          <span className="text-yn-text-muted">—</span>
        ),
    },
    {
      key: 'executionTime',
      title: 'Duration',
      cell: (m: MigrationRow) => <span className="text-yn-text-secondary font-mono">{m.executionTime}ms</span>,
    },
    {
      key: 'actions',
      title: 'Actions',
      cell: (m: MigrationRow) => (
        <DropdownMenu>
          <DropdownMenu.Trigger asChild>
            <Button variant="ghost" size="icon">
              <MoreVertical className="w-4 h-4" />
            </Button>
          </DropdownMenu.Trigger>
          <DropdownMenu.Content align="end">
            <DropdownMenu.Item
              onClick={() => toast.info('سكربت الهجرة', m.script || `V${m.version} (غير متاح في السجل)`)}
            >
              <Eye className="w-4 h-4 mr-2" />
              View Script Name
            </DropdownMenu.Item>
          </DropdownMenu.Content>
        </DropdownMenu>
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
          <h1 className="font-heading text-2xl font-bold text-yn-text">{t('system.migrations.title')}</h1>
          <p className="text-yn-text-secondary">{t('system.migrations.subtitle')}</p>
        </div>
        <div className="flex items-center gap-3">
          <Button variant="secondary" size="sm" onClick={exportReport} disabled={!available}>
            <Download className="w-4 h-4 mr-2" />
            Export Report
          </Button>
          <Button variant="secondary" size="sm" onClick={() => void refetch()}>
            <RefreshCw className="w-4 h-4 mr-2" />
            Refresh History
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
            message={`تعذّر قراءة سجل Flyway: ${error instanceof Error ? error.message : 'خطأ غير معروف'}`}
            onRetry={() => void refetch()}
          />
        </div>
      </div>
    );
  }

  if (!available) {
    return (
      <div className="yn-page space-y-6">
        {header}
        <div className="yn-card yn-card-liquid yn-glass">
          <ErrorState
            message={`سجل Flyway غير متاح: ${data?.reason ?? 'لم يوضّح الخادم السبب'}`}
            onRetry={() => void refetch()}
          />
        </div>
      </div>
    );
  }

  return (
    <div className="yn-page space-y-6">
      {header}

      {/* Status Summary — أرقام سجل Flyway الحقيقي */}
      <div className="grid grid-cols-1 md:grid-cols-4 gap-4">
        <StatCard label="Total Migrations" value={total} icon={GitBranch} color="blue" />
        <StatCard label="Applied" value={applied} icon={CheckCircle} color="green" />
        <StatCard label="Pending" value="—" icon={AlertTriangle} color="gold" hint="يحتاج مقارنة ملفات الهجرة بالسجل على الخادم — غير مكشوف بعد" />
        <StatCard label="Failed" value={failed} icon={XCircle} color="danger" />
      </div>

      <p className="text-xs text-yn-text-muted">
        الإصدار الحالي: <span className="font-mono text-yn-text-secondary">{data?.currentVersion ?? '—'}</span>
        {' · '}المصدر: <span className="font-mono">flyway_schema_history</span>
      </p>

      <div className="yn-card yn-card-liquid yn-glass overflow-hidden">
        {rows.length === 0 ? (
          <EmptyState title="لا توجد هجرات مسجلة" description="جدول flyway_schema_history فارغ أو لم تُنفَّذ أي هجرة بعد." />
        ) : (
          <DataTable columns={columns} data={rows} rowKey="id" emptyMessage="No migrations found" />
        )}
      </div>
    </div>
  );
}

function StatCard({
  label,
  value,
  icon: Icon,
  color,
  hint,
}: {
  label: string;
  value: number | string;
  icon: React.ComponentType<{ className?: string }>;
  color: string;
  hint?: string;
}) {
  return (
    <div className="yn-card yn-card-liquid yn-glass p-4" title={hint}>
      <div className="flex items-center justify-between">
        <div>
          <p className="text-sm font-medium text-yn-text-secondary">{label}</p>
          <p className="text-2xl font-bold text-yn-text mt-1">{value}</p>
        </div>
        <div className={cn('w-10 h-10 rounded-lg flex items-center justify-center', `bg-yn-${color}/15`)} >
          <Icon className={cn('w-5 h-5', `text-yn-${color}`)} aria-hidden="true" />
        </div>
      </div>
    </div>
  );
}

function getStatusVariant(status: string): 'green' | 'gold' | 'blue' | 'danger' {
  switch (status) {
    case 'SUCCESS': return 'green';
    case 'PENDING': return 'gold';
    case 'FAILED': return 'danger';
    default: return 'gold';
  }
}

export default MigrationsPage;
