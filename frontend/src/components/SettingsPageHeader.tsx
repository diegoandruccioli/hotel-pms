import type { ReactNode } from 'react';
import { useTranslation } from 'react-i18next';
import { MaterialIcon } from './MaterialIcon';

interface SettingsPageHeaderProps {
  icon: string;
  title: string;
  subtitle?: string;
  onBack: () => void;
  /** Trailing action (e.g. a "new user" button) — optional, only AdminUsers.tsx
   * needs one today. Every other caller omits it and the layout is unchanged. */
  actions?: ReactNode;
}

export const SettingsPageHeader = ({ icon, title, subtitle, onBack, actions }: SettingsPageHeaderProps) => {
  const { t } = useTranslation('common');

  return (
    <div className="flex items-center justify-between gap-4 border-b border-outline-variant pb-4">
      <div className="flex items-center gap-4">
        <button
          type="button"
          onClick={onBack}
          className="p-2 rounded-full hover:bg-surface-variant transition-colors text-on-surface-variant"
          aria-label={t('back')}
        >
          <MaterialIcon name="arrow_back" />
        </button>
        <div>
          <h1 className="text-2xl font-display font-bold text-on-surface flex items-center gap-2">
            <MaterialIcon name={icon} className="text-primary" />
            {title}
          </h1>
          {subtitle && <p className="text-sm text-on-surface-variant mt-1">{subtitle}</p>}
        </div>
      </div>
      {actions && <div className="flex items-center gap-3 shrink-0">{actions}</div>}
    </div>
  );
};
