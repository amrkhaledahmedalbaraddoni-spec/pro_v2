/**
 * Dashboard المحسّن والمتكامل
 * يجمع أفضل ما من Dashboard.tsx و MasterOverview.tsx
 * مع إضافة ميزات متقدمة وتصميم احترافي
 */

import { useMemo, useState, useEffect, useCallback } from 'react';
import {
  Card,
  Col,
  Row,
  Space,
  Statistic,
  Table,
  Tag,
  Typography,
  Button,
  Tooltip,
  Alert,
  Spin,
  Segmented,
  DatePicker,
} from 'antd';
import {
  ArrowUpOutlined,
  ArrowDownOutlined,
  CloudServerOutlined,
  DatabaseFilled,
  ThunderboltFilled,
  VideoCameraOutlined,
  TeamOutlined,
  MessageOutlined,
  SafetyCertificateFilled,
  CheckCircleOutlined,
  ExclamationCircleOutlined,
  CloseCircleOutlined,
  ReloadOutlined,
} from '@ant-design/icons';
import {
  LineChart,
  Line,
  BarChart,
  Bar,
  AreaChart,
  Area,
  PieChart,
  Pie,
  Cell,
  XAxis,
  YAxis,
  CartesianGrid,
  Tooltip as RechartTooltip,
  Legend,
  ResponsiveContainer,
  ComposedChart,
} from 'recharts';
import dayjs from 'dayjs';
import { apiFetch } from '../api';
import { usePolling } from '../hooks/usePolling';
import { getThemeColors, getGradient, RED_BRAND_COLORS } from '../utils/theme-enhanced';

// ============= Types =============

interface DashboardMetrics {
  active_users: number;
  pending_approvals: number;
  messages_24h: number;
  delivery_rate_percent: number;
  active_calls: number;
  system_health: string;
}

interface ChartDataPoint {
  time: string;
  messages: number;
  users: number;
  calls: number;
  errors: number;
}

interface SystemHealth {
  status: string;
  cpu_percent: number;
  memory_percent: number;
  disk_percent: number;
  network_mbps: number;
  uptime_hours: number;
  services: Record<string, 'UP' | 'DOWN' | 'DEGRADED'>;
}

// ============= Stat Card Component =============

interface StatCardProps {
  title: string;
  value: number | string;
  change?: number;
  icon: React.ReactNode;
  trend?: 'up' | 'down' | 'neutral';
  loading?: boolean;
  isDark?: boolean;
}

function StatCard({ title, value, change, icon, trend = 'neutral', loading, isDark }: StatCardProps) {
  const colors = getThemeColors(isDark || false);
  const trendColor = trend === 'up' ? colors.states.success : trend === 'down' ? colors.states.error : colors.text.tertiary;
  const TrendIcon = trend === 'up' ? ArrowUpOutlined : trend === 'down' ? ArrowDownOutlined : null;

  return (
    <Card
      loading={loading}
      style={{
        borderColor: colors.border.light,
        background: colors.background.primary,
      }}
      bordered
    >
      <Space direction="vertical" style={{ width: '100%' }}>
        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
          <Typography.Text type="secondary" style={{ fontSize: '12px', fontWeight: 600, textTransform: 'uppercase' }}>
            {title}
          </Typography.Text>
          <div style={{ fontSize: '20px', color: colors.text.secondary }}>
            {icon}
          </div>
        </div>
        <div style={{ fontSize: '28px', fontWeight: 700, color: colors.text.primary }}>
          {value}
        </div>
        {change !== undefined && (
          <div style={{ display: 'flex', alignItems: 'center', gap: '4px', color: trendColor, fontSize: '12px' }}>
            {TrendIcon && <TrendIcon />}
            <span>{change >= 0 ? '+' : ''}{change.toFixed(1)}%</span>
          </div>
        )}
      </Space>
    </Card>
  );
}

// ============= System Health Component =============

interface HealthProps {
  health: SystemHealth | null;
  isDark?: boolean;
}

function HealthMonitor({ health, isDark }: HealthProps) {
  const colors = getThemeColors(isDark || false);
  if (!health) return null;

  const getHealthColor = (percent: number) => {
    if (percent > 80) return colors.states.error;
    if (percent > 60) return colors.states.warning;
    return colors.states.success;
  };

  return (
    <Card title="صحة النظام" style={{ borderColor: colors.border.light }}>
      <Row gutter={[16, 16]}>
        {[
          { label: 'CPU', value: health.cpu_percent },
          { label: 'Memory', value: health.memory_percent },
          { label: 'Disk', value: health.disk_percent },
        ].map((item) => (
          <Col key={item.label} xs={8}>
            <div style={{ textAlign: 'center' }}>
              <div style={{
                width: '60px',
                height: '60px',
                borderRadius: '50%',
                border: `3px solid ${getHealthColor(item.value)}`,
                display: 'flex',
                alignItems: 'center',
                justifyContent: 'center',
                margin: '0 auto 8px',
                color: getHealthColor(item.value),
                fontWeight: 700,
              }}>
                {item.value}%
              </div>
              <Typography.Text style={{ fontSize: '12px' }}>{item.label}</Typography.Text>
            </div>
          </Col>
        ))}
      </Row>
    </Card>
  );
}

// ============= Main Dashboard Component =============

export default function EnhancedDashboard() {
  const [metrics, setMetrics] = useState<DashboardMetrics | null>(null);
  const [health, setHealth] = useState<SystemHealth | null>(null);
  const [chartData, setChartData] = useState<ChartDataPoint[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [isDark, setIsDark] = useState(false);
  const [timeRange, setTimeRange] = useState<'24h' | '7d' | '30d'>('24h');
  const [autoRefresh, setAutoRefresh] = useState(true);

  const colors = useMemo(() => getThemeColors(isDark), [isDark]);

  const loadData = useCallback(async () => {
    try {
      setLoading(true);
      const [metricsRes, healthRes, chartRes] = await Promise.all([
        apiFetch('/api/master/v1/stats/realtime?range=' + timeRange),
        apiFetch('/health'),
        apiFetch('/api/master/v1/stats/activity?range=' + timeRange),
      ]);

      if (metricsRes.ok) setMetrics(await metricsRes.json());
      if (healthRes.ok) setHealth(await healthRes.json());
      if (chartRes.ok) setChartData(await chartRes.json());

      setError(null);
    } catch (err: any) {
      setError(err.message || 'فشل تحميل البيانات');
    } finally {
      setLoading(false);
    }
  }, [timeRange]);

  usePolling(loadData, autoRefresh ? 5000 : null);

  useEffect(() => {
    loadData();
  }, [loadData]);

  const chartColors = {
    messages: RED_BRAND_COLORS.emerald.dark,
    users: RED_BRAND_COLORS.gold.dark,
    calls: colors.states.info,
    errors: colors.states.error,
  };

  return (
    <div style={{ padding: '24px', background: colors.background.primary, minHeight: '100vh' }}>
      <Space direction="vertical" style={{ width: '100%' }} size="large">
        {/* Header */}
        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
          <div>
            <Typography.Title level={2} style={{ margin: 0, color: colors.text.primary }}>
              لوحة التحكم الرئيسية
            </Typography.Title>
            <Typography.Text type="secondary">
              مراقبة حية لأداء النظام والمستخدمين والرسائل
            </Typography.Text>
          </div>
          <Space>
            <Button
              icon={<ReloadOutlined />}
              onClick={loadData}
              loading={loading}
            >
              تحديث
            </Button>
            <Segmented
              value={timeRange}
              onChange={(val) => setTimeRange(val as any)}
              options={[
                { label: '24 ساعة', value: '24h' },
                { label: '7 أيام', value: '7d' },
                { label: '30 يوماً', value: '30d' },
              ]}
            />
          </Space>
        </div>

        {error && <Alert type="error" message={error} showIcon />}

        {/* Stats Grid */}
        <Spin spinning={loading} style={{ opacity: loading ? 0.5 : 1 }}>
          <Row gutter={[16, 16]}>
            <Col xs={24} sm={12} lg={6}>
              <StatCard
                title="المستخدمون النشطون"
                value={metrics?.active_users ?? '—'}
                change={5.2}
                trend="up"
                icon={<TeamOutlined />}
                isDark={isDark}
              />
            </Col>
            <Col xs={24} sm={12} lg={6}>
              <StatCard
                title="الموافقات المعلقة"
                value={metrics?.pending_approvals ?? '—'}
                icon={<SafetyCertificateFilled />}
                isDark={isDark}
              />
            </Col>
            <Col xs={24} sm={12} lg={6}>
              <StatCard
                title="الرسائل في 24 ساعة"
                value={metrics?.messages_24h ?? '—'}
                change={12.5}
                trend="up"
                icon={<MessageOutlined />}
                isDark={isDark}
              />
            </Col>
            <Col xs={24} sm={12} lg={6}>
              <StatCard
                title="المكالمات النشطة"
                value={metrics?.active_calls ?? '—'}
                icon={<VideoCameraOutlined />}
                isDark={isDark}
              />
            </Col>
          </Row>
        </Spin>

        {/* Health Monitor */}
        <Row gutter={[16, 16]}>
          <Col xs={24} lg={12}>
            <HealthMonitor health={health} isDark={isDark} />
          </Col>
          <Col xs={24} lg={12}>
            <Card title="معدل الأداء" style={{ borderColor: colors.border.light }}>
              <Row gutter={[16, 16]}>
                <Col xs={12}>
                  <Statistic
                    title="معدل التوصيل"
                    value={metrics?.delivery_rate_percent ?? 0}
                    suffix="%"
                    prefix={<ArrowUpOutlined style={{ color: colors.states.success }} />}
                  />
                </Col>
                <Col xs={12}>
                  <Statistic
                    title="وقت التشغيل"
                    value={health?.uptime_hours ?? 0}
                    suffix="ساعة"
                  />
                </Col>
              </Row>
            </Card>
          </Col>
        </Row>

        {/* Charts */}
        <Row gutter={[16, 16]}>
          {/* Line Chart - Messages */}
          <Col xs={24} lg={12}>
            <Card title="الرسائل عبر الزمن">
              <ResponsiveContainer width="100%" height={300}>
                <LineChart data={chartData}>
                  <CartesianGrid strokeDasharray="3 3" />
                  <XAxis dataKey="time" />
                  <YAxis />
                  <RechartTooltip />
                  <Legend />
                  <Line
                    type="monotone"
                    dataKey="messages"
                    stroke={chartColors.messages}
                    dot={false}
                    name="الرسائل"
                  />
                </LineChart>
              </ResponsiveContainer>
            </Card>
          </Col>

          {/* Bar Chart - Users vs Calls */}
          <Col xs={24} lg={12}>
            <Card title="المستخدمون والمكالمات">
              <ResponsiveContainer width="100%" height={300}>
                <BarChart data={chartData}>
                  <CartesianGrid strokeDasharray="3 3" />
                  <XAxis dataKey="time" />
                  <YAxis />
                  <RechartTooltip />
                  <Legend />
                  <Bar dataKey="users" fill={chartColors.users} name="المستخدمون" />
                  <Bar dataKey="calls" fill={chartColors.calls} name="المكالمات" />
                </BarChart>
              </ResponsiveContainer>
            </Card>
          </Col>

          {/* Area Chart - Errors */}
          <Col xs={24} lg={12}>
            <Card title="الأخطاء">
              <ResponsiveContainer width="100%" height={300}>
                <AreaChart data={chartData}>
                  <CartesianGrid strokeDasharray="3 3" />
                  <XAxis dataKey="time" />
                  <YAxis />
                  <RechartTooltip />
                  <Legend />
                  <Area
                    type="monotone"
                    dataKey="errors"
                    fill={chartColors.errors}
                    stroke={chartColors.errors}
                    fillOpacity={0.3}
                    name="الأخطاء"
                  />
                </AreaChart>
              </ResponsiveContainer>
            </Card>
          </Col>

          {/* Pie Chart - Distribution */}
          <Col xs={24} lg={12}>
            <Card title="توزيع النشاط">
              <ResponsiveContainer width="100%" height={300}>
                <PieChart>
                  <Pie
                    data={[
                      { name: 'رسائل', value: metrics?.messages_24h || 0 },
                      { name: 'مستخدمون', value: metrics?.active_users || 0 },
                      { name: 'مكالمات', value: metrics?.active_calls || 0 },
                    ]}
                    cx="50%"
                    cy="50%"
                    labelLine={false}
                    label={({ name, value }) => `${name}: ${value}`}
                    outerRadius={80}
                    fill="#8884d8"
                    dataKey="value"
                  >
                    <Cell fill={chartColors.messages} />
                    <Cell fill={chartColors.users} />
                    <Cell fill={chartColors.calls} />
                  </Pie>
                  <RechartTooltip />
                </PieChart>
              </ResponsiveContainer>
            </Card>
          </Col>
        </Row>

        {/* Services Status */}
        <Card title="حالة الخدمات">
          <Row gutter={[16, 16]}>
            {health?.services && Object.entries(health.services).map(([service, status]) => (
              <Col key={service} xs={24} sm={12} lg={8}>
                <div style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
                  {status === 'UP' && <CheckCircleOutlined style={{ color: colors.states.success }} />}
                  {status === 'DOWN' && <CloseCircleOutlined style={{ color: colors.states.error }} />}
                  {status === 'DEGRADED' && <ExclamationCircleOutlined style={{ color: colors.states.warning }} />}
                  <span style={{ flex: 1 }}>{service}</span>
                  <Tag color={status === 'UP' ? 'green' : status === 'DOWN' ? 'red' : 'orange'}>
                    {status}
                  </Tag>
                </div>
              </Col>
            ))}
          </Row>
        </Card>
      </Space>
    </div>
  );
}
