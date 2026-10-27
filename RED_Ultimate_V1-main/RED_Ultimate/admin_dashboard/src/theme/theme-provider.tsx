/**
 * ═══════════════════════════════════════════════════════════════
 * Theme Provider — مزود الثيمات مع دعم كامل للتبديل والحفظ
 * ═══════════════════════════════════════════════════════════════
 */

import React, { createContext, useContext, useEffect, useState, ReactNode } from 'react';
import type { PaletteName, ThemeMode } from './theme-complete';

// ═══ Types ═══════════════════════════════════════════════════════
export interface ThemeContextType {
  mode: ThemeMode;
  palette: PaletteName;
  toggleMode: (mode?: ThemeMode) => void;
  setPalette: (palette: PaletteName) => void;
  isDark: boolean;
  isLight: boolean;
  availablePalettes: PaletteName[];
}

// ═══ Constants ═══════════════════════════════════════════════════
const STORAGE_KEY_MODE = 'yns-theme-mode';
const STORAGE_KEY_PALETTE = 'yns-theme-palette';

const DEFAULT_MODE: ThemeMode = 'light';
const DEFAULT_PALETTE: PaletteName = 'normal';

const AVAILABLE_PALETTES: PaletteName[] = [
  'normal',
  'vibrant',
  'soft',
  'ocean',
  'forest',
  'sunset',
];

const PALETTE_LABELS: Record<PaletteName, string> = {
  normal: 'عادي - النمط الأساسي',
  vibrant: 'حيوي - ألوان مبهجة',
  soft: 'ناعم - ألوان هادئة',
  ocean: 'محيط - ألوان زرقاء',
  forest: 'غابة - ألوان طبيعية',
  sunset: 'غروب - ألوان دافئة',
};

// ═══ Context ═══════════════════════════════════════════════════
const ThemeContext = createContext<ThemeContextType | undefined>(undefined);

// ═══ Provider Component ═══════════════════════════════════════════
export interface ThemeProviderProps {
  children: ReactNode;
  defaultMode?: ThemeMode;
  defaultPalette?: PaletteName;
  storageEnabled?: boolean;
}

export function ThemeProvider({
  children,
  defaultMode = DEFAULT_MODE,
  defaultPalette = DEFAULT_PALETTE,
  storageEnabled = true,
}: ThemeProviderProps): React.ReactElement {
  const [mode, setMode] = useState<ThemeMode>(defaultMode);
  const [palette, setPaletteState] = useState<PaletteName>(defaultPalette);
  const [mounted, setMounted] = useState(false);

  // ═══ Initialize from localStorage ═══════════════════════════
  useEffect(() => {
    if (!storageEnabled) {
      setMounted(true);
      return;
    }

    try {
      const savedMode = localStorage.getItem(STORAGE_KEY_MODE) as ThemeMode | null;
      const savedPalette = localStorage.getItem(STORAGE_KEY_PALETTE) as PaletteName | null;

      if (savedMode && ['light', 'dark'].includes(savedMode)) {
        setMode(savedMode);
      }

      if (savedPalette && AVAILABLE_PALETTES.includes(savedPalette)) {
        setPaletteState(savedPalette);
      }
    } catch (error) {
      console.warn('Failed to load theme from storage:', error);
    }

    setMounted(true);
  }, [storageEnabled]);

  // ═══ Apply theme to DOM ═══════════════════════════════════════
  useEffect(() => {
    if (!mounted) return;

    // Set color scheme
    if (typeof document !== 'undefined') {
      document.documentElement.setAttribute('data-theme', mode);
      document.documentElement.setAttribute('data-palette', palette);

      // Apply prefers-color-scheme
      if (mode === 'dark') {
        document.documentElement.style.colorScheme = 'dark';
      } else {
        document.documentElement.style.colorScheme = 'light';
      }
    }

    // Save to storage
    if (storageEnabled) {
      try {
        localStorage.setItem(STORAGE_KEY_MODE, mode);
        localStorage.setItem(STORAGE_KEY_PALETTE, palette);
      } catch (error) {
        console.warn('Failed to save theme to storage:', error);
      }
    }
  }, [mode, palette, mounted, storageEnabled]);

  // ═══ Toggle Mode ═══════════════════════════════════════════
  const toggleMode = (nextMode?: ThemeMode): void => {
    if (nextMode) {
      setMode(nextMode);
      return;
    }

    setMode((prev) => (prev === 'light' ? 'dark' : 'light'));
  };

  // ═══ Set Palette ═══════════════════════════════════════════
  const setPalette = (newPalette: PaletteName): void => {
    if (AVAILABLE_PALETTES.includes(newPalette)) {
      setPaletteState(newPalette);
    }
  };

  const value: ThemeContextType = {
    mode,
    palette,
    toggleMode,
    setPalette,
    isDark: mode === 'dark',
    isLight: mode === 'light',
    availablePalettes: AVAILABLE_PALETTES,
  };

  // ═══ Prevent FOUC (Flash of Unstyled Content) ═══════════════
  if (!mounted) {
    return <>{children}</>;
  }

  return (
    <ThemeContext.Provider value={value}>
      {children}
    </ThemeContext.Provider>
  );
}

// ═══ Hook: useTheme ═══════════════════════════════════════════════
export function useTheme(): ThemeContextType {
  const context = useContext(ThemeContext);

  if (!context) {
    throw new Error(
      'useTheme must be used within a ThemeProvider. ' +
      'Make sure ThemeProvider wraps your component tree.'
    );
  }

  return context;
}

// ═══ Hook: useThemeMode ═══════════════════════════════════════════
export function useThemeMode(): [ThemeMode, (mode?: ThemeMode) => void] {
  const { mode, toggleMode } = useTheme();
  return [mode, toggleMode];
}

// ═══ Hook: usePalette ═══════════════════════════════════════════════
export function usePalette(): [PaletteName, (palette: PaletteName) => void] {
  const { palette, setPalette } = useTheme();
  return [palette, setPalette];
}

// ═══ Hook: useIsDark ═══════════════════════════════════════════════
export function useIsDark(): boolean {
  const { isDark } = useTheme();
  return isDark;
}

// ═══ Hook: useIsLight ═══════════════════════════════════════════════
export function useIsLight(): boolean {
  const { isLight } = useTheme();
  return isLight;
}

// ═══ Component: Theme Toggle ═══════════════════════════════════════
export interface ThemeToggleProps {
  className?: string;
  showLabel?: boolean;
}

export function ThemeToggle({
  className = '',
  showLabel = true,
}: ThemeToggleProps): React.ReactElement {
  const { mode, toggleMode, isDark } = useTheme();

  return (
    <button
      onClick={() => toggleMode()}
      className={`theme-toggle ${className}`}
      aria-label={`تبديل إلى الوضع ${isDark ? 'الفاتح' : 'الداكن'}`}
      title={`الوضع الحالي: ${isDark ? 'داكن' : 'فاتح'}`}
    >
      <span className="theme-toggle-icon">
        {isDark ? '☀️' : '🌙'}
      </span>
      {showLabel && (
        <span className="theme-toggle-label">
          {isDark ? 'فاتح' : 'داكن'}
        </span>
      )}
    </button>
  );
}

// ═══ Component: Palette Selector ═══════════════════════════════════
export interface PaletteSelectorProps {
  className?: string;
  compact?: boolean;
}

export function PaletteSelector({
  className = '',
  compact = false,
}: PaletteSelectorProps): React.ReactElement {
  const { palette, setPalette, availablePalettes } = useTheme();

  if (compact) {
    return (
      <select
        value={palette}
        onChange={(e) => setPalette(e.target.value as PaletteName)}
        className={`palette-selector-compact ${className}`}
        aria-label="اختر نمط الألوان"
      >
        {availablePalettes.map((p) => (
          <option key={p} value={p}>
            {PALETTE_LABELS[p]}
          </option>
        ))}
      </select>
    );
  }

  return (
    <div className={`palette-selector ${className}`}>
      <div className="palette-selector-label">اختر نمط الألوان</div>
      <div className="palette-selector-options">
        {availablePalettes.map((p) => (
          <button
            key={p}
            onClick={() => setPalette(p)}
            className={`palette-option ${p === palette ? 'active' : ''}`}
            title={PALETTE_LABELS[p]}
            aria-pressed={p === palette}
          >
            <span className="palette-preview">{p}</span>
            <span className="palette-name">{PALETTE_LABELS[p]}</span>
          </button>
        ))}
      </div>
    </div>
  );
}

// ═══ Component: Theme Settings Panel ═══════════════════════════════
export interface ThemeSettingsPanelProps {
  className?: string;
  compact?: boolean;
}

export function ThemeSettingsPanel({
  className = '',
  compact = false,
}: ThemeSettingsPanelProps): React.ReactElement {
  const { mode, palette, toggleMode, setPalette, availablePalettes } = useTheme();

  return (
    <div className={`theme-settings-panel ${compact ? 'compact' : 'full'} ${className}`}>
      <div className="theme-settings-header">
        <h3>إعدادات الثيم</h3>
      </div>

      <div className="theme-settings-content">
        {/* Mode Selector */}
        <div className="theme-setting-group">
          <label className="theme-setting-label">الوضع</label>
          <div className="theme-mode-buttons">
            <button
              onClick={() => toggleMode('light')}
              className={`mode-btn ${mode === 'light' ? 'active' : ''}`}
              aria-pressed={mode === 'light'}
            >
              ☀️ فاتح
            </button>
            <button
              onClick={() => toggleMode('dark')}
              className={`mode-btn ${mode === 'dark' ? 'active' : ''}`}
              aria-pressed={mode === 'dark'}
            >
              🌙 داكن
            </button>
          </div>
        </div>

        {/* Palette Selector */}
        <div className="theme-setting-group">
          <label className="theme-setting-label">نمط الألوان</label>
          <div className="palette-options">
            {availablePalettes.map((p) => (
              <button
                key={p}
                onClick={() => setPalette(p)}
                className={`palette-btn ${p === palette ? 'active' : ''}`}
                title={PALETTE_LABELS[p]}
                aria-pressed={p === palette}
              >
                {PALETTE_LABELS[p]}
              </button>
            ))}
          </div>
        </div>
      </div>
    </div>
  );
}

// ═══ Export all utilities ═══════════════════════════════════════════
export {
  AVAILABLE_PALETTES,
  PALETTE_LABELS,
  DEFAULT_MODE,
  DEFAULT_PALETTE,
};

export default ThemeProvider;
