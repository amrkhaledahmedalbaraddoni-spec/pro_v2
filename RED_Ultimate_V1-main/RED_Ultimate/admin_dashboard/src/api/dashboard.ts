/**
 * Dashboard API Client — ربط البيانات من الخادم
 * الملف: src/api/dashboard.ts
 * محسّن مع cache و WebSocket و error handling متقدم
 */

import { apiFetch } from '../api';

export interface DashboardMetrics {
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
  createdAt: string;
  // Fields محسّنة
  activeSessions?: number;
  onlineUsers?: number;
  deliveryRate?: number;
  systemStatus?: 'UP' | 'DOWN' | 'DEGRADED';
}

export interface SystemHealth {
  cpu: number;
  memory: number;
  disk: number;
  network: number;
  status: 'healthy' | 'degraded' | 'critical';
  timestamp: string;
  // Fields محسّنة
  cpu_percent?: number;
  memory_percent?: number;
  disk_percent?: number;
  network_mbps?: number;
  uptime_hours?: number;
  services?: Record<string, 'UP' | 'DOWN' | 'DEGRADED'>;
}

export interface ChartDataPoint {
  date: string;
  time?: string;
  users: number;
  messages: number;
  calls: number;
  revenue?: number;
  newUsers?: number;
  dau?: number;
  mau?: number;
  // Fields محسّنة
  errors?: number;
  latency?: number;
}

export interface Alert {
  id: string;
  level: 'P0' | 'P1' | 'P2';
  message: string;
  component: string;
  timestamp: string;
  acknowledged: boolean;
}

// Cache management
const cache = new Map<string, { data: unknown; timestamp: number }>();
const CACHE_DURATION = 30000; // 30 seconds

function getCached<T>(key: string): T | null {
  const cached = cache.get(key);
  if (cached && Date.now() - cached.timestamp < CACHE_DURATION) {
    return cached.data as T;
  }
  cache.delete(key);
  return null;
}

function setCache(key: string, data: unknown): void {
  cache.set(key, { data, timestamp: Date.now() });
}

/**
 * جلب الإحصائيات الحية
 */
export async function fetchDashboardMetrics(
  range: '24h' | '7d' | '30d' = '24h'
): Promise<DashboardMetrics> {
  const cacheKey = `metrics-${range}`;
  const cached = getCached<DashboardMetrics>(cacheKey);
  if (cached) return cached;

  try {
    const response = await apiFetch(
      `/api/master/v1/stats/realtime?range=${range}`
    );

    if (!response.ok) {
      throw new Error(`HTTP ${response.status}: ${response.statusText}`);
    }

    const data = await response.json();
    const result: DashboardMetrics = {
      totalUsers: data.totalUsers ?? data.total_users ?? 0,
      activeUsers: data.activeUsers ?? data.active_users ?? 0,
      totalMessages: data.totalMessages ?? data.messages_24h ?? 0,
      totalCalls: data.totalCalls ?? data.active_calls ?? 0,
      groupsCount: data.groupsCount ?? data.groups_count ?? 0,
      storiesCount: data.storiesCount ?? data.stories_count ?? 0,
      reactionsCount: data.reactionsCount ?? data.reactions_count ?? 0,
      usersGrowth: data.usersGrowth ?? data.users_growth ?? 0,
      messagesGrowth: data.messagesGrowth ?? data.messages_growth ?? 0,
      callsGrowth: data.callsGrowth ?? data.calls_growth ?? 0,
      storageUsed: data.storageUsed ?? data.storage_used ?? 0,
      storageTotal: data.storageTotal ?? data.storage_total ?? 1,
      createdAt: data.createdAt ?? data.created_at ?? new Date().toISOString(),
      activeSessions: data.activeSessions ?? data.active_sessions,
      onlineUsers: data.onlineUsers ?? data.online_users,
      deliveryRate: data.deliveryRate ?? data.delivery_rate_percent,
      systemStatus: data.systemStatus ?? data.system_status ?? 'UP',
    };
    setCache(cacheKey, result);
    return result;
  } catch (error) {
    console.error('Failed to fetch dashboard metrics:', error);
    throw error;
  }
}

/**
 * جلب بيانات الرسم البياني
 */
export async function fetchChartData(
  range: '24h' | '7d' | '30d' = '24h'
): Promise<ChartDataPoint[]> {
  const cacheKey = `chart-data-${range}`;
  const cached = getCached<ChartDataPoint[]>(cacheKey);
  if (cached) return cached;

  try {
    const response = await apiFetch(
      `/api/master/v1/stats/activity?range=${range}`
    );

    if (!response.ok) {
      throw new Error(`HTTP ${response.status}: ${response.statusText}`);
    }

    const data = await response.json();
    const result = Array.isArray(data) ? data : data.data || [];
    setCache(cacheKey, result);
    return result;
  } catch (error) {
    console.error('Failed to fetch chart data:', error);
    return [];
  }
}

/**
 * جلب صحة النظام
 */
export async function fetchSystemHealth(): Promise<SystemHealth> {
  const cacheKey = 'system-health';
  const cached = getCached<SystemHealth>(cacheKey);
  if (cached) return cached;

  try {
    const response = await apiFetch('/health');

    if (!response.ok) {
      throw new Error(`HTTP ${response.status}: ${response.statusText}`);
    }

    const data = await response.json();

    const cpu = data.cpu ?? data.cpu_percent ?? 0;
    const memory = data.memory ?? data.memory_percent ?? 0;
    const disk = data.disk ?? data.disk_percent ?? 0;
    const network = data.network ?? data.network_mbps ?? 0;

    const avgUsage = (cpu + memory + disk + network) / 4;
    let status: 'healthy' | 'degraded' | 'critical' = 'healthy';

    if (avgUsage > 80) {
      status = 'critical';
    } else if (avgUsage > 60) {
      status = 'degraded';
    }

    const result: SystemHealth = {
      cpu,
      memory,
      disk,
      network,
      status,
      timestamp: data.timestamp ?? new Date().toISOString(),
      cpu_percent: cpu,
      memory_percent: memory,
      disk_percent: disk,
      network_mbps: network,
      uptime_hours: data.uptime_hours ?? 0,
      services: data.services || {},
    };

    setCache(cacheKey, result);
    return result;
  } catch (error) {
    console.error('Failed to fetch system health:', error);
    return {
      cpu: 0,
      memory: 0,
      disk: 0,
      network: 0,
      status: 'critical',
      timestamp: new Date().toISOString(),
    };
  }
}

/**
 * جلب التنبيهات
 */
export async function fetchAlerts(): Promise<Alert[]> {
  try {
    const response = await apiFetch('/api/admin/alerts?limit=50');

    if (!response.ok) {
      throw new Error(`HTTP ${response.status}: ${response.statusText}`);
    }

    const data = await response.json();
    return Array.isArray(data) ? data : data.alerts || [];
  } catch (error) {
    console.error('Failed to fetch alerts:', error);
    return [];
  }
}

/**
 * الاستماع لتحديثات الإحصائيات عبر WebSocket
 */
export class DashboardWebSocket {
  private ws: WebSocket | null = null;
  private url: string;
  private reconnectAttempts = 0;
  private maxReconnectAttempts = 5;
  private reconnectDelay = 3000;
  private listeners: Map<string, Set<(data: any) => void>> = new Map();

  constructor(url: string = 'ws://localhost:8080/ws/master') {
    this.url = url;
  }

  connect(): Promise<void> {
    return new Promise((resolve, reject) => {
      try {
        this.ws = new WebSocket(this.url);

        this.ws.onopen = () => {
          console.log('[Dashboard WS] Connected');
          this.reconnectAttempts = 0;
          resolve();
        };

        this.ws.onmessage = (event) => {
          try {
            const message = JSON.parse(event.data);
            this.emit(message.type, message.data);
          } catch (error) {
            console.error('[Dashboard WS] Failed to parse message:', error);
          }
        };

        this.ws.onerror = (error) => {
          console.error('[Dashboard WS] Error:', error);
          reject(error);
        };

        this.ws.onclose = () => {
          console.log('[Dashboard WS] Disconnected');
          this.attemptReconnect();
        };
      } catch (error) {
        reject(error);
      }
    });
  }

  private attemptReconnect(): void {
    if (this.reconnectAttempts < this.maxReconnectAttempts) {
      this.reconnectAttempts++;
      const delay = this.reconnectDelay * this.reconnectAttempts;
      console.log(`[Dashboard WS] Attempting reconnect in ${delay}ms...`);
      setTimeout(() => this.connect().catch(console.error), delay);
    }
  }

  subscribe(eventType: string, callback: (data: any) => void): void {
    if (!this.listeners.has(eventType)) {
      this.listeners.set(eventType, new Set());
    }
    this.listeners.get(eventType)!.add(callback);
  }

  unsubscribe(eventType: string, callback: (data: any) => void): void {
    const callbacks = this.listeners.get(eventType);
    if (callbacks) {
      callbacks.delete(callback);
    }
  }

  private emit(eventType: string, data: any): void {
    const callbacks = this.listeners.get(eventType);
    if (callbacks) {
      callbacks.forEach((callback) => {
        try {
          callback(data);
        } catch (error) {
          console.error(`[Dashboard WS] Error in callback for ${eventType}:`, error);
        }
      });
    }
  }

  send(message: any): void {
    if (this.ws && this.ws.readyState === WebSocket.OPEN) {
      this.ws.send(JSON.stringify(message));
    }
  }

  disconnect(): void {
    if (this.ws) {
      this.ws.close();
      this.ws = null;
    }
  }

  isConnected(): boolean {
    return this.ws !== null && this.ws.readyState === WebSocket.OPEN;
  }
}

/**
 * الاستماع لتحديثات الإحصائيات عبر WebSocket (Legacy)
 */
export function setupMetricsListener(
  onMetricsUpdate: (data: Partial<DashboardMetrics>) => void,
  onHealthUpdate: (data: SystemHealth) => void,
  onAlertReceived: (alert: Alert) => void
): () => void {
  const ws = new DashboardWebSocket();

  ws.connect().catch(console.error);
  ws.subscribe('metrics:update', onMetricsUpdate);
  ws.subscribe('health:update', onHealthUpdate);
  ws.subscribe('alert:new', onAlertReceived);

  return () => {
    ws.disconnect();
  };
}

/**
 * تصدير البيانات CSV
 */
export async function exportMetricsAsCSV(
  metrics: DashboardMetrics,
  fileName: string = 'dashboard-metrics.csv'
): Promise<void> {
  const csv = [
    ['Metric', 'Value'],
    ['Total Users', metrics.totalUsers],
    ['Active Users', metrics.activeUsers],
    ['Total Messages', metrics.totalMessages],
    ['Total Calls', metrics.totalCalls],
    ['Groups', metrics.groupsCount],
    ['Stories', metrics.storiesCount],
    ['Reactions', metrics.reactionsCount],
    ['Users Growth', `${metrics.usersGrowth}%`],
    ['Messages Growth', `${metrics.messagesGrowth}%`],
    ['Calls Growth', `${metrics.callsGrowth}%`],
    ['Storage Used', `${(metrics.storageUsed / (1024 ** 3)).toFixed(2)} GB`],
    ['Storage Total', `${(metrics.storageTotal / (1024 ** 3)).toFixed(2)} GB`],
  ]
    .map(row => row.map(cell => `"${cell}"`).join(','))
    .join('\n');

  const blob = new Blob([csv], { type: 'text/csv;charset=utf-8;' });
  const link = document.createElement('a');
  const url = URL.createObjectURL(blob);

  link.setAttribute('href', url);
  link.setAttribute('download', fileName);
  link.style.visibility = 'hidden';

  document.body.appendChild(link);
  link.click();
  document.body.removeChild(link);
  URL.revokeObjectURL(url);
}

/**
 * تصدير البيانات PDF
 */
export async function exportMetricsAsPDF(
  metrics: DashboardMetrics,
  fileName: string = 'dashboard-metrics.pdf'
): Promise<void> {
  try {
    const response = await apiFetch(
      '/api/master/v1/stats/export?format=pdf',
      { method: 'POST', body: JSON.stringify(metrics) }
    );
    if (!response.ok) throw new Error('Export failed');

    const blob = await response.blob();
    const link = document.createElement('a');
    const url = URL.createObjectURL(blob);

    link.setAttribute('href', url);
    link.setAttribute('download', fileName);
    document.body.appendChild(link);
    link.click();
    document.body.removeChild(link);
    URL.revokeObjectURL(url);
  } catch (error) {
    console.error('Failed to export PDF:', error);
  }
}

/**
 * مسح الـ Cache
 */
export function clearDashboardCache(): void {
  cache.clear();
}

/**
 * إعادة تحميل بيانات معينة
 */
export function invalidateDashboardCache(pattern?: string): void {
  if (!pattern) {
    cache.clear();
    return;
  }
  for (const key of cache.keys()) {
    if (key.includes(pattern)) {
      cache.delete(key);
    }
  }
}
