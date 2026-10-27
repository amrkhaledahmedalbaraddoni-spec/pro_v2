/**
 * ═══════════════════════════════════════════════════════════════
 * Component Styles — أنماط المكونات الأساسية مع الحالات التفاعلية
 * ═══════════════════════════════════════════════════════════════
 */

import type { CSSProperties } from 'react';

// ═══ Component Style Types ═══════════════════════════════════════
export interface ComponentStyleConfig {
  base: CSSProperties;
  hover: CSSProperties;
  active: CSSProperties;
  disabled: CSSProperties;
  focus: CSSProperties;
}

export interface InteractiveComponentStyles {
  [key: string]: ComponentStyleConfig;
}

// ═══ Button Styles ═══════════════════════════════════════════════
export const buttonStyles: InteractiveComponentStyles = {
  primary: {
    base: {
      display: 'inline-flex',
      alignItems: 'center',
      justifyContent: 'center',
      gap: 'var(--sp-2)',
      padding: 'var(--sp-3) var(--sp-5)',
      fontFamily: 'var(--font-primary)',
      fontSize: '0.875rem',
      fontWeight: 600,
      borderRadius: 'var(--radius-md)',
      border: 'none',
      cursor: 'pointer',
      transition: 'all var(--transition-fast)',
      textDecoration: 'none',
      whiteSpace: 'nowrap',
      background: 'var(--color-primary-500)',
      color: 'var(--color-bg-primary)',
      boxShadow: 'var(--shadow-md)',
    },
    hover: {
      background: 'var(--color-primary-600)',
      transform: 'translateY(-1px)',
      boxShadow: 'var(--shadow-lg)',
    },
    active: {
      background: 'var(--color-primary-700)',
      transform: 'translateY(0)',
    },
    disabled: {
      opacity: 0.5,
      cursor: 'not-allowed',
      transform: 'none',
    },
    focus: {
      outline: '2px solid var(--color-primary-300)',
      outlineOffset: '2px',
    },
  },

  secondary: {
    base: {
      display: 'inline-flex',
      alignItems: 'center',
      justifyContent: 'center',
      gap: 'var(--sp-2)',
      padding: 'var(--sp-3) var(--sp-5)',
      fontFamily: 'var(--font-primary)',
      fontSize: '0.875rem',
      fontWeight: 600,
      borderRadius: 'var(--radius-md)',
      border: '1px solid var(--color-border-light)',
      cursor: 'pointer',
      transition: 'all var(--transition-fast)',
      textDecoration: 'none',
      whiteSpace: 'nowrap',
      background: 'var(--color-bg-secondary)',
      color: 'var(--color-text-primary)',
    },
    hover: {
      background: 'var(--color-bg-tertiary)',
      borderColor: 'var(--color-border-dark)',
    },
    active: {
      background: 'var(--color-bg-quaternary)',
    },
    disabled: {
      opacity: 0.5,
      cursor: 'not-allowed',
    },
    focus: {
      outline: '2px solid var(--color-primary-300)',
      outlineOffset: '2px',
    },
  },

  danger: {
    base: {
      display: 'inline-flex',
      alignItems: 'center',
      justifyContent: 'center',
      gap: 'var(--sp-2)',
      padding: 'var(--sp-3) var(--sp-5)',
      fontFamily: 'var(--font-primary)',
      fontSize: '0.875rem',
      fontWeight: 600,
      borderRadius: 'var(--radius-md)',
      border: 'none',
      cursor: 'pointer',
      transition: 'all var(--transition-fast)',
      textDecoration: 'none',
      whiteSpace: 'nowrap',
      background: 'var(--color-error)',
      color: 'white',
    },
    hover: {
      background: '#FF7070',
      transform: 'translateY(-1px)',
    },
    active: {
      background: '#E63946',
      transform: 'translateY(0)',
    },
    disabled: {
      opacity: 0.5,
      cursor: 'not-allowed',
    },
    focus: {
      outline: '2px solid var(--color-error)',
      outlineOffset: '2px',
    },
  },

  ghost: {
    base: {
      display: 'inline-flex',
      alignItems: 'center',
      justifyContent: 'center',
      gap: 'var(--sp-2)',
      padding: 'var(--sp-3) var(--sp-5)',
      fontFamily: 'var(--font-primary)',
      fontSize: '0.875rem',
      fontWeight: 600,
      borderRadius: 'var(--radius-md)',
      border: 'none',
      cursor: 'pointer',
      transition: 'all var(--transition-fast)',
      textDecoration: 'none',
      whiteSpace: 'nowrap',
      background: 'transparent',
      color: 'var(--color-text-secondary)',
    },
    hover: {
      background: 'var(--color-bg-tertiary)',
      color: 'var(--color-text-primary)',
    },
    active: {
      background: 'var(--color-bg-quaternary)',
    },
    disabled: {
      opacity: 0.5,
      cursor: 'not-allowed',
    },
    focus: {
      outline: '2px solid var(--color-primary-300)',
      outlineOffset: '2px',
    },
  },
};

// ═══ Input Styles ═══════════════════════════════════════════════
export const inputStyles: InteractiveComponentStyles = {
  default: {
    base: {
      width: '100%',
      padding: 'var(--sp-3)',
      fontFamily: 'var(--font-primary)',
      fontSize: '0.9375rem',
      color: 'var(--color-text-primary)',
      background: 'var(--color-bg-secondary)',
      border: '1px solid var(--color-border-light)',
      borderRadius: 'var(--radius-md)',
      transition: 'all var(--transition-fast)',
      boxSizing: 'border-box',
    },
    hover: {
      borderColor: 'var(--color-border-dark)',
    },
    active: {
      borderColor: 'var(--color-primary-500)',
    },
    disabled: {
      opacity: 0.6,
      cursor: 'not-allowed',
      background: 'var(--color-bg-tertiary)',
    },
    focus: {
      outline: 'none',
      borderColor: 'var(--color-primary-500)',
      boxShadow: '0 0 0 3px rgba(0, 122, 186, 0.1)',
    },
  },
};

// ═══ Card Styles ═══════════════════════════════════════════════
export const cardStyles: ComponentStyleConfig = {
  base: {
    background: 'var(--color-bg-secondary)',
    border: '1px solid var(--color-border-light)',
    borderRadius: 'var(--radius-lg)',
    padding: 'var(--sp-5)',
    transition: 'all var(--transition-normal)',
  },
  hover: {
    borderColor: 'rgba(0, 122, 186, 0.3)',
    boxShadow: 'var(--shadow-md)',
  },
  active: {
    boxShadow: 'var(--shadow-lg)',
  },
  disabled: {
    opacity: 0.6,
  },
  focus: {
    outline: '2px solid var(--color-primary-300)',
    outlineOffset: '2px',
  },
};

// ═══ Badge Styles ═══════════════════════════════════════════════
export const badgeStyles: InteractiveComponentStyles = {
  primary: {
    base: {
      display: 'inline-flex',
      alignItems: 'center',
      padding: '2px 10px',
      fontSize: '0.75rem',
      fontWeight: 600,
      borderRadius: 'var(--radius-full)',
      background: 'rgba(0, 122, 186, 0.15)',
      color: 'var(--color-primary-500)',
    },
    hover: {},
    active: {},
    disabled: {},
    focus: {},
  },

  success: {
    base: {
      display: 'inline-flex',
      alignItems: 'center',
      padding: '2px 10px',
      fontSize: '0.75rem',
      fontWeight: 600,
      borderRadius: 'var(--radius-full)',
      background: 'rgba(16, 185, 129, 0.15)',
      color: 'var(--color-success)',
    },
    hover: {},
    active: {},
    disabled: {},
    focus: {},
  },

  warning: {
    base: {
      display: 'inline-flex',
      alignItems: 'center',
      padding: '2px 10px',
      fontSize: '0.75rem',
      fontWeight: 600,
      borderRadius: 'var(--radius-full)',
      background: 'rgba(245, 158, 11, 0.15)',
      color: 'var(--color-warning)',
    },
    hover: {},
    active: {},
    disabled: {},
    focus: {},
  },

  error: {
    base: {
      display: 'inline-flex',
      alignItems: 'center',
      padding: '2px 10px',
      fontSize: '0.75rem',
      fontWeight: 600,
      borderRadius: 'var(--radius-full)',
      background: 'rgba(239, 68, 68, 0.15)',
      color: 'var(--color-error)',
    },
    hover: {},
    active: {},
    disabled: {},
    focus: {},
  },
};

// ═══ Avatar Styles ═══════════════════════════════════════════════
export const avatarStyles: ComponentStyleConfig = {
  base: {
    width: '36px',
    height: '36px',
    borderRadius: 'var(--radius-full)',
    display: 'flex',
    alignItems: 'center',
    justifyContent: 'center',
    fontWeight: 700,
    fontSize: '0.875rem',
    background: 'linear-gradient(135deg, var(--color-primary-500) 0%, var(--color-secondary-500) 100%)',
    color: 'white',
  },
  hover: {},
  active: {},
  disabled: {},
  focus: {},
};

// ═══ Status Badge Styles ═══════════════════════════════════════
export const statusBadgeStyles: InteractiveComponentStyles = {
  online: {
    base: {
      display: 'inline-flex',
      alignItems: 'center',
      gap: 'var(--sp-2)',
      height: '30px',
      whiteSpace: 'nowrap',
      padding: '0 var(--sp-3)',
      borderRadius: 'var(--radius-full)',
      fontSize: '0.78rem',
      fontWeight: 600,
      border: '1px solid transparent',
      color: 'var(--color-success)',
      background: 'rgba(16, 185, 129, 0.1)',
      borderColor: 'rgba(16, 185, 129, 0.35)',
    },
    hover: {},
    active: {},
    disabled: {},
    focus: {},
  },

  offline: {
    base: {
      display: 'inline-flex',
      alignItems: 'center',
      gap: 'var(--sp-2)',
      height: '30px',
      whiteSpace: 'nowrap',
      padding: '0 var(--sp-3)',
      borderRadius: 'var(--radius-full)',
      fontSize: '0.78rem',
      fontWeight: 600,
      border: '1px solid transparent',
      color: 'var(--color-error)',
      background: 'rgba(239, 68, 68, 0.1)',
      borderColor: 'rgba(239, 68, 68, 0.35)',
    },
    hover: {},
    active: {},
    disabled: {},
    focus: {},
  },

  pending: {
    base: {
      display: 'inline-flex',
      alignItems: 'center',
      gap: 'var(--sp-2)',
      height: '30px',
      whiteSpace: 'nowrap',
      padding: '0 var(--sp-3)',
      borderRadius: 'var(--radius-full)',
      fontSize: '0.78rem',
      fontWeight: 600,
      border: '1px solid transparent',
      color: 'var(--color-warning)',
      background: 'rgba(245, 158, 11, 0.1)',
      borderColor: 'rgba(245, 158, 11, 0.35)',
    },
    hover: {},
    active: {},
    disabled: {},
    focus: {},
  },
};

// ═══ Alert Styles ═══════════════════════════════════════════════
export const alertStyles: InteractiveComponentStyles = {
  success: {
    base: {
      padding: 'var(--sp-4)',
      borderRadius: 'var(--radius-md)',
      display: 'flex',
      alignItems: 'flex-start',
      gap: 'var(--sp-3)',
      fontSize: '0.9375rem',
      background: 'rgba(16, 185, 129, 0.1)',
      border: '1px solid rgba(16, 185, 129, 0.3)',
      color: 'var(--color-success)',
    },
    hover: {},
    active: {},
    disabled: {},
    focus: {},
  },

  info: {
    base: {
      padding: 'var(--sp-4)',
      borderRadius: 'var(--radius-md)',
      display: 'flex',
      alignItems: 'flex-start',
      gap: 'var(--sp-3)',
      fontSize: '0.9375rem',
      background: 'rgba(14, 165, 233, 0.1)',
      border: '1px solid rgba(14, 165, 233, 0.3)',
      color: 'var(--color-info)',
    },
    hover: {},
    active: {},
    disabled: {},
    focus: {},
  },

  warning: {
    base: {
      padding: 'var(--sp-4)',
      borderRadius: 'var(--radius-md)',
      display: 'flex',
      alignItems: 'flex-start',
      gap: 'var(--sp-3)',
      fontSize: '0.9375rem',
      background: 'rgba(245, 158, 11, 0.1)',
      border: '1px solid rgba(245, 158, 11, 0.3)',
      color: 'var(--color-warning)',
    },
    hover: {},
    active: {},
    disabled: {},
    focus: {},
  },

  error: {
    base: {
      padding: 'var(--sp-4)',
      borderRadius: 'var(--radius-md)',
      display: 'flex',
      alignItems: 'flex-start',
      gap: 'var(--sp-3)',
      fontSize: '0.9375rem',
      background: 'rgba(239, 68, 68, 0.1)',
      border: '1px solid rgba(239, 68, 68, 0.3)',
      color: 'var(--color-error)',
    },
    hover: {},
    active: {},
    disabled: {},
    focus: {},
  },
};

// ═══ Utility Functions ═══════════════════════════════════════════
export function combineStyles(
  ...styles: (CSSProperties | undefined | null | false)[]
): CSSProperties {
  return styles.reduce<CSSProperties>((acc, style) => {
    if (style) {
      return { ...acc, ...style };
    }
    return acc;
  }, {} as CSSProperties);
}

export function getComponentStyle(
  component: string,
  variant: string,
  state: 'base' | 'hover' | 'active' | 'disabled' | 'focus' = 'base'
): CSSProperties {
  const styles: Record<string, InteractiveComponentStyles> = {
    button: buttonStyles,
    input: inputStyles,
    badge: badgeStyles,
    statusBadge: statusBadgeStyles,
    alert: alertStyles,
  };

  const componentStyles = styles[component];
  if (!componentStyles) {
    return {};
  }

  const variantStyle = componentStyles[variant];
  if (!variantStyle) {
    return {};
  }

  return variantStyle[state] || {};
}

// ═══ CSS String Generators ═══════════════════════════════════════
export function generateButtonCss(variant: string = 'primary'): string {
  const style = buttonStyles[variant];
  if (!style) return '';

  return `
    .btn-${variant} {
      ${Object.entries(style.base)
        .map(([key, value]) => `${camelToKebab(key)}: ${value};`)
        .join('\n')}
    }

    .btn-${variant}:hover {
      ${Object.entries(style.hover)
        .map(([key, value]) => `${camelToKebab(key)}: ${value};`)
        .join('\n')}
    }

    .btn-${variant}:active {
      ${Object.entries(style.active)
        .map(([key, value]) => `${camelToKebab(key)}: ${value};`)
        .join('\n')}
    }

    .btn-${variant}:disabled {
      ${Object.entries(style.disabled)
        .map(([key, value]) => `${camelToKebab(key)}: ${value};`)
        .join('\n')}
    }

    .btn-${variant}:focus-visible {
      ${Object.entries(style.focus)
        .map(([key, value]) => `${camelToKebab(key)}: ${value};`)
        .join('\n')}
    }
  `;
}

function camelToKebab(str: string): string {
  return str.replace(/([a-z0-9]|(?=[A-Z]))([A-Z])/g, '$1-$2').toLowerCase();
}

// ═══ Export all styles ═══════════════════════════════════════════
export default {
  buttonStyles,
  inputStyles,
  cardStyles,
  badgeStyles,
  avatarStyles,
  statusBadgeStyles,
  alertStyles,
  combineStyles,
  getComponentStyle,
  generateButtonCss,
};
