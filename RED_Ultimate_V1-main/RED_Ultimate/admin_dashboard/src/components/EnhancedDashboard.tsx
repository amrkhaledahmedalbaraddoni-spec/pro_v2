/**
 * مكون Dashboard المحسّن مع الإحصائيات الحية والرسوم البيانية المتقدمة
 * يستخدم API الجديدة من dashboard.ts
 */

'use client';

import { useState, useEffect, useCallback } from 'react';
import { useQuery, useQueryClient } from '@tanstack/react-query';
import { useTranslation } from 'react-i18next';
import {
  BarChart, Bar, LineChart, Line, AreaChart, Area,
  PieChart, Pie, Cell, XAxis, YAxis, CartesianGrid,
  Tooltip, Legend, ResponsiveContainer, ComposedChart
} from 'recharts';
import {
  Users, MessageSquare, Phone, TrendingUp, TrendingDown,
  Activity, Server, Database, HardDrive, AlertTriangle,
  CheckCircle, RefreshCw, Calendar, Download, Zap
} from 'lucide-react';
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card';
import { Button } from '@/components/ui/button';
import { Badge } from '@/components/ui/badge';
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select';
import { useTheme } from '@/components/providers/ThemeProvider';
import { useSocket } from '@/components/providers/SocketProvider';
import { GRADIENTS } from '@/utils/theme-enhanced';
import {
} from '@/api/dashboard';
import { apiFetch } from '@/api';

interface DashboardMetrics {
  totalUsers: number;
  activeUsers: number;
  totalMessages: number;
  totalCalls: number;
  groupsCount: number;
  storiesCount: number;
  reactionsCount: number;
  usersGrowth: number;
  messagesGrowth: number;
  callsGrowth: number;
  storageUsed: number;
  storageTotal: number;
}

interface SystemHealth {
  cpu: number;
  memory: number;
  disk: number;
  network: number;
  status: 'healthy' | 'degraded' | 'critical';
}

interface ChartDataPoint {
  date: string;
  users: number;
  messages: number;
  calls: number;
  revenue: number;
}

const CHART_COLORS = {
  primary: '#3b82f6',    // Blue
  success: '#10b981',    // Green
  warning: '#f59e0b',    // Amber
  error: '#ef4444',      // Red
  secondary: '#8b5cf6',  // Purple
};

/**
 * مكون بطاقة الإحصائية
 */
function StatCard({
  title,
  value,
  trend,
  icon: Icon,
  color = 'primary',
  loading = false,
}: {
  title: string;
  value: string | number;
  trend?: number;
  icon: React.ComponentType<{ className?: string }>;
  color?: keyof typeof CHART_COLORS;
  loading?: boolean;
}) {
  const trendIcon = trend !== undefined && trend !== 0 ? (trend > 0 ? TrendingUp : TrendingDown) : null;
  const trendColor = trend !== undefined && trend > 0 ? 'text-green-500' : 'text-red-500';

  return (
    <Card className="overflow-hidden hover:shadow-lg transition-shadow">
      <CardContent className="p-6">
        <div className="flex items-center justify-between">
          <div className="space-y-2">
            <p className="text-sm font-medium text-muted-foreground">{title}</p>
            <p className="text-2xl font-bold">{loading ? '—' : value}</p>
            {trend !== undefined && !loading && (
              <div className={`flex items-center gap-1 text-sm ${trendColor}`}>
                {trendIcon && (() => { const TrendIcon = trendIcon; return <TrendIcon className="h-4 w-4" />; })()}
                <span>{trend > 0 ? '+' : ''}{trend}%</span>
              </div>
            )}
          </div>
          <div className={`p-3 rounded-lg bg-${color}-100 dark:bg-${color}-900/30`}>
            <Icon className={`h-8 w-8 text-${color}-600 dark:text-${color}-400`} />
          </div>
        </div>
      </CardContent>
    </Card>
  );
}

/**
 * مكون نظام الصحة
 */
function HealthMonitor({ health, loading }: { health?: SystemHealth; loading?: boolean }) {
  const { isDark } = useTheme();
  const { t } = useTranslation();

  if (loading || !health) {
    return <div className="h-32 bg-muted animate-pulse rounded-lg" />;
  }

  const metrics = [
    { label: 'CPU', value: health.cpu, max: 100, color: CHART_COLORS.primary },
    { label: 'Memory', value: health.memory, max: 100, color: CHART_COLORS.secondary },
    { label: 'Disk', value: health.disk, max: 100, color: CHART_COLORS.warning },
    { label: 'Network', value: health.network, max: 100, color: CHART_COLORS.success },
  ];

  return (
    <Card>
      <CardHeader>
        <CardTitle className="flex items-center gap-2">
          <Server className="h-5 w-5" />
          {t('dashboard.systemHealth')}
        </CardTitle>
      </CardHeader>
      <CardContent>
        <div className="space-y-4">
          {metrics.map((metric) => (
            <div key={metric.label}>
              <div className="flex justify-between mb-2">
                <span className="text-sm font-medium">{metric.label}</span>
                <span className="text-sm font-semibold">{metric.value}%</span>
              </div>
              <div className="w-full h-2 bg-muted rounded-full overflow-hidden">
                <div
                  className="h-full transition-all duration-300"
                  style={{
                    width: `${metric.value}%`,
                    backgroundColor: metric.color,
                  }}
                />
              </div>
            </div>
          ))}
          <div className="flex items-center gap-2 mt-4 pt-4 border-t">
            {health.status === 'healthy' && (
              <>
                <CheckCircle className="h-4 w-4 text-green-500" />
                <span className="text-sm text-green-600 dark:text-green-400">{t('dashboard.allSystemsOperational')}</span>
              </>
            )}
            {health.status === 'degraded' && (
              <>
                <AlertTriangle className="h-4 w-4 text-yellow-500" />
                <span className="text-sm text-yellow-600 dark:text-yellow-400">{t('dashboard.someIssuesDetected')}</span>
              </>
            )}
            {health.status === 'critical' && (
              <>
                <AlertTriangle className="h-4 w-4 text-red-500" />
                <span className="text-sm text-red-600 dark:text-red-400">{t('dashboard.criticalIssuesDetected')}</span>
              </>
            )}
          </div>
        </div>
      </CardContent>
    </Card>
  );
}

/**
 * مكون الرسم البياني للنشاط
 */
function ActivityChart({ data, loading }: { data?: ChartDataPoint[]; loading?: boolean }) {
  const { isDark } = useTheme();
  const { t } = useTranslation();

  if (loading) {
    return <div className="h-64 bg-muted animate-pulse rounded-lg" />;
  }

  return (
    <Card>
      <CardHeader>
        <CardTitle className="flex items-center gap-2">
          <Activity className="h-5 w-5" />
          {t('dashboard.activityOverTime')}
        </CardTitle>
      </CardHeader>
      <CardContent>
        <ResponsiveContainer width="100%" height={300}>
          <ComposedChart data={data || []}>
            <CartesianGrid strokeDasharray="3 3" stroke={isDark ? '#334155' : '#e5e7eb'} />
            <XAxis dataKey="date" stroke={isDark ? '#94a3b8' : '#6b7280'} />
            <YAxis stroke={isDark ? '#94a3b8' : '#6b7280'} />
            <Tooltip
              contentStyle={{
                backgroundColor: isDark ? '#1e293b' : '#ffffff',
                border: `1px solid ${isDark ? '#475569' : '#e5e7eb'}`
              }}
            />
            <Legend />
            <Bar dataKey="messages" fill={CHART_COLORS.primary} />
            <Bar dataKey="calls" fill={CHART_COLORS.success} />
            <Line type="monotone" dataKey="users" stroke={CHART_COLORS.secondary} strokeWidth={2} />
          </ComposedChart>
        </ResponsiveContainer>
      </CardContent>
    </Card>
  );
}

/**
 * المكون الرئيسي للـ Dashboard
 */
export function EnhancedDashboard() {
  const { t } = useTranslation();
  const { isDark } = useTheme();
  const { socket, isConnected } = useSocket();
  const [timeRange, setTimeRange] = useState<'24h' | '7d' | '30d'>('24h');
  const [refreshKey, setRefreshKey] = useState(0);

  // جلب الإحصائيات
  const { data: metrics, isLoading: metricsLoading } = useQuery({
    queryKey: ['dashboard', 'metrics', timeRange, refreshKey],
    queryFn: async () => {
      const response = await apiFetch(`/api/master/v1/stats/realtime?range=${timeRange}`);
      return response.json() as Promise<DashboardMetrics>;
    },
    staleTime: 10_000,
    refetchInterval: 30_000,
  });

  // جلب صحة النظام
  const { data: health, isLoading: healthLoading } = useQuery({
    queryKey: ['dashboard', 'health'],
    queryFn: async () => {
      const response = await apiFetch('/api/master/admin/system/stats');
      return response.json() as Promise<SystemHealth>;
    },
    staleTime: 30_000,
    refetchInterval: 30_000,
  });

  // جلب بيانات الرسم البياني
  const { data: chartData, isLoading: chartLoading } = useQuery({
    queryKey: ['dashboard', 'chart', timeRange, refreshKey],
    queryFn: async () => {
      const response = await apiFetch(`/api/master/v1/stats/activity?range=${timeRange}`);
      return response.json() as Promise<ChartDataPoint[]>;
    },
    staleTime: 15_000,
    refetchInterval: 60_000,
  });

  const handleRefresh = () => {
    setRefreshKey(prev => prev + 1);
  };

  const handleExport = async () => {
    // تصدير بيانات CSV
    const csv = [
      ['Metric', 'Value'],
      ['Total Users', metrics?.totalUsers || 0],
      ['Active Users', metrics?.activeUsers || 0],
      ['Total Messages', metrics?.totalMessages || 0],
      ['Total Calls', metrics?.totalCalls || 0],
      ['Groups', metrics?.groupsCount || 0],
    ].map(row => row.join(',')).join('\n');

    const blob = new Blob([csv], { type: 'text/csv' });
    const url = URL.createObjectURL(blob);
    const link = document.createElement('a');
    link.href = url;
    link.download = `dashboard-export-${new Date().toISOString()}.csv`;
    link.click();
  };

  return (
    <div className="space-y-6">
      {/* Header */}
      <div className="flex flex-col sm:flex-row sm:items-center sm:justify-between gap-4">
        <div>
          <h1 className="text-3xl font-bold">{t('dashboard.title')}</h1>
          <p className="text-muted-foreground">{t('dashboard.subtitle')}</p>
        </div>
        <div className="flex items-center gap-2">
          <div className="flex items-center gap-2">
            <div className={`h-2 w-2 rounded-full ${isConnected ? 'bg-green-500' : 'bg-red-500'}`} />
            <span className="text-sm text-muted-foreground">
              {isConnected ? t('dashboard.realtime') : t('dashboard.disconnected')}
            </span>
          </div>
          <Select value={timeRange} onValueChange={(v) => setTimeRange(v as typeof timeRange)}>
            <SelectTrigger className="w-[160px]">
              <SelectValue />
            </SelectTrigger>
            <SelectContent>
              <SelectItem value="24h">{t('common.last24Hours')}</SelectItem>
              <SelectItem value="7d">{t('common.last7Days')}</SelectItem>
              <SelectItem value="30d">{t('common.last30Days')}</SelectItem>
            </SelectContent>
          </Select>
          <Button variant="outline" size="sm" onClick={handleRefresh} disabled={metricsLoading}>
            <RefreshCw className={`h-4 w-4 ${metricsLoading ? 'animate-spin' : ''}`} />
          </Button>
          <Button variant="outline" size="sm" onClick={handleExport}>
            <Download className="h-4 w-4" />
          </Button>
        </div>
      </div>

      {/* Main Statistics Grid */}
      <div className="grid gap-4 md:grid-cols-2 lg:grid-cols-4">
        <StatCard
          title={t('dashboard.metrics.totalUsers')}
          value={metrics?.totalUsers?.toLocaleString() || '—'}
          trend={metrics?.usersGrowth}
          icon={Users}
          color="primary"
          loading={metricsLoading}
        />
        <StatCard
          title={t('dashboard.metrics.activeUsers')}
          value={metrics?.activeUsers?.toLocaleString() || '—'}
          icon={Activity}
          color="success"
          loading={metricsLoading}
        />
        <StatCard
          title={t('dashboard.metrics.totalMessages')}
          value={metrics?.totalMessages?.toLocaleString() || '—'}
          trend={metrics?.messagesGrowth}
          icon={MessageSquare}
          color="secondary"
          loading={metricsLoading}
        />
        <StatCard
          title={t('dashboard.metrics.totalCalls')}
          value={metrics?.totalCalls?.toLocaleString() || '—'}
          trend={metrics?.callsGrowth}
          icon={Phone}
          color="warning"
          loading={metricsLoading}
        />
      </div>

      {/* Secondary Statistics */}
      <div className="grid gap-4 md:grid-cols-3">
        <StatCard
          title={t('dashboard.metrics.groups')}
          value={metrics?.groupsCount || '—'}
          icon={Users}
          loading={metricsLoading}
        />
        <StatCard
          title={t('dashboard.metrics.stories')}
          value={metrics?.storiesCount || '—'}
          icon={Activity}
          loading={metricsLoading}
        />
        <StatCard
          title={t('dashboard.metrics.reactions')}
          value={metrics?.reactionsCount || '—'}
          icon={MessageSquare}
          loading={metricsLoading}
        />
      </div>

      {/* Charts and Health Monitor */}
      <div className="grid gap-6 lg:grid-cols-3">
        <div className="lg:col-span-2">
          <ActivityChart data={chartData} loading={chartLoading} />
        </div>
        <HealthMonitor health={health} loading={healthLoading} />
      </div>

      {/* Storage Information */}
      {metrics && (
        <Card>
          <CardHeader>
            <CardTitle className="flex items-center gap-2">
              <HardDrive className="h-5 w-5" />
              {t('dashboard.storage')}
            </CardTitle>
          </CardHeader>
          <CardContent>
            <div className="space-y-4">
              <div>
                <div className="flex justify-between mb-2">
                  <span className="text-sm font-medium">{t('dashboard.storageUsed')}</span>
                  <span className="text-sm font-semibold">
                    {(metrics.storageUsed / (1024 ** 3)).toFixed(2)} GB / {(metrics.storageTotal / (1024 ** 3)).toFixed(2)} GB
                  </span>
                </div>
                <div className="w-full h-3 bg-muted rounded-full overflow-hidden">
                  <div
                    className="h-full bg-gradient-to-r from-blue-500 to-purple-500 transition-all"
                    style={{ width: `${(metrics.storageUsed / metrics.storageTotal) * 100}%` }}
                  />
                </div>
              </div>
              <div className="text-sm text-muted-foreground">
                {((metrics.storageUsed / metrics.storageTotal) * 100).toFixed(1)}% {t('dashboard.storageUsedPercentage')}
              </div>
            </div>
          </CardContent>
        </Card>
      )}
    </div>
  );
}
