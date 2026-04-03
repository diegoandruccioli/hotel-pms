import { useCallback, memo, type ReactElement } from 'react';
import { useComponentTranslation } from '../hooks/useComponentTranslation';
import { itSettingsModalTranslations } from './SettingsModal.it.i18n';
import { enSettingsModalTranslations } from './SettingsModal.en.i18n';

import { M3Dialog } from './m3/M3Dialog';
import { MaterialIcon } from './MaterialIcon';
import { useThemeStore } from '../store/themeStore';
import { useSettingsStore, type FontScale } from '../store/settingsStore';

/* ── Types ──────────────────────────────────────────── */

type ThemeValue = 'light' | 'dark' | 'system';

interface SegmentOption<T extends string> {
  value: T;
  labelKey: string;
  icon: string;
}

/* ── Data ───────────────────────────────────────────── */

const THEME_OPTIONS: SegmentOption<ThemeValue>[] = [
  { value: 'light', labelKey: 'theme_light', icon: 'light_mode' },
  { value: 'dark',  labelKey: 'theme_dark',  icon: 'dark_mode' },
  { value: 'system', labelKey: 'theme_system', icon: 'desktop_windows' },
];

const FONT_OPTIONS: SegmentOption<FontScale>[] = [
  { value: 'small',  labelKey: 'font_small',  icon: 'text_fields' },
  { value: 'normal', labelKey: 'font_normal',  icon: 'text_fields' },
  { value: 'large',  labelKey: 'font_large',   icon: 'text_fields' },
];

const LANGUAGE_OPTIONS = [
  { value: 'it', labelKey: 'lang_italian',  flag: '🇮🇹' },
  { value: 'en', labelKey: 'lang_english',  flag: '🇬🇧' },
];

/* ── Sub-components ─────────────────────────────────── */

/** Section wrapper with a title */
const SettingsSection = ({
  title,
  children,
}: {
  title: string;
  children: React.ReactNode;
}) => (
  <section className="mb-6 last:mb-0">
    <h3 className="text-xs font-semibold uppercase tracking-widest text-on-surface-variant mb-3">
      {title}
    </h3>
    {children}
  </section>
);

/**
 * Single button inside a SegmentedRow.
 * Extracted to avoid creating new functions as props on every render.
 */
const SegmentedButton = memo(function SegmentedButton<T extends string>({
  opt,
  isActive,
  isFirst,
  isLast,
  onChange,
}: {
  opt: SegmentOption<T>;
  isActive: boolean;
  isFirst: boolean;
  isLast: boolean;
  onChange: (v: T) => void;
}) {
  const { t } = useComponentTranslation('SettingsModal', itSettingsModalTranslations, enSettingsModalTranslations);
  const handleClick = useCallback(() => onChange(opt.value), [onChange, opt.value]);

  return (
    <button
      type="button"
      role="radio"
      aria-checked={isActive}
      onClick={handleClick}
      className={[
        'relative flex items-center justify-center gap-1.5',
        'flex-1 h-10 px-3 text-sm font-medium font-body',
        'focus-visible:outline-none focus-visible:z-10',
        'focus-visible:ring-2 focus-visible:ring-primary focus-visible:ring-inset',
        'transition-colors',
        isFirst  ? 'rounded-l-shape-full' : '',
        isLast   ? 'rounded-r-shape-full' : '',
        isActive
          ? 'bg-secondary-container text-on-secondary-container'
          : 'bg-surface text-on-surface-variant hover:bg-surface-container-high',
        !isFirst ? 'border-l border-outline' : '',
      ].join(' ')}
      aria-label={t(opt.labelKey)}
    >
      {isActive && (
        <MaterialIcon name="check" size={16} className="shrink-0" />
      )}
      <span className="truncate">{t(opt.labelKey)}</span>
    </button>
  );
}) as <T extends string>(props: {
  opt: SegmentOption<T>;
  isActive: boolean;
  isFirst: boolean;
  isLast: boolean;
  onChange: (v: T) => void;
}) => ReactElement;

/**
 * M3 Segmented-button row (mutual exclusion).
 * Each option shows a check icon when selected.
 */
function SegmentedRow<T extends string>({
  options,
  value,
  onChange,
  ariaLabel,
}: {
  options: SegmentOption<T>[];
  value: T;
  onChange: (v: T) => void;
  ariaLabel: string;
}) {
  return (
    <div
      role="radiogroup"
      aria-label={ariaLabel}
      className="flex rounded-shape-full border border-outline overflow-hidden"
    >
      {options.map((opt, idx) => (
        <SegmentedButton
          key={opt.value}
          opt={opt}
          isActive={opt.value === value}
          isFirst={idx === 0}
          isLast={idx === options.length - 1}
          onChange={onChange}
        />
      ))}
    </div>
  );
}

/* ── LanguageButton ─────────────────────────────────── */

interface LangOption {
  value: string;
  labelKey: string;
  flag: string;
}

/** Single language selection button — memoised to avoid inline handler violations. */
const LanguageButton = memo(function LanguageButton({
  lang,
  isActive,
  onSelect,
}: {
  lang: LangOption;
  isActive: boolean;
  onSelect: (v: string) => void;
}) {
  const { t } = useComponentTranslation('SettingsModal', itSettingsModalTranslations, enSettingsModalTranslations);
  const handleClick = useCallback(() => onSelect(lang.value), [onSelect, lang.value]);

  return (
    <button
      type="button"
      role="radio"
      aria-checked={isActive}
      onClick={handleClick}
      className={[
        'flex items-center gap-3 px-4 py-3 rounded-[12px]',
        'text-sm font-body text-left',
        'border transition-colors',
        'focus-visible:outline-none focus-visible:ring-2',
        'focus-visible:ring-primary focus-visible:ring-offset-2',
        isActive
          ? 'bg-primary-container text-on-primary-container border-primary'
          : 'border-outline-variant text-on-surface hover:bg-surface-container-highest',
      ].join(' ')}
    >
      <span className="text-xl leading-none" aria-hidden="true">
        {lang.flag}
      </span>
      <span className="flex-1">{t(lang.labelKey)}</span>
      {isActive && (
        <MaterialIcon
          name="check_circle"
          size={18}
          filled
          className="text-primary shrink-0"
        />
      )}
    </button>
  );
});

/* ── Main Component ─────────────────────────────────── */

interface SettingsModalProps {
  open: boolean;
  onClose: () => void;
}

export const SettingsModal = ({ open, onClose }: SettingsModalProps) => {
  const { t, i18n } = useComponentTranslation('SettingsModal', itSettingsModalTranslations, enSettingsModalTranslations);

  const { theme, setTheme } = useThemeStore();
  const { contrast, fontScale, setContrast, setFontScale, setLanguage } = useSettingsStore();

  const handleThemeChange = useCallback(
    (v: ThemeValue) => setTheme(v),
    [setTheme]
  );

  const handleFontChange = useCallback(
    (v: FontScale) => setFontScale(v),
    [setFontScale]
  );

  const handleContrastToggle = useCallback(() => {
    setContrast(contrast === 'high' ? 'normal' : 'high');
  }, [contrast, setContrast]);

  const handleLanguageChange = useCallback(
    (lang: string) => setLanguage(lang),
    [setLanguage]
  );

  return (
    <M3Dialog
      open={open}
      onClose={onClose}
      title={t('settings')}
      titleId="settings-dialog-title"
    >
      {/* ── Theme ───────────────────────────────────── */}
      <SettingsSection title={t('settings_section_appearance')}>
        <SegmentedRow<ThemeValue>
          options={THEME_OPTIONS}
          value={theme}
          onChange={handleThemeChange}
          ariaLabel={t('settings_theme_label')}
        />
      </SettingsSection>

      {/* ── Font Scale ──────────────────────────────── */}
      <SettingsSection title={t('settings_section_typography')}>
        <SegmentedRow<FontScale>
          options={FONT_OPTIONS}
          value={fontScale}
          onChange={handleFontChange}
          ariaLabel={t('settings_font_label')}
        />
      </SettingsSection>

      {/* ── High Contrast ───────────────────────────── */}
      <SettingsSection title={t('settings_section_accessibility')}>
        <button
          type="button"
          role="switch"
          aria-checked={contrast === 'high'}
          onClick={handleContrastToggle}
          className={[
            'flex items-center justify-between w-full',
            'px-4 py-3 rounded-[12px]',
            'border border-outline-variant',
            'hover:bg-surface-container-highest',
            'focus-visible:outline-none focus-visible:ring-2',
            'focus-visible:ring-primary focus-visible:ring-offset-2',
            'transition-colors',
          ].join(' ')}
        >
          <div className="flex items-center gap-3">
            <MaterialIcon
              name="contrast"
              size={20}
              className="text-on-surface-variant"
            />
            <div className="text-left">
              <p className="text-sm font-medium text-on-surface">
                {t('settings_high_contrast')}
              </p>
              <p className="text-xs text-on-surface-variant">
                {t('settings_high_contrast_desc')}
              </p>
            </div>
          </div>

          {/* M3 Switch visual */}
          <div
            aria-hidden="true"
            className={[
              'relative w-12 h-7 rounded-shape-full border-2 transition-colors',
              contrast === 'high'
                ? 'bg-primary border-primary'
                : 'bg-surface-container-highest border-outline',
            ].join(' ')}
          >
            <span
              className={[
                'absolute top-0.5 block w-5 h-5 rounded-shape-full',
                'shadow-elevation-1 transition-all duration-200',
                contrast === 'high'
                  ? 'translate-x-[22px] bg-on-primary'
                  : 'translate-x-0.5 bg-outline',
              ].join(' ')}
            />
          </div>
        </button>
      </SettingsSection>

      {/* ── Language ────────────────────────────────── */}
      <SettingsSection title={t('settings_section_language')}>
        <div
          role="radiogroup"
          aria-label={t('settings_language_label')}
          className="flex flex-col gap-2"
        >
          {LANGUAGE_OPTIONS.map((lang) => (
            <LanguageButton
              key={lang.value}
              lang={lang}
              isActive={i18n.language.startsWith(lang.value)}
              onSelect={handleLanguageChange}
            />
          ))}
        </div>
      </SettingsSection>
    </M3Dialog>
  );
};
