import type { ThemeConfig } from 'antd';
import { theme as antdTheme } from 'antd';

/**
 * ═══ الثيم المركزي للوحة يونس السيادية — «ذهب الليل» ═══
 *
 * المصدر الوحيد لثيم Ant Design: `App.tsx` يستهلكه عبر
 * `theme={buildYounesTheme(resolvedTheme)}`.
 *
 * الهوية: ذهبي دافئ على أوبسيديان كحلي. الأساسي الداكن #D4B25A
 * (9.45:1 على #0A0F18) مع نص داكن #06090F عليه (9.45:1)؛ الأبيض
 * على الذهب راسب (2.1:1) فلا يُستعمل. الفاتح #8A6719 على الأبيض
 * (5.2:1) مع نص أبيض (5.2:1).
 *
 * الحياديات مطابقة لـ `src/styles.css` (`--yns-*`):
 * الخلفية #0A0F18 والسطح #131C29 والحدود #2A394A.
 * حارس التباين `scripts/check-contrast.mjs` يقرأ `--yns-*` من styles.css
 * (لا تُخفَّض أي قيمة دون حدّها).
 */

export type YounesThemeMode = 'light' | 'dark' | 'system';
export type YounesResolvedTheme = 'light' | 'dark';

/** مفتاح التخزين المحلي — تفضيل عرض فقط، لا أسرار ولا توكنات. */
export const YNS_THEME_STORAGE_KEY = 'yns-theme';

/**
 * ألوان الحالة السيمانتيكية للمكوّنات الديناميكية (قيم valueStyle وinline styles) —
 * مصدر واحد thay التكرار الحرفي: زمردي #14C79A للنجاح/الاتصال (داكن)،
 * أحمر #F25C5C للفصل، ذهبي #E0A83C للتحذير — مطابقة للـtokens أعلاه.
 */
export const YNS_STATUS_COLORS = {
  ok: '#14C79A',
  error: '#F25C5C',
  warning: '#E0A83C',
} as const;

const FONT_STACK = "'IBM Plex Sans Arabic', 'Segoe UI', Tahoma, Arial, sans-serif";

/** لوحة الداكن السيادية: ذهبي على أوبسيديان — مطابقة لـ :root في styles.css */
const darkToken: ThemeConfig['token'] = {
  colorPrimary: '#D4B25A',
  colorSuccess: '#14C79A',
  colorWarning: '#E0B551',
  colorError: '#FF5A5F',
  colorInfo: '#4FC3F7',
  colorLink: '#D4B25A',
  colorTextLightSolid: '#06090F',
  colorBgBase: '#0A0F18',
  colorBgContainer: '#131C29',
  colorBgLayout: '#0A0F18',
  colorBorder: '#2A394A',
  colorText: '#F2F6F8',
  colorTextSecondary: '#9AAEBB',
  colorTextTertiary: '#7C90A0',
  borderRadius: 14,
  fontFamily: FONT_STACK,
};

/** لوحة الفاتح الرسمية: لؤلؤي + كحلي + ذهبي داكن — مطابقة لـ [data-theme="light"] */
const lightToken: ThemeConfig['token'] = {
  colorPrimary: '#8A6719',
  colorSuccess: '#007A5E',
  colorWarning: '#8A6719',
  colorError: '#D32F2F',
  colorInfo: '#1976D2',
  colorLink: '#8A6719',
  colorTextLightSolid: '#FFFFFF',
  colorBgBase: '#F7F8FA',
  colorBgContainer: '#FFFFFF',
  colorBgLayout: '#F7F8FA',
  colorBorder: '#E6E8EB',
  colorText: '#0F1B2D',
  colorTextSecondary: '#5A6B7D',
  colorTextTertiary: '#6B7D8F',
  borderRadius: 14,
  fontFamily: FONT_STACK,
};

export function buildYounesTheme(resolved: YounesResolvedTheme): ThemeConfig {
  const dark = resolved === 'dark';
  return {
    algorithm: dark ? antdTheme.darkAlgorithm : antdTheme.defaultAlgorithm,
    token: dark ? darkToken : lightToken,
    components: {
      Menu: {
        darkItemBg: 'transparent',
        darkSubMenuItemBg: 'transparent',
        darkItemColor: '#9AAEBB',
        darkItemHoverBg: 'rgba(212,178,90,0.12)',
        darkItemHoverColor: '#F0D48C',
        darkItemSelectedBg:
          'linear-gradient(135deg, rgba(212,178,90,0.24) 0%, rgba(77,159,232,0.12) 100%)' as unknown as string,
        darkItemSelectedColor: '#F0D48C',
        itemBorderRadius: 10,
        itemMarginInline: 10,
      },
      Layout: {
        siderBg: '#0A1014',
        headerBg: dark ? 'rgba(8, 21, 37, 0.85)' : 'rgba(255,255,255,0.85)',
        headerHeight: 64,
      },
      Card: {
        borderRadiusLG: 20,
      },
    },
  };
}
