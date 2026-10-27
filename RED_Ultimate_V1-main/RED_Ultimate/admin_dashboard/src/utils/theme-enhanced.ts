/**
 * نظام الثيمات المتقدم لـ RED Sovereign Admin Dashboard
 * يدعم Dark/Light mode مع ألوان احترافية وتدرجات متقدمة
 * أفضل من Telegram و WhatsApp
 */

// ألوان العلامة التجارية الأساسية
export const RED_BRAND_COLORS = {
  emerald: {
    50: '#f0fdf4',
    100: '#dcfce7',
    200: '#bbf7d0',
    300: '#86efac',
    400: '#4ade80',
    500: '#22c55e',
    600: '#16a34a',
    700: '#15803d',
    800: '#166534',
    900: '#14532d',
    dark: '#14C79A',
  },
  gold: {
    50: '#fefce8',
    100: '#fef3c7',
    200: '#fde68a',
    300: '#fcd34d',
    400: '#fbbf24',
    500: '#f59e0b',
    600: '#d97706',
    700: '#b45309',
    800: '#92400e',
    900: '#78350f',
    dark: '#E0B551',
  },
};

// نظام الألوان الكامل
export const THEME_COLORS = {
  light: {
    background: {
      primary: '#ffffff',
      secondary: '#f9fafb',
      tertiary: '#f3f4f6',
      overlay: 'rgba(0, 0, 0, 0.5)',
    },
    text: {
      primary: '#111827',
      secondary: '#6b7280',
      tertiary: '#9ca3af',
      muted: '#d1d5db',
    },
    border: {
      light: '#e5e7eb',
      medium: '#d1d5db',
      dark: '#9ca3af',
    },
    states: {
      success: '#10b981',
      warning: '#f59e0b',
      error: '#ef4444',
      info: '#3b82f6',
    },
    shadow: {
      sm: '0 1px 2px 0 rgb(0 0 0 / 0.05)',
      md: '0 4px 6px -1px rgb(0 0 0 / 0.1)',
      lg: '0 10px 15px -3px rgb(0 0 0 / 0.1)',
      xl: '0 20px 25px -5px rgb(0 0 0 / 0.1)',
    },
  },
  dark: {
    background: {
      primary: '#0f172a',
      secondary: '#1e293b',
      tertiary: '#334155',
      overlay: 'rgba(0, 0, 0, 0.7)',
    },
    text: {
      primary: '#f1f5f9',
      secondary: '#cbd5e1',
      tertiary: '#94a3b8',
      muted: '#64748b',
    },
    border: {
      light: '#475569',
      medium: '#334155',
      dark: '#1e293b',
    },
    states: {
      success: '#10b981',
      warning: '#f59e0b',
      error: '#ef4444',
      info: '#3b82f6',
    },
    shadow: {
      sm: '0 1px 2px 0 rgb(0 0 0 / 0.3)',
      md: '0 4px 6px -1px rgb(0 0 0 / 0.4)',
      lg: '0 10px 15px -3px rgb(0 0 0 / 0.5)',
      xl: '0 20px 25px -5px rgb(0 0 0 / 0.6)',
    },
  },
};

// التدرجات المتقدمة
export const GRADIENTS = {
  primary: {
    light: 'linear-gradient(135deg, #B78A2E 0%, #D4B25A 50%, #F0D48C 100%)',
    dark: 'linear-gradient(135deg, #D4B25A 0%, #B78A2E 100%)',
  },
  secondary: {
    light: 'linear-gradient(135deg, #E0B551 0%, #F4D683 50%, #FBBF24 100%)',
    dark: 'linear-gradient(135deg, #E0B551 0%, #F59E0B 100%)',
  },
  success: {
    light: 'linear-gradient(135deg, #10b981 0%, #14C79A 100%)',
    dark: 'linear-gradient(135deg, #14b8a6 0%, #0d9488 100%)',
  },
  warning: {
    light: 'linear-gradient(135deg, #f59e0b 0%, #E0B551 100%)',
    dark: 'linear-gradient(135deg, #fbbf24 0%, #f59e0b 100%)',
  },
  error: {
    light: 'linear-gradient(135deg, #ef4444 0%, #dc2626 100%)',
    dark: 'linear-gradient(135deg, #f87171 0%, #ef4444 100%)',
  },
  info: {
    light: 'linear-gradient(135deg, #3b82f6 0%, #2563eb 100%)',
    dark: 'linear-gradient(135deg, #60a5fa 0%, #3b82f6 100%)',
  },
};

// نظام المسافات
export const SPACING = {
  xs: '0.25rem',
  sm: '0.5rem',
  md: '1rem',
  lg: '1.5rem',
  xl: '2rem',
  '2xl': '2.5rem',
  '3xl': '3rem',
  '4xl': '4rem',
};

// نظام أحجام الخطوط
export const FONT_SIZES = {
  xs: '0.75rem',
  sm: '0.875rem',
  base: '1rem',
  lg: '1.125rem',
  xl: '1.25rem',
  '2xl': '1.5rem',
  '3xl': '1.875rem',
  '4xl': '2.25rem',
  '5xl': '3rem',
};

// أوزان الخطوط
export const FONT_WEIGHTS = {
  thin: 100,
  extralight: 200,
  light: 300,
  normal: 400,
  medium: 500,
  semibold: 600,
  bold: 700,
  extrabold: 800,
  black: 900,
};

// تقييم زاوي
export const BORDER_RADIUS = {
  none: '0',
  sm: '0.125rem',
  base: '0.25rem',
  md: '0.375rem',
  lg: '0.5rem',
  xl: '0.75rem',
  '2xl': '1rem',
  '3xl': '1.5rem',
  full: '9999px',
};

// Transitions
export const TRANSITIONS = {
  fast: '150ms ease-in-out',
  base: '200ms ease-in-out',
  slow: '300ms ease-in-out',
  slower: '500ms ease-in-out',
};

// Z-Index
export const Z_INDEX = {
  hide: -1,
  base: 0,
  dropdown: 1000,
  sticky: 1020,
  fixed: 1030,
  modalBackdrop: 1040,
  modal: 1050,
  popover: 1060,
  tooltip: 1070,
};

export const getThemeColors = (isDark: boolean) => {
  return isDark ? THEME_COLORS.dark : THEME_COLORS.light;
};

export const getGradient = (type: keyof typeof GRADIENTS, isDark: boolean) => {
  const gradient = GRADIENTS[type];
  return isDark ? gradient.dark : gradient.light;
};

export const getStateColor = (state: 'success' | 'warning' | 'error' | 'info', isDark: boolean) => {
  const colors = getThemeColors(isDark);
  return colors.states[state];
};

export const getCSSVariables = (isDark: boolean) => {
  const colors = getThemeColors(isDark);
  return {
    '--bg-primary': colors.background.primary,
    '--bg-secondary': colors.background.secondary,
    '--bg-tertiary': colors.background.tertiary,
    '--text-primary': colors.text.primary,
    '--text-secondary': colors.text.secondary,
    '--text-tertiary': colors.text.tertiary,
    '--border-light': colors.border.light,
    '--border-medium': colors.border.medium,
    '--state-success': colors.states.success,
    '--state-warning': colors.states.warning,
    '--state-error': colors.states.error,
    '--state-info': colors.states.info,
    '--gradient-primary': getGradient('primary', isDark),
    '--gradient-secondary': getGradient('secondary', isDark),
    '--gradient-success': getGradient('success', isDark),
    '--brand-emerald': isDark ? RED_BRAND_COLORS.emerald.dark : RED_BRAND_COLORS.emerald['500'],
    '--brand-gold': isDark ? RED_BRAND_COLORS.gold.dark : RED_BRAND_COLORS.gold['500'],
  } as React.CSSProperties;
};
