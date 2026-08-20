import type { ReactNode } from 'react';
import { MaterialIcon } from './MaterialIcon';

interface PageHeaderProps {
  icon: string;
  title: string;
  subtitle?: string;
  actions?: ReactNode;
}

/** The `h1` + icon + subtitle + action row was copy-pasted at the top of
 * every list page (e.g. Guests.tsx). Settings sub-pages already share
 * SettingsPageHeader for the same reason; this is the equivalent for the
 * rest of the app. */
export const PageHeader = ({ icon, title, subtitle, actions }: PageHeaderProps) => (
  <div className="flex flex-col sm:flex-row justify-between items-start sm:items-center gap-4">
    <div>
      <h1 className="text-2xl font-display font-bold tracking-tight text-on-surface flex items-center">
        <MaterialIcon name={icon} className="mr-2 text-primary" />
        {title}
      </h1>
      {subtitle && <p className="text-sm font-body text-on-surface-variant mt-1">{subtitle}</p>}
    </div>
    {actions && <div className="flex items-center gap-3">{actions}</div>}
  </div>
);
