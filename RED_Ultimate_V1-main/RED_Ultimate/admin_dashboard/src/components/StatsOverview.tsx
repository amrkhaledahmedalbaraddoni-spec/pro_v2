/**
 * مكون قائمة الإحصائيات الرئيسية (Stats Overview)
 * يعرض الإحصائيات الأساسية بشكل ملخص
 */

'use client';

import { useState, useEffect } from 'react';
import { useQuery } from '@tanstack/react-query';
import { useTranslation } from 'react-i18next';
import {
  BarChart, Bar, LineChart, Line,
  XAxis, YAxis, CartesianGrid, Tooltip,
  Legend, ResponsiveContainer, PieChart, Pie, Cell
} from 'recharts';
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card';
import { Badge } from '@/components/ui/badge';
import {
  Users, MessageSquare, Phone, Zap,
  TrendingUp, TrendingDown, Activity
} from 'lucide-react';
import { apiFetch } from '@/api';
import { useTheme } from '@/components/providers/ThemeProvider';

interface Stats {
  totalUsers: number;
  activeUsers: number;
  totalMessages: number;
  totalCalls: number;
  groupsCount: number;
  storiesCount: number;
}

interface TopStats {
  label: string;
  value: number | string;
  change: number;
  icon: React.ReactNode;
  color: string;
  percentage: number;
}

const STAT_COLORS = {
  users: '#3b82f6',
  active: '#10b981',
  messages: '#8b5cf6',
  calls: '#f59e0b',
  groups: '#06b6d4',
  stories: '#ec4899',
};

/**
 * مكون بطاقة الإحصائية البسيطة
 */
function SimpleStatCard({
  title,
  value,
  change,
  icon: Icon,
  color,
}: {
  title: string;
  value: number | string;
  change?: number;
  icon: React.ComponentType<{ className?: string; style?: React.CSSProperties }>;
  color: string;
}) {
  const isPositive = change && change > 0;

  return (
    <Card className="hover:shadow-lg transition-shadow">
      <CardContent className="p-6">
        <div className="flex items-start justify-between">
          <div className="space-y-1">
            <p className="text-sm text-muted-foreground">{title}</p>
            <p className="text-2xl font-bold">{value}</p>
            {change !== undefined && (
              <div className={`flex items-center gap-1 text-xs mt-2 ${
                isPositive ? 'text-green-600 dark:text-green-400' : 'text-red-600 dark:text-red-400'
              }`}>
                {isPositive ? <TrendingUp className="h-3 w-3" /> : <TrendingDown className="h-3 w-3" />}
                <span>{Math.abs(change)}% من الأمس</span>
              </div>
            )}
          </div>
          <div className="p-2 rounded-lg" style={{ backgroundColor: `${color}20` }}>
            <Icon className="h-6 w-6" style={{ color }} />
          </div>
        </div>
      </CardContent>
    </Card>
  );
}

/**
 * مكون الإحصائيات العامة
 */
export function StatsOverview() {
  const { t } = useTranslation();
  const { isDark } = useTheme();

  // جلب الإحصائيات
  const { data: stats, isLoading } = useQuery({
    queryKey: ['stats', 'overview'],
    queryFn: async () => {
      const response = await apiFetch('/api/master/v1/stats/realtime?range=24h');
      return response.json() as Promise<Stats>;
    },
    staleTime: 10_000,
    refetchInterval: 30_000,
  });

  if (isLoading) {
    return (
      <div className="grid gap-4 md:grid-cols-2 lg:grid-cols-3">
        {Array.from({ length: 3 }).map((_, i) => (
          <Card key={i} className="animate-pulse">
            <CardContent className="p-6 h-32 bg-muted rounded" />
          </Card>
        ))}
      </div>
    );
  }

  const topStats: TopStats[] = [
    {
      label: t('dashboard.metrics.totalUsers'),
      value: stats?.totalUsers || 0,
      change: 12.5,
      icon: <Users className="h-6 w-6" />,
      color: STAT_COLORS.users,
      percentage: ((stats?.activeUsers || 0) / (stats?.totalUsers || 1)) * 100,
    },
    {
      label: t('dashboard.metrics.activeUsers'),
      value: stats?.activeUsers || 0,
      change: 8.2,
      icon: <Activity className="h-6 w-6" />,
      color: STAT_COLORS.active,
      percentage: ((stats?.activeUsers || 0) / (stats?.totalUsers || 1)) * 100,
    },
    {
      label: t('dashboard.metrics.totalMessages'),
      value: (stats?.totalMessages || 0).toLocaleString(),
      change: 23.1,
      icon: <MessageSquare className="h-6 w-6" />,
      color: STAT_COLORS.messages,
      percentage: 100,
    },
    {
      label: t('dashboard.metrics.totalCalls'),
      value: (stats?.totalCalls || 0).toLocaleString(),
      change: 5.3,
      icon: <Phone className="h-6 w-6" />,
      color: STAT_COLORS.calls,
      percentage: 100,
    },
    {
      label: t('dashboard.metrics.groups'),
      value: (stats?.groupsCount || 0).toLocaleString(),
      change: 15.8,
      icon: <Zap className="h-6 w-6" />,
      color: STAT_COLORS.groups,
      percentage: 100,
    },
  ];

  return (
    <div className="space-y-6">
      {/* Grid of stats */}
      <div className="grid gap-4 md:grid-cols-2 lg:grid-cols-4">
        {topStats.slice(0, 4).map((stat, idx) => (
          <SimpleStatCard
            key={idx}
            title={stat.label}
            value={stat.value}
            change={stat.change}
            icon={() => stat.icon}
            color={stat.color}
          />
        ))}
      </div>

      {/* Summary Cards */}
      <div className="grid gap-4 md:grid-cols-2">
        {/* Activity Summary */}
        <Card>
          <CardHeader>
            <CardTitle className="flex items-center gap-2">
              <Activity className="h-5 w-5" />
              {t('dashboard.activitySummary')}
            </CardTitle>
          </CardHeader>
          <CardContent>
            <div className="space-y-3">
              <div className="flex justify-between items-center">
                <span className="text-sm text-muted-foreground">{t('dashboard.metrics.activeUsers')}</span>
                <span className="font-semibold">{stats?.activeUsers || 0}</span>
              </div>
              <div className="w-full h-2 bg-muted rounded-full overflow-hidden">
                <div
                  className="h-full bg-gradient-to-r from-green-500 to-emerald-500"
                  style={{ width: `${((stats?.activeUsers || 0) / (stats?.totalUsers || 1)) * 100}%` }}
                />
              </div>
              <p className="text-xs text-muted-foreground">
                {((stats?.activeUsers || 0) / (stats?.totalUsers || 1) * 100).toFixed(1)}% من المستخدمين نشطون
              </p>
            </div>
          </CardContent>
        </Card>

        {/* Top Features */}
        <Card>
          <CardHeader>
            <CardTitle className="flex items-center gap-2">
              <TrendingUp className="h-5 w-5" />
              {t('dashboard.topFeatures')}
            </CardTitle>
          </CardHeader>
          <CardContent>
            <div className="space-y-2">
              <Badge variant="outline" className="w-full justify-start">
                📱 {t('dashboard.metrics.totalMessages')}: {(stats?.totalMessages || 0).toLocaleString()}
              </Badge>
              <Badge variant="outline" className="w-full justify-start">
                📞 {t('dashboard.metrics.totalCalls')}: {(stats?.totalCalls || 0).toLocaleString()}
              </Badge>
              <Badge variant="outline" className="w-full justify-start">
                👥 {t('dashboard.metrics.groups')}: {stats?.groupsCount || 0}
              </Badge>
            </div>
          </CardContent>
        </Card>
      </div>
    </div>
  );
}

/**
 * مكون توزيع المستخدمين
 */
export function UserDistributionChart() {
  const { t } = useTranslation();
  const { isDark } = useTheme();

  const data = [
    { name: 'نشطون الآن', value: 3500, color: '#10b981' },
    { name: 'نشطون أمس', value: 2800, color: '#3b82f6' },
    { name: 'نشطون الأسبوع', value: 5200, color: '#8b5cf6' },
    { name: 'نشطون الشهر', value: 8900, color: '#f59e0b' },
  ];

  const COLORS = data.map(d => d.color);

  return (
    <Card>
      <CardHeader>
        <CardTitle>{t('dashboard.userDistribution')}</CardTitle>
      </CardHeader>
      <CardContent>
        <ResponsiveContainer width="100%" height={300}>
          <PieChart>
            <Pie
              data={data}
              cx="50%"
              cy="50%"
              labelLine={false}
              label={({ name, value }) => `${name}: ${value}`}
              outerRadius={80}
              fill="#8884d8"
              dataKey="value"
            >
              {data.map((entry, index) => (
                <Cell key={`cell-${index}`} fill={COLORS[index]} />
              ))}
            </Pie>
            <Tooltip
              contentStyle={{
                backgroundColor: isDark ? '#1e293b' : '#ffffff',
                border: `1px solid ${isDark ? '#475569' : '#e5e7eb'}`
              }}
            />
            <Legend />
          </PieChart>
        </ResponsiveContainer>
      </CardContent>
    </Card>
  );
}

/**
 * مكون الأنشطة الأخيرة
 */
export function RecentActivities() {
  const { t } = useTranslation();

  const activities = [
    { id: 1, type: 'message', user: 'أحمد', action: 'أرسل رسالة', time: 'منذ 5 دقائق', color: 'bg-blue-500' },
    { id: 2, type: 'call', user: 'فاطمة', action: 'بدأت مكالمة', time: 'منذ 15 دقيقة', color: 'bg-green-500' },
    { id: 3, type: 'group', user: 'محمد', action: 'أنشأ مجموعة', time: 'منذ ساعة', color: 'bg-purple-500' },
    { id: 4, type: 'story', user: 'سارة', action: 'نشرت قصة', time: 'منذ ساعتين', color: 'bg-pink-500' },
    { id: 5, type: 'reaction', user: 'علي', action: 'أضاف تفاعل', time: 'منذ 3 ساعات', color: 'bg-yellow-500' },
  ];

  return (
    <Card>
      <CardHeader>
        <CardTitle>{t('dashboard.recentActivities')}</CardTitle>
      </CardHeader>
      <CardContent>
        <div className="space-y-4">
          {activities.map((activity) => (
            <div key={activity.id} className="flex items-center gap-3 pb-4 border-b last:border-0 last:pb-0">
              <div className={`w-2 h-2 rounded-full ${activity.color}`} />
              <div className="flex-1">
                <p className="text-sm font-medium">
                  <span className="text-muted-foreground">{activity.user}</span>
                  <span className="text-foreground"> {activity.action}</span>
                </p>
                <p className="text-xs text-muted-foreground mt-1">{activity.time}</p>
              </div>
            </div>
          ))}
        </div>
      </CardContent>
    </Card>
  );
}
