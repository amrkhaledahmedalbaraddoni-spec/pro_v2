import type { Config } from 'tailwindcss';
import animate from 'tailwindcss-animate';

const config: Config = {
  content: [
    './index.html',
    './src/**/*.{js,ts,jsx,tsx}',
  ],
  darkMode: 'class',
  theme: {
    extend: {
      // ملاحظة: المتغيرات --background/--primary/... معرّفة في src/styles.css
      // كألوان مباشرة (ليست قيم hsl خام)، فتُقرأ هنا بلا غلاف hsl() —
      // وبهذا تعمل أدوات bg-background وtext-foreground... في الوضعين الداكن والفاتح معًا.
      colors: {
        border: 'var(--border)',
        input: 'var(--input)',
        ring: 'var(--ring)',
        background: 'var(--background)',
        foreground: 'var(--foreground)',
        primary: {
          DEFAULT: 'var(--primary)',
          foreground: 'var(--primary-foreground)',
        },
        secondary: {
          DEFAULT: 'var(--secondary)',
          foreground: 'var(--secondary-foreground)',
        },
        destructive: {
          DEFAULT: 'var(--destructive)',
          foreground: 'var(--destructive-foreground)',
        },
        muted: {
          DEFAULT: 'var(--muted)',
          foreground: 'var(--muted-foreground)',
        },
        accent: {
          DEFAULT: 'var(--accent)',
          foreground: 'var(--accent-foreground)',
        },
        popover: {
          DEFAULT: 'var(--popover)',
          foreground: 'var(--popover-foreground)',
        },
        card: {
          DEFAULT: 'var(--card)',
          foreground: 'var(--card-foreground)',
        },
        yn: {
          green: {
            DEFAULT: '#14C79A',
            dark: '#0B6B4F',
            light: '#3DE8BC',
          },
          gold: {
            DEFAULT: '#E0B551',
            dark: '#9A7B2E',
          },
          blue: {
            DEFAULT: '#4D9FE8',
            dark: '#1E4E8A',
          },
          navy: '#0A0F18',
          dark: '#0A0F14',
          surface: '#131C29',
          'surface-light': '#1B2635',
          border: '#2A394A',
          text: '#F2F6F8',
          'text-secondary': '#9AAEBB',
          'text-muted': '#7C90A0',
          error: '#F25C5C',
          warning: '#E0B551',
          success: '#14C79A',
          info: '#4D9FE8',
        },
      },
      fontFamily: {
        primary: ['IBM Plex Sans Arabic', 'Segoe UI', 'Tahoma', 'Arial', 'sans-serif'],
        heading: ['IBM Plex Sans Arabic', 'Segoe UI', 'sans-serif'],
        mono: ['JetBrains Mono', 'Fira Code', 'monospace'],
        sans: ['IBM Plex Sans Arabic', 'Segoe UI', 'Tahoma', 'Arial', 'sans-serif'],
      },
      fontSize: {
        'display-xl': ['2.25rem', { letterSpacing: '-0.02em', lineHeight: '1.3' }],
        'display-lg': ['1.75rem', { letterSpacing: '-0.01em', lineHeight: '1.3' }],
        'display-md': ['1.375rem', { lineHeight: '1.3' }],
        'display-sm': ['1.125rem', { lineHeight: '1.3' }],
        'base': ['1rem', { lineHeight: '1.6' }],
        'sm': ['0.875rem', { lineHeight: '1.5' }],
        'xs': ['0.75rem', { lineHeight: '1.5' }],
      },
      spacing: {
        '1': '4px',
        '2': '8px',
        '3': '12px',
        '4': '16px',
        '5': '20px',
        '6': '24px',
        '8': '32px',
        '10': '40px',
        '12': '48px',
        '16': '64px',
      },
      borderRadius: {
        lg: 'var(--radius)',
        md: 'calc(var(--radius) - 2px)',
        sm: 'calc(var(--radius) - 4px)',
        xl: '12px',
        '2xl': '16px',
        full: '9999px',
      },
      boxShadow: {
        sm: '0 1px 2px rgba(15, 27, 45, 0.08)',
        md: '0 4px 16px rgba(15, 27, 45, 0.12)',
        lg: '0 8px 32px rgba(15, 27, 45, 0.16)',
        'glow-green': '0 0 15px rgba(20, 199, 154, 0.25)',
        'glow-gold': '0 0 15px rgba(224, 181, 81, 0.25)',
        'glow-blue': '0 0 15px rgba(77, 159, 232, 0.25)',
      },
      transitionDuration: {
        fast: '150ms',
        normal: '250ms',
        slow: '400ms',
      },
      transitionTimingFunction: {
        DEFAULT: 'ease',
      },
      zIndex: {
        base: '0',
        dropdown: '100',
        sticky: '200',
        overlay: '300',
        modal: '400',
        toast: '500',
      },
      backgroundImage: {
        'gradient-primary': 'linear-gradient(135deg, var(--yns-green) 0%, var(--yns-blue) 100%)',
        'gradient-gold': 'linear-gradient(135deg, var(--yns-gold) 0%, var(--yns-green) 100%)',
        'gradient-surface': 'linear-gradient(180deg, var(--yns-surface) 0%, var(--yns-navy) 100%)',
        'gradient-header': 'linear-gradient(180deg, rgba(26, 36, 44, 0.6) 0%, transparent 100%)',
        'geometric-pattern': 'url("data:image/svg+xml,%3Csvg width=\'60\' height=\'60\' viewBox=\'0 0 60 60\' xmlns=\'http://www.w3.org/2000/svg\'%3E%3Cg fill=\'none\' fill-rule=\'evenodd\'%3E%3Cg fill=\'%2300B37E\' fill-opacity=\'0.05\'%3E%3Cpath d=\'M36 34v-4h-2v4h-4v2h4v4h2v-4h4v-2h-4zm0-30V0h-2v4h-4v2h4v4h2V6h4V4h-4zM6 34v-4H4v4H0v2h4v4h2V6h4V4H6zM6 4V0H4v4H0v2h4v4h2V6h4V4H6z\'/%3E%3C/g%3E%3C/g%3E%3C/svg%3E")',
      },
    },
  },
  plugins: [animate],
};

export default config;