import type { ReactNode } from 'react';
import { PageHeader } from './PageHeader';

interface SettingsPageHeaderProps {
  icon: string;
  title: string;
  subtitle?: string;
  onBack: () => void;
  /** Trailing action (e.g. a "new user" button) — optional, only AdminUsers.tsx
   * needs one today. Every other caller omits it and the layout is unchanged. */
  actions?: ReactNode;
}

/** Settings sub-pages always have a way back and a divider under the title:
 * `PageHeader` with `onBack` and `bordered` pre-set. */
export const SettingsPageHeader = ({ icon, title, subtitle, onBack, actions }: SettingsPageHeaderProps) => (
  <PageHeader icon={icon} title={title} subtitle={subtitle} onBack={onBack} actions={actions} bordered />
);
