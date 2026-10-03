import type { Role } from '../types';

export const OWNER_ADMIN_ROLES: readonly Role[] = ['OWNER', 'ADMIN'];
/** Night audit is night-shift front-desk work, open to RECEPTIONIST too —
 * see GAP-26 in THREAT_MODEL.md. */
export const NIGHT_AUDIT_ROLES: readonly Role[] = ['OWNER', 'ADMIN', 'RECEPTIONIST'];

type NavNamespace = 'common' | 'settings' | 'admin';

export type NavGroup = 'front-office' | 'operations' | 'revenue' | 'admin';

/** Sidebar section order. */
const NAV_GROUP_ORDER: readonly NavGroup[] = ['front-office', 'operations', 'revenue', 'admin'];

export interface NavEntry {
  id: string;
  path: string;
  icon: string;
  labelKey: string;
  ns: 'common' | 'settings';
  allowedRoles?: readonly Role[];
  /** Shown in the sidebar (desktop sidebar and mobile drawer). */
  sidebar?: true;
  /** Sidebar only: section the entry sits under. Entries without one (the
   * dashboard) render first, under no heading. */
  group?: NavGroup;
  /** Title announced on navigation: `true` reuses the label, an object overrides
   * it. Entries without it are covered by the nearest announced parent path. */
  announce?: true | { key: string; ns: NavNamespace };
}

/** Single source of truth for every static, directly-navigable route: the
 * sidebar (in the order listed here), the command palette and the route
 * announcer all derive from it. Pure data — no router, store or React — so
 * the consumers' test mocks stay untouched. */
export const NAV_ENTRIES: readonly NavEntry[] = [
  { id: 'dashboard', path: '/', icon: 'dashboard', labelKey: 'nav_dashboard', ns: 'common', sidebar: true },
  { id: 'guests', path: '/guests', icon: 'group', labelKey: 'nav_guests', ns: 'common', sidebar: true, group: 'front-office', announce: true },
  { id: 'reservations', path: '/reservations', icon: 'event', labelKey: 'nav_reservations', ns: 'common', sidebar: true, group: 'front-office', announce: true },
  { id: 'reservation-groups', path: '/reservations/groups', icon: 'groups', labelKey: 'nav_reservation_groups', ns: 'common', sidebar: true, group: 'front-office', announce: true },
  { id: 'quotations', path: '/quotations', icon: 'request_quote', labelKey: 'nav_quotations', ns: 'common', sidebar: true, group: 'front-office', announce: true },
  { id: 'calendar', path: '/calendar', icon: 'date_range', labelKey: 'nav_calendar', ns: 'common', sidebar: true, group: 'front-office', announce: true },
  { id: 'stays', path: '/stays', icon: 'hotel', labelKey: 'nav_stays', ns: 'common', sidebar: true, group: 'front-office', announce: true },
  { id: 'housekeeping', path: '/housekeeping', icon: 'cleaning_services', labelKey: 'nav_housekeeping', ns: 'common', sidebar: true, group: 'operations', announce: true },
  { id: 'night-audit', path: '/night-audit', icon: 'fact_check', labelKey: 'nav_night_audit', ns: 'common', sidebar: true, group: 'operations', announce: true, allowedRoles: NIGHT_AUDIT_ROLES },
  { id: 'billing', path: '/billing', icon: 'receipt_long', labelKey: 'nav_billing', ns: 'common', sidebar: true, group: 'revenue', announce: true },
  { id: 'restaurant', path: '/restaurant', icon: 'restaurant', labelKey: 'nav_restaurant', ns: 'common', sidebar: true, group: 'operations', announce: true },
  { id: 'rooms', path: '/rooms', icon: 'meeting_room', labelKey: 'nav_rooms', ns: 'common', sidebar: true, group: 'revenue', announce: true },
  { id: 'rates', path: '/rates', icon: 'payments', labelKey: 'nav_rates', ns: 'common', sidebar: true, group: 'revenue', announce: true },
  { id: 'owner-dashboard', path: '/owner-dashboard', icon: 'bar_chart', labelKey: 'nav_owner_dashboard', ns: 'common', sidebar: true, group: 'revenue', announce: true, allowedRoles: OWNER_ADMIN_ROLES },
  { id: 'settings', path: '/settings', icon: 'settings', labelKey: 'settings', ns: 'settings', announce: true },
  { id: 'settings-profile', path: '/settings/profile', icon: 'person', labelKey: 'my_profile', ns: 'common' },
  { id: 'settings-password', path: '/settings/password', icon: 'lock', labelKey: 'change_password', ns: 'common' },
  { id: 'settings-accessibility', path: '/settings/accessibility', icon: 'accessibility_new', labelKey: 'settings_section_accessibility', ns: 'settings' },
  { id: 'settings-appearance', path: '/settings/appearance', icon: 'palette', labelKey: 'settings_appearance_language_title', ns: 'settings' },
  { id: 'settings-privacy', path: '/settings/privacy', icon: 'privacy_tip', labelKey: 'settings_section_privacy', ns: 'settings', allowedRoles: OWNER_ADMIN_ROLES },
  { id: 'settings-system', path: '/settings/system', icon: 'admin_panel_settings', labelKey: 'settings_section_system', ns: 'settings', allowedRoles: OWNER_ADMIN_ROLES },
  { id: 'hotel-profile', path: '/profile/hotel', icon: 'apartment', labelKey: 'settings_section_hotel_profile', ns: 'settings', sidebar: true, group: 'admin', announce: { key: 'hotel_profile_title', ns: 'admin' }, allowedRoles: OWNER_ADMIN_ROLES },
  { id: 'admin-users', path: '/admin/users', icon: 'manage_accounts', labelKey: 'settings_section_admin_users', ns: 'settings', sidebar: true, group: 'admin', announce: { key: 'page_title', ns: 'admin' }, allowedRoles: OWNER_ADMIN_ROLES },
  { id: 'settings-city-tax', path: '/settings/city-tax', icon: 'account_balance', labelKey: 'settings_section_city_tax', ns: 'settings', allowedRoles: OWNER_ADMIN_ROLES },
];

const isVisibleTo = (entry: NavEntry, role: Role | undefined): boolean =>
  !entry.allowedRoles || (role !== undefined && entry.allowedRoles.includes(role));

export interface SidebarSection {
  /** `null` for the ungrouped entries (dashboard), rendered without a heading. */
  group: NavGroup | null;
  entries: NavEntry[];
}

/** Sidebar content for a role: the ungrouped entries first, then each group in
 * `NAV_GROUP_ORDER`; groups left empty by the role filter are dropped. */
export const getSidebarSections = (role: Role | undefined): SidebarSection[] => {
  const visible = NAV_ENTRIES.filter((e) => e.sidebar && isVisibleTo(e, role));
  return [null, ...NAV_GROUP_ORDER]
    .map((group) => ({ group, entries: visible.filter((e) => (e.group ?? null) === group) }))
    .filter((section) => section.entries.length > 0);
};

export const getPaletteEntries = (role: Role | undefined): NavEntry[] =>
  NAV_ENTRIES.filter((e) => isVisibleTo(e, role));

const DASHBOARD_TITLE = { key: 'nav_dashboard', ns: 'common' } as const;

const ANNOUNCED = NAV_ENTRIES
  .flatMap((e) => {
    if (e.path === '/' || !e.announce) return [];
    const title = e.announce === true ? { key: e.labelKey, ns: e.ns } : e.announce;
    return [{ path: e.path, title }];
  })
  .sort((a, b) => b.path.length - a.path.length);

/** Title to announce for a pathname: the longest announced path that is the
 * pathname itself or a parent segment of it; the dashboard for `/` and for
 * anything unmapped. */
export const resolveAnnouncement = (pathname: string): { key: string; ns: string } => {
  const match = ANNOUNCED.find((e) => pathname === e.path || pathname.startsWith(`${e.path}/`));
  return match?.title ?? DASHBOARD_TITLE;
};
