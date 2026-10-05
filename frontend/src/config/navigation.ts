import type { Role } from '../types';

export const OWNER_ADMIN_ROLES: readonly Role[] = ['OWNER', 'ADMIN'];
/** Night audit is night-shift front-desk work, open to RECEPTIONIST too —
 * see GAP-26 in THREAT_MODEL.md. */
export const NIGHT_AUDIT_ROLES: readonly Role[] = ['OWNER', 'ADMIN', 'RECEPTIONIST'];

type NavNamespace = 'common' | 'settings' | 'admin' | 'quotations' | 'stays';

export type NavGroup = 'front-office' | 'operations' | 'revenue' | 'admin';

export const NAV_GROUP_LABEL_KEYS: Record<NavGroup, string> = {
  'front-office': 'nav_group_front_office',
  operations: 'nav_group_operations',
  revenue: 'nav_group_revenue',
  admin: 'nav_group_admin',
};

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
  /** Breadcrumbs only: id of the entry this one sits under (settings sub-pages). */
  parent?: string;
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
  { id: 'settings-profile', path: '/settings/profile', icon: 'person', labelKey: 'my_profile', parent: 'settings', ns: 'common', announce: true },
  { id: 'settings-password', path: '/settings/password', icon: 'lock', labelKey: 'change_password', parent: 'settings', ns: 'common', announce: true },
  { id: 'settings-accessibility', path: '/settings/accessibility', icon: 'accessibility_new', labelKey: 'settings_section_accessibility', parent: 'settings', ns: 'settings', announce: true },
  { id: 'settings-appearance', path: '/settings/appearance', icon: 'palette', labelKey: 'settings_appearance_language_title', parent: 'settings', ns: 'settings', announce: true },
  { id: 'settings-privacy', path: '/settings/privacy', icon: 'privacy_tip', labelKey: 'settings_section_privacy', parent: 'settings', ns: 'settings', announce: true, allowedRoles: OWNER_ADMIN_ROLES },
  { id: 'settings-system', path: '/settings/system', icon: 'admin_panel_settings', labelKey: 'settings_section_system', parent: 'settings', ns: 'settings', announce: true, allowedRoles: OWNER_ADMIN_ROLES },
  { id: 'hotel-profile', path: '/profile/hotel', icon: 'apartment', labelKey: 'settings_section_hotel_profile', ns: 'settings', sidebar: true, group: 'admin', announce: { key: 'hotel_profile_title', ns: 'admin' }, allowedRoles: OWNER_ADMIN_ROLES },
  { id: 'admin-users', path: '/admin/users', icon: 'manage_accounts', labelKey: 'settings_section_admin_users', ns: 'settings', sidebar: true, group: 'admin', announce: { key: 'page_title', ns: 'admin' }, allowedRoles: OWNER_ADMIN_ROLES },
  { id: 'settings-city-tax', path: '/settings/city-tax', icon: 'account_balance', labelKey: 'settings_section_city_tax', parent: 'settings', ns: 'settings', announce: true, allowedRoles: OWNER_ADMIN_ROLES },
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

/** Order of the settings sections in the settings navigation (the layout's side nav). */
const SETTINGS_NAV_ORDER = [
  'settings-profile',
  'settings-password',
  'settings-appearance',
  'settings-accessibility',
  'settings-city-tax',
  'settings-system',
  'settings-privacy',
] as const;

/** The sections of the settings area the role may open, in nav order. */
export const getSettingsEntries = (role: Role | undefined): NavEntry[] =>
  SETTINGS_NAV_ORDER
    .map((id) => NAV_ENTRIES.find((e) => e.id === id))
    .filter((e): e is NavEntry => e !== undefined && isVisibleTo(e, role));

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

/** A routed page that is not a nav entry of its own (forms, details, check-in). */
export interface NestedRoute {
  id: string;
  /** Router-style pattern; `:param` matches exactly one segment. */
  pattern: string;
  /** Id of the NAV_ENTRIES entry it lives under. */
  parent: string;
  labelKey: string;
  ns: NavNamespace;
}

/** Specific patterns first: the first match wins. */
export const NESTED_ROUTES: readonly NestedRoute[] = [
  { id: 'reservation-new', pattern: '/reservations/new', parent: 'reservations', labelKey: 'new_reservation', ns: 'common' },
  { id: 'reservation-edit', pattern: '/reservations/edit/:id', parent: 'reservations', labelKey: 'edit_reservation', ns: 'common' },
  { id: 'reservation-view', pattern: '/reservations/:id', parent: 'reservations', labelKey: 'reservation_details', ns: 'common' },
  { id: 'reservation-group-new', pattern: '/reservations/groups/new', parent: 'reservation-groups', labelKey: 'new_group', ns: 'common' },
  { id: 'reservation-group-view', pattern: '/reservations/groups/:id', parent: 'reservation-groups', labelKey: 'group_details', ns: 'common' },
  { id: 'quotation-new', pattern: '/quotations/new', parent: 'quotations', labelKey: 'new_quotation', ns: 'quotations' },
  { id: 'quotation-edit', pattern: '/quotations/:id/edit', parent: 'quotations', labelKey: 'edit_quotation', ns: 'quotations' },
  { id: 'quotation-view', pattern: '/quotations/:id', parent: 'quotations', labelKey: 'quotation_details', ns: 'quotations' },
  { id: 'check-in', pattern: '/stays/check-in/:reservationId', parent: 'stays', labelKey: 'checkin_title', ns: 'stays' },
  { id: 'walk-in', pattern: '/stays/walk-in', parent: 'stays', labelKey: 'walkin_title', ns: 'stays' },
];

/** Whole-path match of a router-style pattern; no router import, so the
 * consumers' test mocks stay untouched. */
export const matchRoute = (pattern: string, pathname: string): boolean => {
  const want = pattern.split('/');
  const got = pathname.split('/');
  return (
    want.length === got.length &&
    want.every((seg, i) => (seg.startsWith(':') ? got[i] !== '' : seg === got[i]))
  );
};

/** The router matches case-insensitively and ignores a trailing slash; so do we. */
const normalizePath = (pathname: string): string =>
  pathname.length > 1 ? pathname.replace(/\/+$/, '').toLowerCase() : pathname;

const resolveNested = (path: string): NestedRoute | undefined =>
  NAV_ENTRIES.some((e) => e.path === path)
    ? undefined
    : NESTED_ROUTES.find((r) => matchRoute(r.pattern, path));

export interface CrumbSpec {
  labelKey: string;
  ns: NavNamespace;
  /** Absent for the group crumb, which is a heading, not a page. */
  path?: string;
}

const entryById = (id: string): NavEntry | undefined => NAV_ENTRIES.find((e) => e.id === id);

const groupCrumb = (group: NavGroup | undefined): CrumbSpec[] =>
  group ? [{ labelKey: NAV_GROUP_LABEL_KEYS[group], ns: 'common' }] : [];

const entryCrumb = (e: NavEntry): CrumbSpec => ({ labelKey: e.labelKey, ns: e.ns, path: e.path });

/** Breadcrumb trail for a pathname: sidebar group (no link), parent entry,
 * then the page itself. Empty for the dashboard and unmapped paths. */
export const resolveCrumbs = (rawPathname: string): CrumbSpec[] => {
  const pathname = normalizePath(rawPathname);
  if (pathname === '/') return [];

  const nested = resolveNested(pathname);
  if (nested) {
    const parent = entryById(nested.parent);
    if (!parent) return [];
    return [
      ...groupCrumb(parent.group),
      entryCrumb(parent),
      { labelKey: nested.labelKey, ns: nested.ns, path: rawPathname },
    ];
  }

  const entry = NAV_ENTRIES.find((e) => e.path === pathname);
  if (!entry) return [];
  const parent = entry.parent ? entryById(entry.parent) : undefined;
  return [
    ...groupCrumb(entry.group ?? parent?.group),
    ...(parent ? [entryCrumb(parent)] : []),
    entryCrumb(entry),
  ];
};

/** Title to announce for a pathname: a nested route's own label, else the
 * longest announced path that is the pathname itself or a parent segment of
 * it; the dashboard for `/` and for anything unmapped. */
export const resolveAnnouncement = (rawPathname: string): { key: string; ns: string } => {
  const pathname = normalizePath(rawPathname);
  const nested = resolveNested(pathname);
  if (nested) return { key: nested.labelKey, ns: nested.ns };
  const match = ANNOUNCED.find((e) => pathname === e.path || pathname.startsWith(`${e.path}/`));
  return match?.title ?? DASHBOARD_TITLE;
};
