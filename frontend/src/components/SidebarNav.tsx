import { memo, useId } from 'react';
import { NavLink } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import { MaterialIcon } from './MaterialIcon';
import { cn } from '../utils';
import type { NavGroup, SidebarSection } from '../config/navigation';

// BUG-7 (docs/LIVE_E2E_AUDIT_2026-07.md): the sidebar had no focus-visible
// ring at all, unlike the skip-link — same recipe as M3Button/M3TableActionLink
// so focus indicators are consistent across the whole app.
const NAV_ITEM_FOCUS_RING =
  'focus-visible:outline-hidden focus-visible:ring-2 focus-visible:ring-primary focus-visible:ring-offset-2';

const GROUP_LABEL_KEYS: Record<NavGroup, string> = {
  'front-office': 'nav_group_front_office',
  operations: 'nav_group_operations',
  revenue: 'nav_group_revenue',
  admin: 'nav_group_admin',
};

const getNavItemClasses = ({ isActive }: { isActive: boolean }) =>
  cn(
    'flex items-center gap-3 px-4 py-2.5 rounded-shape-full text-sm font-medium font-body transition-colors',
    NAV_ITEM_FOCUS_RING,
    isActive
      ? 'bg-primary-container text-on-primary-container'
      : 'text-on-surface-variant hover:bg-surface-container-highest hover:text-on-surface'
  );

interface SidebarNavProps {
  sections: SidebarSection[];
  /** Called after a link is followed (the mobile drawer closes itself). */
  onNavigate?: () => void;
  /** Landmark name; the drawer overrides it so both copies stay distinguishable. */
  ariaLabel?: string;
}

/** Grouped main navigation, shared by the desktop sidebar and the mobile drawer. */
export const SidebarNav = memo(({ sections, onNavigate, ariaLabel }: SidebarNavProps) => {
  const { t } = useTranslation('common');
  // Per-instance: the desktop sidebar and the drawer render side by side.
  const uid = useId();

  return (
    <nav aria-label={ariaLabel ?? t('nav_main')} className="flex flex-col gap-4 px-3 pb-4">
      {sections.map(({ group, entries }) => {
        const headingId = group ? `${uid}-${group}` : undefined;
        return (
          <div
            key={group ?? 'ungrouped'}
            role={group ? 'group' : undefined}
            aria-labelledby={headingId}
            className="flex flex-col gap-0.5"
          >
            {group && (
              <span
                id={headingId}
                className="px-4 pb-1 text-xs font-semibold font-body uppercase tracking-wide text-on-surface-variant"
              >
                {t(GROUP_LABEL_KEYS[group])}
              </span>
            )}
            {entries.map((item) => (
              <NavLink
                key={item.id}
                to={item.path}
                end={item.path === '/'}
                onClick={onNavigate}
                className={getNavItemClasses}
              >
                {({ isActive }) => (
                  <>
                    <MaterialIcon name={item.icon} filled={isActive} size={24} />
                    {t(item.labelKey, { ns: item.ns })}
                  </>
                )}
              </NavLink>
            ))}
          </div>
        );
      })}
    </nav>
  );
});

SidebarNav.displayName = 'SidebarNav';
