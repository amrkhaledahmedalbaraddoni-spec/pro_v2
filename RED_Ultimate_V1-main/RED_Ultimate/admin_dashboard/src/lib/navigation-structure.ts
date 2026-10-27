/**
 * navigation-structure.ts
 * 🧭 مركز التحكم الموحد لـ Navigation
 *
 * تحديد مركزي لجميع الصفحات والقوائم والتنقل في لوحة الإدارة
 * - معايرة الأيقونات
 * - تسلسل ثابت للقوائم
 * - صفحات مفضلة وحديثة
 * - بحث وتصفية سريعة
 */

import type { LucideIcon } from 'lucide-react';
import {
  LayoutDashboard,
  Users,
  MessageSquare,
  FileText,
  Lock,
  Server,
  BarChart3,
  Settings,
  Bell,
  Database,
  Phone,
  Radio,
  Shield,
  Key,
  UserCheck,
  MonitorSmartphone,
  Clock,
  HardDrive,
  Package,
  Zap,
  ScrollText,
  AlertCircle,
  Search,
  Box,
  BookOpen,
  CheckCircle2,
  Code,
  Gauge,
  Megaphone,
  Eye,
} from 'lucide-react';

export type PageKey =
  // Dashboard & Analytics
  | 'dashboard'
  | 'data-overview'
  | 'monitor'
  | 'system-health'
  | 'diagnostics'
  | 'analytics'

  // User Management
  | 'users'
  | 'user-management'
  | 'authority'
  | 'groups'

  // Communications
  | 'messaging'
  | 'groups'
  | 'calls'
  | 'calls-monitor'
  | 'posts'

  // Content Management
  | 'content'
  | 'media'
  | 'posts'
  | 'announcements'

  // Security & Auth
  | 'security'
  | 'sec-sso'
  | 'sec-mfa'
  | 'sec-roles'
  | 'sec-sessions'
  | 'sec-apikeys'

  // Infrastructure & Monitoring
  | 'infrastructure'
  | 'system-health'
  | 'diagnostics'
  | 'logs'

  // Reports & Audit
  | 'reports'
  | 'audit'
  | 'sys-audit'
  | 'moderation'

  // Settings & Configuration
  | 'settings'
  | 'sys-config'
  | 'sys-cache'
  | 'sys-migrations'
  | 'backups'
  | 'sys-backups'
  | 'notifications'
  | 'featureflags'
  | 'approvals'
  | 'browser-settings';

export interface NavItem {
  key: PageKey;
  label: string;
  icon: LucideIcon;
  description?: string;
  shortcut?: string;
  badge?: string;
  isNew?: boolean;
  isBeta?: boolean;
}

export interface NavCategory {
  key: string;
  label: string;
  description: string;
  icon?: LucideIcon;
  items: NavItem[];
}

export const navigationStructure: NavCategory[] = [
  // 1️⃣ Dashboard & Analytics
  {
    key: 'dashboard-analytics',
    label: 'لوحة التحكم',
    description: 'نظرة عامة وتحليلات النظام',
    icon: LayoutDashboard,
    items: [
      {
        key: 'dashboard',
        label: 'الرئيسية',
        icon: LayoutDashboard,
        description: 'نظرة عامة سريعة على النظام',
        shortcut: 'Ctrl+1',
      },
      {
        key: 'data-overview',
        label: 'جرد بيانات المنصة',
        icon: Database,
        description: 'إحصائيات شاملة للبيانات',
      },
      {
        key: 'monitor',
        label: 'المراقبة الحية',
        icon: Gauge,
        description: 'مراقبة أداء النظام في الوقت الفعلي',
        shortcut: 'Ctrl+M',
      },
      {
        key: 'system-health',
        label: 'صحة النظام',
        icon: AlertCircle,
        description: 'حالة صحة البنية التحتية',
      },
      {
        key: 'diagnostics',
        label: 'التشخيص',
        icon: Search,
        description: 'أدوات تشخيص متقدمة',
      },
      {
        key: 'analytics',
        label: 'التحليلات المتقدمة',
        icon: BarChart3,
        description: 'تقارير وتحليلات معمقة',
        isNew: true,
      },
    ],
  },

  // 2️⃣ User Management
  {
    key: 'user-management',
    label: 'إدارة المستخدمين',
    description: 'المستخدمون والأدوار والأذونات',
    icon: Users,
    items: [
      {
        key: 'users',
        label: 'المستخدمون',
        icon: Users,
        description: 'إدارة حسابات المستخدمين',
        shortcut: 'Ctrl+U',
      },
      {
        key: 'authority',
        label: 'سلطة الاعتماد',
        icon: CheckCircle2,
        description: 'إدارة هويات المستخدمين',
      },
      {
        key: 'groups',
        label: 'المجموعات',
        icon: Users,
        description: 'إدارة مجموعات المستخدمين',
        shortcut: 'Ctrl+G',
      },
    ],
  },

  // 3️⃣ Communications
  {
    key: 'communications',
    label: 'الاتصالات',
    description: 'الرسائل والمكالمات والبث',
    icon: MessageSquare,
    items: [
      {
        key: 'messaging',
        label: 'مركز الرسائل',
        icon: MessageSquare,
        description: 'إدارة الرسائل والمراسلات',
      },
      {
        key: 'calls',
        label: 'سجل المكالمات',
        icon: Phone,
        description: 'سجل المكالمات الكامل',
      },
      {
        key: 'calls-monitor',
        label: 'مراقبة المكالمات',
        icon: Phone,
        description: 'مراقبة المكالمات المباشرة',
      },
      {
        key: 'posts',
        label: 'المنشورات والتغريدات',
        icon: FileText,
        description: 'إدارة المنشورات والمحتوى',
      },
      {
        key: 'announcements',
        label: 'الإعلانات',
        icon: Megaphone,
        description: 'نشر الإعلانات والتنبيهات',
      },
    ],
  },

  // 4️⃣ Content Management
  {
    key: 'content-management',
    label: 'إدارة المحتوى',
    description: 'الوسائط والمحتوى والموارد',
    icon: FileText,
    items: [
      {
        key: 'content',
        label: 'المحتوى',
        icon: FileText,
        description: 'إدارة محتوى المنصة',
      },
      {
        key: 'media',
        label: 'مركز الوسائط',
        icon: Box,
        description: 'مكتبة الصور والفيديوهات',
      },
      {
        key: 'moderation',
        label: 'الإشراف السريع',
        icon: Eye,
        description: 'مراجعة واعتماد المحتوى',
      },
      {
        key: 'reports',
        label: 'مراقبة المحتوى',
        icon: AlertCircle,
        description: 'تقارير المحتوى والبلاغات',
      },
    ],
  },

  // 5️⃣ Security & Auth
  {
    key: 'security-auth',
    label: 'الأمان والمصادقة',
    description: 'الأمان والتشفير والمصادقة',
    icon: Lock,
    items: [
      {
        key: 'security',
        label: 'مركز الأمان',
        icon: Shield,
        description: 'مركز إدارة الأمان',
      },
      {
        key: 'sec-sso',
        label: 'الدخول الموحد SSO',
        icon: Key,
        description: 'إعدادات الدخول الموحد',
      },
      {
        key: 'sec-mfa',
        label: 'المصادقة MFA',
        icon: CheckCircle2,
        description: 'المصادقة متعددة العوامل',
      },
      {
        key: 'sec-roles',
        label: 'الأدوار والأذونات',
        icon: UserCheck,
        description: 'إدارة الأدوار والصلاحيات',
      },
      {
        key: 'sec-sessions',
        label: 'الجلسات',
        icon: Clock,
        description: 'إدارة جلسات المستخدمين',
      },
      {
        key: 'sec-apikeys',
        label: 'مفاتيح API',
        icon: Code,
        description: 'إدارة مفاتيح الـ API',
      },
    ],
  },

  // 6️⃣ Infrastructure & Monitoring
  {
    key: 'infrastructure',
    label: 'البنية التحتية',
    description: 'الخوادم والقواعد والشبكات',
    icon: Server,
    items: [
      {
        key: 'infrastructure',
        label: 'البنية التحتية',
        icon: Server,
        description: 'إدارة الخوادم والموارد',
      },
      {
        key: 'logs',
        label: 'سجل النظام الحي',
        icon: ScrollText,
        description: 'سجلات النظام والأخطاء',
      },
      {
        key: 'sys-config',
        label: 'إعدادات النظام',
        icon: Settings,
        description: 'إعدادات النظام المتقدمة',
      },
      {
        key: 'sys-cache',
        label: 'الكاش',
        icon: Package,
        description: 'إدارة الكاش والأداء',
      },
      {
        key: 'sys-migrations',
        label: 'الترحيلات',
        icon: Zap,
        description: 'ترحيلات قاعدة البيانات',
      },
    ],
  },

  // 7️⃣ Reports & Audit
  {
    key: 'reports-audit',
    label: 'التقارير والتدقيق',
    description: 'السجلات والتقارير والتدقيق',
    icon: BarChart3,
    items: [
      {
        key: 'audit',
        label: 'سجل التدقيق',
        icon: BookOpen,
        description: 'سجل تدقيق العمليات',
      },
      {
        key: 'sys-audit',
        label: 'التدقيق المفصّل',
        icon: ScrollText,
        description: 'تقارير تدقيق مفصلة',
      },
      {
        key: 'approvals',
        label: 'الموافقات المعلقة',
        icon: CheckCircle2,
        description: 'الموافقات المعلقة والمراجعات',
        badge: 'pending',
      },
    ],
  },

  // 8️⃣ Settings & Configuration
  {
    key: 'settings-config',
    label: 'الإعدادات',
    description: 'إعدادات النظام والمتصفح',
    icon: Settings,
    items: [
      {
        key: 'settings',
        label: 'الإعدادات',
        icon: Settings,
        description: 'إعدادات عامة النظام',
      },
      {
        key: 'notifications',
        label: 'الإشعارات',
        icon: Bell,
        description: 'إعدادات الإشعارات',
      },
      {
        key: 'featureflags',
        label: 'أعلام الميزات',
        icon: Zap,
        description: 'تفعيل/تعطيل الميزات',
      },
      {
        key: 'backups',
        label: 'النسخ الاحتياطية',
        icon: HardDrive,
        description: 'إدارة النسخ الاحتياطية',
      },
      {
        key: 'sys-backups',
        label: 'النسخ المتقدم',
        icon: HardDrive,
        description: 'نسخ احتياطي متقدم',
      },
      {
        key: 'browser-settings',
        label: 'إعدادات المتصفح',
        icon: Settings,
        description: 'إعدادات واجهة المستخدم',
      },
    ],
  },
];

/**
 * دالة مساعدة للبحث عن صفحة
 */
export function findNavItem(key: PageKey): NavItem | undefined {
  for (const category of navigationStructure) {
    const item = category.items.find(i => i.key === key);
    if (item) return item;
  }
  return undefined;
}

/**
 * دالة مساعدة للبحث عن فئة صفحة
 */
export function findNavCategory(key: PageKey): NavCategory | undefined {
  return navigationStructure.find(cat =>
    cat.items.some(item => item.key === key)
  );
}

/**
 * دالة للحصول على جميع الصفحات كقائمة مسطحة
 */
export function getAllNavItems(): NavItem[] {
  return navigationStructure.flatMap(cat => cat.items);
}

/**
 * دالة للبحث عن صفحات حسب كلمة مفتاحية
 */
export function searchNavItems(query: string): NavItem[] {
  const lowerQuery = query.toLowerCase();
  return getAllNavItems().filter(item =>
    item.label.toLowerCase().includes(lowerQuery) ||
    item.description?.toLowerCase().includes(lowerQuery)
  );
}

/**
 * صفحات مفضلة افتراضية
 */
export const defaultFavorites: PageKey[] = [
  'dashboard',
  'monitor',
  'users',
  'security',
  'reports',
];

/**
 * صفحات حديثة (يتم تحديثها حسب استخدام المسؤول)
 */
export const recentPagesLimit = 5;
