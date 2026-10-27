import { useCallback, useEffect, useRef, useState } from 'react';
import { api } from './client';
import { usePolling } from '@/hooks/usePolling';
import type {
  User,
  Device,
  SecurityEvent,
  ActivityLogEntry,
  PageResponse,
  DashboardMetrics,
  SystemHealthComponent,
  Alert,
  ReportedContent,
  Channel,
  FeatureFlag,
  ConfigEntry,
  AuditLogEntry,
  Backup,
  Role,
  Permission,
  ApiKey,
  Session,
  Notification,
  RealtimeMetrics as CanonicalRealtimeMetrics,
} from '@/types';

/**
 * طبقة الاستعلامات الخفيفة — بلا `@tanstack/react-query`
 * (كانت ميتة: لا QueryClientProvider في التطبيق، فكانت الخطافات تنفجر
 * وقت التشغيل؛ وpackage.json ممنوع المساس به).
 * العقد الخارجي مطابق لسابقه: كل خطاف استعلام يُرجع
 * `{ data, error, isLoading, isError, isSuccess, refetch }`
 * وكل طفرة تُرجع `{ mutate, mutateAsync, isPending, isLoading, isError, error, reset }`.
 * لا setInterval خام هنا (حارس الواجهة) — الاستطلاع عبر usePolling.
 */

// ━━━━━━━━━━━━ RealtimeMetrics موحّدة ━━━━━━━━━━━━
// الشكل المسطّح من `@/types` (users/messages/calls/... أرقام) مضافًا إليه
// حقل `health` الاختياري من شكل `src/api.ts` الحي
// (`{ users, health: Record<string, SystemHealth>, timestamp }`).
// تقاطع الشكلين في نوع واحد: أي حمولة من أي عميل تُقبل وتُقرأ بأمان.
// (التوحيد النهائي في `src/types/index.ts` خارج النطاق — لم يُمسّ.)
export interface RealtimeMetrics extends CanonicalRealtimeMetrics {
  health?: Record<string, SystemHealthComponent>;
}

// ━━━━━━━━━━━━ مفاتيح الاستعلام (للتوافق — بلا QueryKey) ━━━━━━━━━━━━
export const queryKeys = {
  dashboard: {
    metrics: ['dashboard', 'metrics'] as const,
    health: ['dashboard', 'health'] as const,
    alerts: ['dashboard', 'alerts'] as const,
    realtime: ['dashboard', 'realtime'] as const,
  },
  users: {
    list: (params: Record<string, unknown>) => ['users', 'list', params] as const,
    detail: (id: string) => ['users', 'detail', id] as const,
    sessions: (id: string) => ['users', 'sessions', id] as const,
    devices: (id: string) => ['users', 'devices', id] as const,
    activity: (id: string) => ['users', 'activity', id] as const,
    security: (id: string) => ['users', 'security', id] as const,
  },
  content: {
    moderationQueue: (params: Record<string, unknown>) => ['content', 'moderation', params] as const,
    channels: (params: Record<string, unknown>) => ['content', 'channels', params] as const,
    channelDetail: (id: string) => ['content', 'channel', id] as const,
    reportedContent: (id: string) => ['content', 'reported', id] as const,
  },
  system: {
    featureFlags: ['system', 'featureFlags'] as const,
    config: (params: Record<string, unknown>) => ['system', 'config', params] as const,
    auditLog: (params: Record<string, unknown>) => ['system', 'auditLog', params] as const,
    backups: ['system', 'backups'] as const,
    migrations: ['system', 'migrations'] as const,
    cache: ['system', 'cache'] as const,
  },
  security: {
    roles: ['security', 'roles'] as const,
    apiKeys: ['security', 'apiKeys'] as const,
    sessions: ['security', 'sessions'] as const,
  },
  analytics: {
    reports: (params: Record<string, unknown>) => ['analytics', 'reports', params] as const,
    customReport: (id: string) => ['analytics', 'customReport', id] as const,
  },
} as const;

// ━━━━━━━━━━━━ البنية الخفيفة ━━━━━━━━━━━━
export interface QueryResult<T> {
  data: T | undefined;
  error: unknown;
  isLoading: boolean;
  isError: boolean;
  isSuccess: boolean;
  refetch: () => Promise<void>;
}

interface QueryOptions {
  /** معادل `enabled` في react-query — لا جلب إطلاقًا عند false. */
  enabled?: boolean;
  /** معادل `refetchInterval` — بالمللي ثانية، null يعني بلا استطلاع. */
  intervalMs?: number | null;
  /** معادل `placeholderData: previous => previous` — إبقاء القديم أثناء التحديث. */
  keepPrevious?: boolean;
}

export function useApiQuery<T>(
  fetcher: () => Promise<T>,
  deps: unknown[],
  options: QueryOptions = {}
): QueryResult<T> {
  const { enabled = true, intervalMs = null, keepPrevious = true } = options;
  const [data, setData] = useState<T | undefined>(undefined);
  const [error, setError] = useState<unknown>(null);
  const [isLoading, setIsLoading] = useState<boolean>(enabled);
  const mounted = useRef(true);
  const requestId = useRef(0);
  const fetcherRef = useRef(fetcher);
  const key = JSON.stringify(deps ?? []);

  useEffect(() => {
    fetcherRef.current = fetcher;
  });

  const refetch = useCallback(async () => {
    if (!enabled) return;
    const id = ++requestId.current;
    setIsLoading(true);
    setError(null);
    try {
      const result = await fetcherRef.current();
      if (mounted.current && requestId.current === id) setData(result);
    } catch (e) {
      if (mounted.current && requestId.current === id) {
        if (!keepPrevious) setData(undefined);
        setError(e);
      }
    } finally {
      if (mounted.current && requestId.current === id) setIsLoading(false);
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [enabled, key, keepPrevious]);

  useEffect(() => {
    mounted.current = true;
    if (enabled) void refetch();
    else setIsLoading(false);
    return () => {
      mounted.current = false;
    };
  }, [refetch, enabled]);

  // استطلاع مُدار (يتوقف عند إخفاء التبويب/انقطاع الشبكة) بدل refetchInterval
  usePolling(
    () => {
      void refetch();
    },
    enabled ? intervalMs : null,
    { immediate: false }
  );

  return {
    data,
    error,
    isLoading,
    isError: error !== null,
    isSuccess: error === null && data !== undefined,
    refetch,
  };
}

export interface MutationResult<TArgs, TRes> {
  mutate: (args: TArgs) => void;
  mutateAsync: (args: TArgs) => Promise<TRes>;
  isPending: boolean;
  isLoading: boolean;
  isError: boolean;
  error: unknown;
  reset: () => void;
}

export function useApiMutation<TArgs = void, TRes = unknown>(
  fn: (args: TArgs) => Promise<TRes>
): MutationResult<TArgs, TRes> {
  const [isPending, setIsPending] = useState(false);
  const [error, setError] = useState<unknown>(null);
  const fnRef = useRef(fn);

  useEffect(() => {
    fnRef.current = fn;
  });

  const mutateAsync = useCallback(async (args: TArgs): Promise<TRes> => {
    setIsPending(true);
    setError(null);
    try {
      return await fnRef.current(args);
    } catch (e) {
      setError(e);
      throw e;
    } finally {
      setIsPending(false);
    }
  }, []);

  const mutate = useCallback(
    (args: TArgs) => {
      // بلا كاش مركزي يُبطل — الصفحات تُحدّث عبر refetch/الاستطلاع الدوري
      void mutateAsync(args).catch(() => {});
    },
    [mutateAsync]
  );

  const reset = useCallback(() => {
    setError(null);
    setIsPending(false);
  }, []);

  return { mutate, mutateAsync, isPending, isLoading: isPending, isError: error !== null, error, reset };
}

// ━━━━━━━━━━━━ الاستعلامات — مسارات 2026-09-27 الموصولة بالعقود الحقيقية ━━━━━━━━━━━━
// تصحيح جذر ثانٍ: كل مسار أدناه تحقّقنا منه مقابل controller الباك اند الحي (grep Mapping)
// — لا مسار يُكتب بالتمني. ما لا عقد له يُعلَّم NO_CONTRACT بلا نداء شبكي.

/** `AdminV2Controller.getDashboardMetrics` (خريطة مسطّحة، revenue:null دائماً) → `DashboardMetrics` (واجهة متداخلة). */
function normalizeDashboardMetrics(raw: Record<string, any>): DashboardMetrics {
  const num = (v: unknown): number => (typeof v === 'number' && Number.isFinite(v) ? v : 0);
  const total = num(raw?.totalUsers);
  const pending = num(raw?.pendingApprovals);
  const banned = num(raw?.bannedUsers);
  return {
    users: {
      total,
      // عقد الباك اند لا يفصل approved — الاشتقاق الحسابي الوحيد الصادق عن مجمله.
      approved: Math.max(total - pending - banned, 0),
      pending,
      banned,
      new24h: 0, // ليس في عقد metrics (موجود في /admin/dashboard/charts فقط)
      approvalRate: 0,
    },
    messages: { total: num(raw?.messages), last24h: num(raw?.messages), perSecond: 0 },
    calls: { total: num(raw?.calls), active: 0, avgDuration: 0 },
    channels: { total: num(raw?.channels), active: num(raw?.channels) },
    revenue: { total: 0, last30d: 0, currency: 'YER' }, // الباك اند: revenue=null (لا فوترة في RED)
    health: { cpu: 0, memory: 0, disk: 0, network: 0, dbConnections: 0, queueDepth: 0 }, // ليست في عقد metrics — الصحة من useSystemHealth
  };
}

/** `AdminV2Controller.getSystemHealth` → `SystemHealth[]` (كيان JPA) → واجهة `SystemHealthComponent` (id إلزامي). */
function normalizeSystemHealth(raw: unknown): SystemHealthComponent[] {
  return toArray<Record<string, any>>(raw).map((row) => ({
    id: String(row?.id ?? row?.component ?? ''),
    component: String(row?.component ?? ''),
    status: (row?.status === 'DEGRADED' || row?.status === 'DOWN'
      ? row.status
      : 'HEALTHY') as SystemHealthComponent['status'],
    cpuUsage: typeof row?.cpuUsage === 'number' ? row.cpuUsage : undefined,
    memoryUsage: typeof row?.memoryUsage === 'number' ? row.memoryUsage : undefined,
    diskUsage: typeof row?.diskUsage === 'number' ? row.diskUsage : undefined,
    details: row?.details != null ? String(row.details) : undefined,
    lastCheckAt: String(row?.lastCheckAt ?? ''),
  }));
}

/** `AdminV2Controller.getRecentAlerts` يعيد `{alerts:[...]}` — وليس مصفوفة خام كما كان النداء يتوقع. */
function normalizeAlerts(raw: unknown): Alert[] {
  const rows = (raw as { alerts?: unknown } | null)?.alerts;
  return toArray<Record<string, any>>(rows).map((row) => ({
    id: String(row?.id ?? ''),
    severity: (row?.severity === 'critical' ? 'P0' : 'P2') as Alert['severity'],
    title: String(row?.title ?? ''),
    message: String(row?.message ?? ''),
    component: '',
    acknowledged: false, // عقد الباك اند لا يفصل الإقرار بعد
    createdAt: String(row?.timestamp ?? ''),
  }));
}

/** طفرة/استعلام بلا عقد باك اند: تُرفض صراحة بدل نداء مسار وهمي يعيد 404. */
const noContract =
  (what: string) =>
  () =>
    Promise.reject(new Error(`NO_CONTRACT: ${what} — لا مقابل في الباك اند (تحقق 2026-09-27)`));

export function useDashboardMetrics() {
  // العقد الحقيقي: GET /api/admin/dashboard/metrics (AdminV2Controller)
  return useApiQuery<DashboardMetrics>(
    () => api.get<Record<string, any>>('/admin/dashboard/metrics').then(normalizeDashboardMetrics),
    [],
    { intervalMs: 30000 }
  );
}

export function useSystemHealth() {
  // العقد الحقيقي: GET /api/admin/system/health → SystemHealth[] (نفس جسم /admin/health)
  return useApiQuery<SystemHealthComponent[]>(
    () => api.get<unknown>('/admin/system/health').then(normalizeSystemHealth),
    [],
    { intervalMs: 60000 }
  );
}

export function useAlerts(params?: { severity?: string; acknowledged?: boolean }) {
  // العقد الحقيقي: GET /api/admin/alerts → {alerts:[...]} — لا فلترات في الباك اند بعد؛
  // params تُمرر استباقياً لتُقبل حين يضيفها العقد.
  return useApiQuery<Alert[]>(
    () => api.get<unknown>('/admin/alerts', params ?? {}).then(normalizeAlerts),
    [params],
    { intervalMs: 15000 }
  );
}

export function useRealtimeMetrics() {
  // العقد الحقيقي: GET /api/admin/metrics/realtime → {users:<رقم>, health:Record, timestamp}
  return useApiQuery<RealtimeMetrics>(
    () =>
      api
        .get<Record<string, any>>('/admin/metrics/realtime')
        .then((raw) => ({ ...raw, users: typeof raw?.users === 'number' ? raw.users : 0 }) as RealtimeMetrics),
    [],
    { intervalMs: 5000 }
  );
}

export function useUsers(
  params: {
    page?: number;
    size?: number;
    status?: string;
    role?: string;
    search?: string;
    sortBy?: string;
    sortDir?: 'asc' | 'desc';
  } = {}
) {
  return useApiQuery<PageResponse<User>>(() => api.get<PageResponse<User>>('/admin/users', params), [params]);
}

export function useUserDetail(userId: string) {
  return useApiQuery<User>(() => api.get<User>(`/admin/users/${userId}`), [userId], {
    enabled: !!userId,
  });
}

/**
 * ⚠️ 2026-09-27: بلا عقد — `/api/admin/users/{id}/sessions|devices|activity|security-events`
 * غير موجود في الباك اند (`AdminV2Controller` يعرّف /users وأفعالها الإدارية فقط، وتفريغ
 * الأجهزة يجري داخلياً عند حذف المستخدم). الخطافات تُصدَّر بلا نداء شبكي (`enabled:false`)
 * حتى يولد العقد — أي مستدعٍ مستقبلي يرى خطأً صريحاً لا 404 صامتاً.
 */
export function useUserSessions(userId: string) {
  return useApiQuery<Session[]>(noContract('GET /api/admin/users/{id}/sessions'), [userId], {
    enabled: false,
  });
}

export function useUserDevices(userId: string) {
  return useApiQuery<Device[]>(noContract('GET /api/admin/users/{id}/devices'), [userId], {
    enabled: false,
  });
}

export function useUserActivity(userId: string) {
  return useApiQuery<ActivityLogEntry[]>(noContract('GET /api/admin/users/{id}/activity'), [userId], {
    enabled: false,
  });
}

export function useUserSecurityEvents(userId: string) {
  return useApiQuery<SecurityEvent[]>(noContract('GET /api/admin/users/{id}/security-events'), [userId], {
    enabled: false,
  });
}

/** `AdminReportResponse` (باك اند: ModerationController) → `ReportedContent` (واجهة) — ما لا يعيده العقد قيم محايدة صادقة لا اختلاق. */
const KNOWN_REPORT_CATEGORIES: ReadonlyArray<ReportedContent['category']> = [
  'SPAM', 'HARASSMENT', 'VIOLENCE', 'ILLEGAL', 'CSAM', 'COPYRIGHT', 'MISINFORMATION', 'OTHER',
];

function normalizeAdminReport(row: Record<string, any>): ReportedContent {
  const statusMap: Record<string, ReportedContent['status']> = {
    OPEN: 'PENDING',
    REVIEWING: 'REVIEWING',
    RESOLVED: 'RESOLVED',
    DISMISSED: 'DISMISSED',
  };
  const cat = String(row?.category ?? '').toUpperCase() as ReportedContent['category'];
  return {
    id: String(row?.id ?? ''),
    type: 'MESSAGE', // العقد لا يفصل نوع المحتوى المُبلَّغ عنه
    contentId: String(row?.id ?? ''),
    reporterId: String(row?.reporterRedId ?? ''),
    reason: String(row?.details ?? ''),
    category: KNOWN_REPORT_CATEGORIES.includes(cat) ? cat : 'OTHER',
    status: statusMap[String(row?.status ?? 'OPEN')] ?? 'PENDING',
    priority: 'MEDIUM',
    createdAt: String(row?.createdAt ?? ''),
  };
}

export function useModerationQueue(
  params: {
    page?: number;
    size?: number;
    status?: string;
    category?: string;
    priority?: string;
  } = {}
) {
  // العقد الحقيقي: GET /api/admin/moderation/reports?status= → AdminReportResponse[]
  // مصفوفة خام بلا ترقيم صفحات في الباك اند — الترقيم هنا تطبيق محلي صادق على القائمة كاملة.
  const size = params.size ?? 50;
  return useApiQuery<PageResponse<ReportedContent>>(
    () =>
      api
        .get<unknown>('/admin/moderation/reports', { status: params.status })
        .then((raw) => {
          const all = toArray<Record<string, any>>(raw).map(normalizeAdminReport);
          const start = (params.page ?? 0) * size;
          return {
            content: all.slice(start, start + size),
            page: params.page ?? 0,
            size,
            totalElements: all.length,
            totalPages: Math.max(Math.ceil(all.length / size), 1),
          };
        }),
    [params],
    { intervalMs: 10000 }
  );
}

export function useReportedContent(contentId: string) {
  // لا GET لتقرير واحد في الباك اند — يُستخرج من قائمة التقارير المفتوحة.
  return useApiQuery<ReportedContent>(
    () =>
      api
        .get<unknown>('/admin/moderation/reports', { status: 'OPEN' })
        .then((raw) =>
          toArray<Record<string, any>>(raw).map(normalizeAdminReport).find((r) => r.id === contentId)
        )
        .then((found) => {
          if (!found) throw new Error(`REPORT_NOT_FOUND: ${contentId}`);
          return found;
        }),
    [contentId],
    { enabled: !!contentId }
  );
}

/** ⚠️ 2026-09-27: بلا عقد — لا /api/admin/content/channels في أي controller. */
export function useChannels(
  params: {
    page?: number;
    size?: number;
    type?: string;
    search?: string;
  } = {}
) {
  return useApiQuery<PageResponse<Channel>>(noContract('GET /api/admin/content/channels'), [params], {
    enabled: false,
  });
}

export function useChannelDetail(channelId: string) {
  return useApiQuery<Channel>(noContract('GET /api/admin/content/channels/{id}'), [channelId], {
    enabled: !!channelId,
  });
}

/** `FeatureFlag` (كيان JPA: flagName/enabled/rolloutPercentage) → `FeatureFlag` (واجهة: key/name/targetRules). */
function normalizeFeatureFlag(row: Record<string, any>): FeatureFlag {
  return {
    key: String(row?.flagName ?? ''),
    name: String(row?.flagName ?? ''),
    description: row?.description != null ? String(row.description) : undefined,
    enabled: Boolean(row?.enabled),
    rolloutPercentage: Number(row?.rolloutPercentage ?? 0),
    targetRules: [], // العقد (كيان JPA) يستهدف users/groups بمصفوفات UUID — لا قواعد مشغّلات
    createdAt: String(row?.createdAt ?? ''),
    updatedAt: String(row?.updatedAt ?? ''),
    createdBy: String(row?.createdBy ?? ''),
  };
}

export function useFeatureFlags() {
  // العقد الحقيقي: GET /api/admin/feature-flags (AdminV2Controller)
  return useApiQuery<FeatureFlag[]>(
    () => api.get<unknown>('/admin/feature-flags').then((raw) => toArray<Record<string, any>>(raw).map(normalizeFeatureFlag)),
    []
  );
}

/**
 * ✅ 2026-09-27 — توصيل كامل بالعقود (ثالث مراجعة، تحقق بـ grep مقابل controllers الباك اند):
 * - ما له عقد حي وُصِل الآن: config، roles، api-keys، mfa، sso (AdminOpsStoresController +
 *   AdminSecurityPolicyController — العقود وُجدت بعد جولة 2026-09-26 التي ألّفت تعليقها
 *   القديم «لا عقد لهما» فيصبح مضللاً)، وdashboard/health/alerts/realtime/users/flags.
 * - ما لا عقد له أُعلّم NO_CONTRACT صراحة (noContract أعلاه) بلا نداء شبكي.
 * - التطبيع إلزامي حيث يخالف شكل الباك اند الواجهة: backups/sessions (جولة 09-26)،
 *   وdashboard/health/alerts/flags/moderation (هذه الجولة).
 */

/** يستخرج مصفوفة من ردّ قد يكون مصفوفة خام أو صفحة `{content}`. */
function toArray<T>(raw: unknown): T[] {
  if (Array.isArray(raw)) return raw as T[];
  const content = (raw as { content?: unknown } | null)?.content;
  return Array.isArray(content) ? (content as T[]) : [];
}

/** `BackupHistory` (باك اند) → `Backup` (واجهة): الحجم بالبايتات والاسم من مسار التخزين. */
function normalizeBackup(row: Record<string, any>): Backup {
  const location = String(row?.storageLocation ?? '');
  const fileName = location.split(/[\\/]/).filter(Boolean).pop();
  return {
    id: String(row?.id ?? ''),
    name: fileName || `backup-${String(row?.id ?? '').slice(0, 8)}`,
    type: (row?.backupType ?? 'FULL') as Backup['type'],
    status: (row?.status ?? 'PENDING') as Backup['status'],
    size: Number(row?.sizeBytes ?? 0),
    startedAt: String(row?.startedAt ?? ''),
    completedAt: row?.completedAt ? String(row.completedAt) : undefined,
    notes: row?.notes ? String(row.notes) : undefined,
    // حقول السجل الحقيقي التي كانت تُهمَل فتظهر «—» في صفحة النسخ
    storageLocation: row?.storageLocation ? String(row.storageLocation) : undefined,
    checksum: row?.checksum ? String(row.checksum) : undefined,
    restoreCount: Number(row?.restoreCount ?? 0),
    verifiedAt: row?.verifiedAt ? String(row.verifiedAt) : undefined,
    triggeredBy: row?.triggeredBy ? String(row.triggeredBy) : undefined,
  };
}

/** `AdminSession` (باك اند) → `Session` (واجهة): لا اسم مستخدم/جهاز في العقد، فنشتقّهما بأمان. */
function normalizeSession(row: Record<string, any>): Session & { platform?: string } {
  const adminId = String(row?.adminId ?? row?.id ?? '');
  const userAgent = String(row?.userAgent ?? '');
  const deviceInfo = String(row?.deviceInfo ?? '');
  const platform = /android|iphone|ipad|mobile/i.test(`${userAgent} ${deviceInfo}`) ? 'Mobile' : 'Web';
  return {
    id: String(row?.id ?? ''),
    userId: adminId,
    deviceId: deviceInfo || String(row?.id ?? ''),
    deviceName: deviceInfo || `${platform} session`,
    ipAddress: String(row?.ipAddress ?? '—'),
    location: row?.location ? String(row.location) : undefined,
    userAgent,
    createdAt: String(row?.startedAt ?? ''),
    lastActivityAt: String(row?.lastActiveAt ?? row?.startedAt ?? ''),
    isCurrent: false,
    isTrusted: Boolean(row?.isActive),
    riskScore: 0,
    platform,
  };
}

/** ✅ العقد الحقيقي — GET /api/admin/system/config (AdminOpsStoresController، JPA: admin_system_config). */
export function useConfig(params: { page?: number; size?: number; search?: string } = {}) {
  return useApiQuery<PageResponse<ConfigEntry>>(
    () => api.get<PageResponse<ConfigEntry>>('/admin/system/config', params),
    [params]
  );
}

export function useAuditLog(
  params: {
    page?: number;
    size?: number;
    adminId?: string;
    action?: string;
    category?: string;
    severity?: string;
    startDate?: string;
    endDate?: string;
    search?: string;
  } = {}
) {
  return useApiQuery<PageResponse<AuditLogEntry>>(
    // العقد الحقيقي: GET /api/admin/audit?page&size&category&adminId&severity
    // (الباك اند يتجاهل حاليًا startDate/endDate/action — الفلترة الزمنية تنتظر دعمه)
    () => api.get<PageResponse<AuditLogEntry>>('/admin/audit', params),
    [params]
  );
}

export function useBackups(params: { page?: number; size?: number } = {}) {
  // GET /api/admin/backups يعيد صفحة من BackupHistory → تطبيع إلزامي.
  return useApiQuery<Backup[]>(
    () =>
      api
        .get<unknown>('/admin/backups', params)
        .then((raw) => toArray<Record<string, any>>(raw).map(normalizeBackup)),
    [params]
  );
}

/** ✅ العقد الحقيقي — GET /api/admin/security/roles (AdminOpsStoresController، JPA: admin_roles، 4 أدوار نظام مزروعة). */
export function useRoles() {
  return useApiQuery<Role[]>(() => api.get<Role[]>('/admin/security/roles'), []);
}

/** ✅ العقد الحقيقي — GET /api/admin/security/api-keys (AdminOpsStoresController، JPA: admin_api_keys، تخزين SHA-256). */
export function useApiKeys() {
  return useApiQuery<ApiKey[]>(() => api.get<ApiKey[]>('/admin/security/api-keys'), []);
}

/** ✅ الجلسة 12: سياسة MFA — عقد حقيقي فوق مخزن admin_system_config (مفاتيح mfa.*). */
export interface MfaPolicy {
  enforceForAdmins: boolean;
  enforceForAll: boolean;
  allowedMethods: string[];
  totpWindow: number;
}

export function useMfaPolicy() {
  return useApiQuery<MfaPolicy>(() => api.get<MfaPolicy>('/admin/mfa/policy'), []);
}

/** ✅ الجلسة 12: إعداد SSO OIDC — clientSecret يُكتب فقط ولا يُعاد في القراءة أبدًا. */
export interface SsoOidcConfig {
  enabled: boolean;
  issuer?: string;
  clientId?: string;
  scopes: string[];
  redirectUri?: string;
  logoutUri?: string;
  lastSync?: string;
}

export interface SsoConfigResponse {
  oidc: SsoOidcConfig;
  saml: { enabled: boolean };
}

export function useSsoConfig() {
  return useApiQuery<SsoConfigResponse>(() => api.get<SsoConfigResponse>('/admin/security/sso'), []);
}

export interface SsoTestResult {
  success: boolean;
  reachable: boolean;
  error?: string;
  detail?: string;
  discoveryUrl?: string;
  issuer?: string;
  authorizationEndpoint?: string;
  tokenEndpoint?: string;
  scopesSupported?: string[];
}

export function useAllSessions(params: { adminId?: string } = {}) {
  // العقد الحقيقي: GET /api/admin/sessions → AdminSession[] (مصفوفة خام) → تطبيع.
  return useApiQuery<Session[]>(
    () =>
      api
        .get<unknown>('/admin/sessions', params)
        .then((raw) => toArray<Record<string, any>>(raw).map(normalizeSession)),
    [params],
    { intervalMs: 30000 }
  );
}

/** صفوف كاش Redis كما يعيدها `/api/admin/system/cache` (مصدر حقيقي: INFO + SCAN مقيّد). */
export interface CacheKeyRow {
  key: string;
  type: string;
  ttl: number;
  size: number | null;
  memory: number | null;
}

export interface CacheOverview {
  keys: CacheKeyRow[];
  truncated: boolean;
  pattern: string;
  memory: {
    used: number;
    peak: number;
    limit: number;
    fragmentation: number;
    keys: number;
    expired: number;
    evicted: number;
  };
}

/** GET /api/admin/system/cache — لا `KEYS` الحاجب ولا أرقام مُختلقة: `null` صريح لما لا يُقاس. */
export function useCacheOverview(params: { pattern?: string; count?: number } = {}) {
  return useApiQuery<CacheOverview>(
    () => api.get<CacheOverview>('/admin/system/cache', params),
    [params],
    { intervalMs: 30000 }
  );
}

export interface MigrationRow {
  id: string;
  version: string;
  description: string;
  type: string;
  status: 'SUCCESS' | 'FAILED';
  checksum: string;
  executedBy: string;
  executedAt: string;
  executionTime: number;
  script?: string;
}

export interface MigrationsOverview {
  available: boolean;
  migrations: MigrationRow[];
  total: number;
  failed: number;
  currentVersion?: string;
  reason?: string;
}

/** GET /api/admin/system/migrations — سجل Flyway الحقيقي (`flyway_schema_history`). */
export function useMigrations() {
  return useApiQuery<MigrationsOverview>(
    () => api.get<MigrationsOverview>('/admin/system/migrations'),
    [],
    { intervalMs: 60000 }
  );
}

/**
 * ⚠️ 2026-09-27: بلا عقد — لا /api/admin/notifications في أي controller.
 * بلا نداء شبكي (`enabled:false`) حتى يولد المسار في الباك اند.
 */
export function useNotifications(params?: { unreadOnly?: boolean }) {
  const p = params ?? {};
  return useApiQuery<Notification[]>(noContract('GET /api/admin/notifications'), [p], {
    enabled: false,
    intervalMs: 30000,
  });
}

// ━━━━━━━━━━━━ الطفرات (الأسماء والتواقيع كما كانت) ━━━━━━━━━━━━
export const mutations = {
  useApproveUser: () =>
    useApiMutation((userId: string) => api.post(`/admin/users/${userId}/approve`)),

  useBanUser: () =>
    useApiMutation(
      ({ userId, reason, durationDays }: { userId: string; reason: string; durationDays?: number }) =>
        api.post(`/admin/users/${userId}/ban`, { reason, durationDays })
    ),

  useUnbanUser: () => useApiMutation((userId: string) => api.post(`/admin/users/${userId}/unban`)),

  useBulkAction: () =>
    useApiMutation(({ action, userIds }: { action: string; userIds: string[] }) =>
      api.post('/admin/users/bulk', { action, userIds })
    ),

  useModerateContent: () =>
    useApiMutation(
      ({ contentId, action }: { contentId: string; action: 'REVIEWING' | 'RESOLVED' | 'DISMISSED' }) =>
        // العقد الحقيقي: PATCH /api/admin/moderation/reports/{id}?status= (ModerationController)
        // — الوضع القديم POST /content/reported/{id}/moderate بلا مقابل في الباك اند.
        api.patch(`/admin/moderation/reports/${encodeURIComponent(contentId)}?status=${encodeURIComponent(action)}`)
    ),

  useUpdateFeatureFlag: () =>
    useApiMutation(({ key, data }: { key: string; data: Partial<FeatureFlag> }) =>
      // العقد الحقيقي: PUT /api/admin/feature-flags/{name} — التحديث فقط.
      api.put(`/admin/feature-flags/${encodeURIComponent(key)}`, data)
    ),

  /** ⚠️ بلا عقد: لا POST /api/admin/feature-flags (الإنشاء عبر زرع/ترحيل فقط). */
  useCreateFeatureFlag: () => useApiMutation(noContract('POST /api/admin/feature-flags')),

  /** ⚠️ بلا عقد: لا DELETE /api/admin/feature-flags/{name}. */
  useDeleteFeatureFlag: () =>
    useApiMutation((key: string) =>
      Promise.reject(new Error(`NO_CONTRACT: DELETE /api/admin/feature-flags/${key}`))
    ),

  useUpdateConfig: () =>
    useApiMutation(
      ({ key, value, description }: { key: string; value: string; description?: string }) =>
        api.put(`/admin/system/config/${encodeURIComponent(key)}`, { value, description })
    ),

  /** ✅ العقد الحقيقي: POST/DELETE /api/admin/system/config (AdminOpsStoresController). */
  useCreateConfig: () =>
    useApiMutation((data: { key: string; value: string; type?: ConfigEntry['type']; description?: string }) =>
      api.post('/admin/system/config', data)
    ),

  useDeleteConfig: () =>
    useApiMutation((key: string) => api.delete(`/admin/system/config/${encodeURIComponent(key)}`)),

  useCreateBackup: () =>
    useApiMutation((data: { type: Backup['type']; notes?: string }) =>
      api.post('/admin/backups', data)
    ),

  useRestoreBackup: () =>
    useApiMutation(({ backupId, confirmCode }: { backupId: string; confirmCode: string }) =>
      api.post(`/admin/backups/${backupId}/restore`, { confirmCode })
    ),

  useDeleteBackup: () =>
    useApiMutation((backupId: string) => api.delete(`/admin/backups/${backupId}`)),

  useCreateRole: () =>
    useApiMutation((data: { name: string; description?: string; permissions?: string[] }) =>
      api.post('/admin/security/roles', data)
    ),

  useUpdateRole: () =>
    useApiMutation(({ id, data }: { id: string; data: { description?: string; permissions?: string[] } }) =>
      api.put(`/admin/security/roles/${id}`, data)
    ),

  useDeleteRole: () => useApiMutation((id: string) => api.delete(`/admin/security/roles/${id}`)),

  /** ✅ V68: يُعاد المفتاح الخام مرة واحدة فقط في استجابة الإنشاء (حقل secret). */
  useCreateApiKey: () =>
    useApiMutation((data: { name: string; scopes: string[]; expiresAt?: string }) =>
      api.post<{ secret?: string }>('/admin/security/api-keys', data)
    ),

  useRevokeApiKey: () =>
    useApiMutation((id: string) => api.delete(`/admin/security/api-keys/${id}`)),

  useRevokeSession: () =>
    useApiMutation(({ sessionId, reason }: { sessionId: string; reason: string }) =>
      // الباك اند: POST /api/admin/sessions/{id}/terminate بجسم {reason}
      api.post(`/admin/sessions/${sessionId}/terminate`, { reason })
    ),

  /** ✅ تحديث سياسة MFA — PUT /api/admin/mfa/policy (تدقيق MFA_POLICY_UPDATED). */
  useUpdateMfaPolicy: () =>
    useApiMutation((data: MfaPolicy) => api.put<MfaPolicy>('/admin/mfa/policy', data)),

  /** ✅ حفظ إعداد SSO — PUT /api/admin/security/sso (تدقيق SSO_CONFIG_UPDATED). */
  useUpdateSsoConfig: () =>
    useApiMutation((data: Partial<SsoOidcConfig> & { clientSecret?: string }) =>
      api.put<SsoConfigResponse>('/admin/security/sso', data)
    ),

  /** ✅ اختبار اكتشاف OIDC حقيقي — PUT /api/admin/security/sso/test/{issuer}. */
  useTestSso: () =>
    useApiMutation(({ issuer }: { issuer: string }) =>
      api.put<SsoTestResult>(`/admin/security/sso/test/${encodeURIComponent(issuer)}`, { issuer })
    ),

  /** ✅ الجلسة 12: تسمية صادقة — POST /api/admin/sessions/cleanup ينظّف الجلسات المنتهية فقط. */
  useCleanupExpiredSessions: () =>
    useApiMutation(() => api.post('/admin/sessions/cleanup')),

  /** إبطال مقيّد بالنمط — لا مسح شامل: الباك اند يرفض `*` بـ 400 ويسجّل كل إبطال في التدقيق. */
  useInvalidateCache: () =>
    useApiMutation(({ pattern, limit }: { pattern: string; limit?: number }) =>
      api.post('/admin/system/cache/invalidate', { pattern, limit })
    ),

  /** ⚠️ بلا عقد: لا /api/admin/notifications في الباك اند (2026-09-27). */
  useMarkNotificationRead: () =>
    useApiMutation((id: string) =>
      Promise.reject(new Error(`NO_CONTRACT: PUT /api/admin/notifications/${id}/read`))
    ),

  useMarkAllNotificationsRead: () =>
    useApiMutation(noContract('PUT /api/admin/notifications/read-all')),

  /** ⚠️ بلا عقد: لا POST /api/admin/export في أي controller (2026-09-27). */
  useExportData: () => useApiMutation(noContract('POST /api/admin/export')),
};

// NOTE: حُذفت الواجهتان المحليتان `Device` و`SecurityEvent` من هذا الملف —
// تُستوردان الآن من `@/types` (المصدر الوحيد). بقيت نسخة `Device` في
// `src/pages/tabs/AuthorityTab.tsx:6` خارج النطاق — يجب حذفها هناك
// واستيراد `Device` من `@/types` بدلها (سطر واحد، لم يُمسّ التزامًا بالنطاق).

// للحفاظ على التوافق مع أي مستورد لنوع Permission من هنا سابقًا:
export type { Permission };
