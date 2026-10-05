import { describe, it, expect } from 'vitest';
import {
  NAV_ENTRIES,
  OWNER_ADMIN_ROLES,
  NIGHT_AUDIT_ROLES,
  getSidebarSections,
  getPaletteEntries,
  getSettingsEntries,
  NESTED_ROUTES,
  NAV_GROUP_LABEL_KEYS,
  matchRoute,
  resolveAnnouncement,
  resolveCrumbs,
} from './navigation';
import commonIt from '../locales/it/common.json';
import commonEn from '../locales/en/common.json';
import settingsIt from '../locales/it/settings.json';
import settingsEn from '../locales/en/settings.json';
import adminIt from '../locales/it/admin.json';
import adminEn from '../locales/en/admin.json';
import quotationsIt from '../locales/it/quotations.json';
import quotationsEn from '../locales/en/quotations.json';
import staysIt from '../locales/it/stays.json';
import staysEn from '../locales/en/stays.json';

const BUNDLES: Record<string, Record<string, Record<string, unknown>>> = {
  it: { common: commonIt, settings: settingsIt, admin: adminIt, quotations: quotationsIt, stays: staysIt },
  en: { common: commonEn, settings: settingsEn, admin: adminEn, quotations: quotationsEn, stays: staysEn },
};

describe('NAV_ENTRIES', () => {
  it('has unique ids and paths', () => {
    expect(new Set(NAV_ENTRIES.map((e) => e.id)).size).toBe(NAV_ENTRIES.length);
    expect(new Set(NAV_ENTRIES.map((e) => e.path)).size).toBe(NAV_ENTRIES.length);
  });

  it.each(['it', 'en'])('resolves every label and announce key in its namespace (%s)', (lang) => {
    for (const entry of NAV_ENTRIES) {
      expect(BUNDLES[lang][entry.ns], `${entry.id} label ns`).toHaveProperty(entry.labelKey);
      if (typeof entry.announce === 'object') {
        expect(BUNDLES[lang][entry.announce.ns], `${entry.id} announce ns`).toHaveProperty(entry.announce.key);
      }
    }
  });
});

describe('role constants', () => {
  it('keeps night audit open to RECEPTIONIST, owner-only areas closed', () => {
    expect(NIGHT_AUDIT_ROLES).toEqual(['OWNER', 'ADMIN', 'RECEPTIONIST']);
    expect(OWNER_ADMIN_ROLES).toEqual(['OWNER', 'ADMIN']);
  });
});

const sectionIds = (role: Parameters<typeof getSidebarSections>[0]) =>
  getSidebarSections(role).map((s) => [s.group, s.entries.map((e) => e.id)] as const);

describe('getSidebarSections', () => {
  it('gives a RECEPTIONIST the dashboard, then three groups in sidebar order, night audit included', () => {
    expect(sectionIds('RECEPTIONIST')).toEqual([
      [null, ['dashboard']],
      ['front-office', ['guests', 'reservations', 'reservation-groups', 'quotations', 'calendar', 'stays']],
      ['operations', ['housekeeping', 'night-audit', 'restaurant']],
      ['revenue', ['billing', 'rooms', 'rates']],
    ]);
  });

  it.each(['ADMIN', 'OWNER'] as const)('adds owner-dashboard to revenue and an admin group for %s', (role) => {
    const sections = getSidebarSections(role);
    expect(sections.map((s) => s.group)).toEqual([null, 'front-office', 'operations', 'revenue', 'admin']);
    expect(sections.find((s) => s.group === 'revenue')?.entries.at(-1)?.id).toBe('owner-dashboard');
    expect(sections.find((s) => s.group === 'admin')?.entries.map((e) => e.id)).toEqual(['hotel-profile', 'admin-users']);
  });

  it('hides night audit, owner-dashboard and the admin group from GUEST and from no role', () => {
    for (const role of ['GUEST', undefined] as const) {
      const sections = getSidebarSections(role);
      const ids = sections.flatMap((s) => s.entries.map((e) => e.id));
      expect(ids).not.toContain('night-audit');
      expect(ids).not.toContain('owner-dashboard');
      expect(sections.map((s) => s.group)).not.toContain('admin');
    }
  });

  it('never returns an empty section', () => {
    for (const role of ['OWNER', 'ADMIN', 'RECEPTIONIST', 'GUEST', undefined] as const) {
      expect(getSidebarSections(role).every((s) => s.entries.length > 0)).toBe(true);
    }
  });
});

describe('getPaletteEntries', () => {
  it('gives ADMIN all 24 entries, privacy included', () => {
    const entries = getPaletteEntries('ADMIN');
    expect(entries).toHaveLength(24);
    expect(entries.map((e) => e.path)).toContain('/settings/privacy');
  });

  it('hides owner-only entries from a RECEPTIONIST but keeps night audit', () => {
    const paths = getPaletteEntries('RECEPTIONIST').map((e) => e.path);
    expect(paths).toContain('/night-audit');
    expect(paths).not.toContain('/settings/privacy');
    expect(paths).not.toContain('/admin/users');
    expect(paths).not.toContain('/owner-dashboard');
  });

  it('hides night audit and every owner-only entry from GUEST and from no role', () => {
    for (const role of ['GUEST', undefined] as const) {
      const paths = getPaletteEntries(role).map((e) => e.path);
      expect(paths).not.toContain('/night-audit');
      expect(paths).not.toContain('/owner-dashboard');
      expect(paths).not.toContain('/settings/privacy');
      expect(paths).not.toContain('/admin/users');
    }
  });

  it('resolves profile and password labels in the common namespace', () => {
    const byId = Object.fromEntries(NAV_ENTRIES.map((e) => [e.id, e]));
    expect(byId['settings-profile'].ns).toBe('common');
    expect(byId['settings-password'].ns).toBe('common');
  });
});

describe('resolveAnnouncement', () => {
  it.each([
    ['/guests', 'nav_guests', 'common'],
    ['/reservations', 'nav_reservations', 'common'],
    ['/reservations/new', 'new_reservation', 'common'],
    ['/reservations/edit/abc', 'edit_reservation', 'common'],
    ['/reservations/abc', 'reservation_details', 'common'],
    ['/reservations/groups', 'nav_reservation_groups', 'common'],
    ['/reservations/groups/new', 'new_group', 'common'],
    ['/reservations/groups/xyz', 'group_details', 'common'],
    ['/quotations/new', 'new_quotation', 'quotations'],
    ['/quotations/q1', 'quotation_details', 'quotations'],
    ['/quotations/q1/edit', 'edit_quotation', 'quotations'],
    ['/stays/check-in/r1', 'checkin_title', 'stays'],
    ['/stays/walk-in', 'walkin_title', 'stays'],
    ['/night-audit', 'nav_night_audit', 'common'],
    ['/owner-dashboard', 'nav_owner_dashboard', 'common'],
    ['/admin/users', 'page_title', 'admin'],
    ['/profile/hotel', 'hotel_profile_title', 'admin'],
    ['/settings', 'settings', 'settings'],
    ['/settings/privacy', 'settings_section_privacy', 'settings'],
    ['/', 'nav_dashboard', 'common'],
    ['/unknown/path', 'nav_dashboard', 'common'],
  ])('%s announces %s (%s)', (path, key, ns) => {
    expect(resolveAnnouncement(path)).toEqual({ key, ns });
  });

  it('matches whole path segments only', () => {
    expect(resolveAnnouncement('/guestsfoo')).toEqual({ key: 'nav_dashboard', ns: 'common' });
  });
});

describe('matchRoute', () => {
  it('matches a static pattern exactly', () => {
    expect(matchRoute('/reservations/new', '/reservations/new')).toBe(true);
    expect(matchRoute('/reservations/new', '/reservations/newer')).toBe(false);
  });

  it('matches :param as exactly one non-empty segment', () => {
    expect(matchRoute('/reservations/:id', '/reservations/abc')).toBe(true);
    expect(matchRoute('/reservations/:id', '/reservations/')).toBe(false);
    expect(matchRoute('/reservations/:id', '/reservations/abc/edit')).toBe(false);
    expect(matchRoute('/quotations/:id/edit', '/quotations/q1/edit')).toBe(true);
  });

  it('does not match a bare parent', () => {
    expect(matchRoute('/reservations/:id', '/reservations')).toBe(false);
  });
});

describe('NESTED_ROUTES', () => {
  it('points every route at an existing parent entry and has unique ids', () => {
    const ids = new Set(NAV_ENTRIES.map((e) => e.id));
    for (const route of NESTED_ROUTES) expect(ids, route.id).toContain(route.parent);
    expect(new Set(NESTED_ROUTES.map((r) => r.id)).size).toBe(NESTED_ROUTES.length);
  });

  it.each(['it', 'en'])('resolves every label key in its namespace (%s)', (lang) => {
    for (const route of NESTED_ROUTES) {
      expect(BUNDLES[lang][route.ns], route.id).toHaveProperty(route.labelKey);
    }
    for (const key of Object.values(NAV_GROUP_LABEL_KEYS)) {
      expect(BUNDLES[lang].common).toHaveProperty(key);
    }
  });
});

describe('resolveCrumbs', () => {
  const FO = { labelKey: 'nav_group_front_office', ns: 'common' };

  it('has no trail for the dashboard and unknown paths', () => {
    expect(resolveCrumbs('/')).toEqual([]);
    expect(resolveCrumbs('/unknown/path')).toEqual([]);
  });

  it('puts a top-level page under its sidebar group (group crumb has no link)', () => {
    expect(resolveCrumbs('/guests')).toEqual([
      FO,
      { labelKey: 'nav_guests', ns: 'common', path: '/guests' },
    ]);
  });

  it('treats reservation groups as a sibling of reservations, not a child', () => {
    expect(resolveCrumbs('/reservations/groups')).toEqual([
      FO,
      { labelKey: 'nav_reservation_groups', ns: 'common', path: '/reservations/groups' },
    ]);
  });

  it('adds the parent entry for nested routes, static before dynamic', () => {
    expect(resolveCrumbs('/reservations/new')).toEqual([
      FO,
      { labelKey: 'nav_reservations', ns: 'common', path: '/reservations' },
      { labelKey: 'new_reservation', ns: 'common', path: '/reservations/new' },
    ]);
    expect(resolveCrumbs('/reservations/abc').at(-1)).toMatchObject({ labelKey: 'reservation_details' });
    expect(resolveCrumbs('/reservations/groups/xyz')).toEqual([
      FO,
      { labelKey: 'nav_reservation_groups', ns: 'common', path: '/reservations/groups' },
      { labelKey: 'group_details', ns: 'common', path: '/reservations/groups/xyz' },
    ]);
    expect(resolveCrumbs('/quotations/q1/edit')).toEqual([
      FO,
      { labelKey: 'nav_quotations', ns: 'common', path: '/quotations' },
      { labelKey: 'edit_quotation', ns: 'quotations', path: '/quotations/q1/edit' },
    ]);
    expect(resolveCrumbs('/stays/check-in/r1').map((c) => c.labelKey)).toEqual([
      'nav_group_front_office', 'nav_stays', 'checkin_title',
    ]);
    expect(resolveCrumbs('/stays/walk-in').at(-1)).toMatchObject({ labelKey: 'walkin_title', ns: 'stays' });
  });

  it('nests settings sub-pages under Settings, which has no group', () => {
    expect(resolveCrumbs('/settings/profile')).toEqual([
      { labelKey: 'settings', ns: 'settings', path: '/settings' },
      { labelKey: 'my_profile', ns: 'common', path: '/settings/profile' },
    ]);
    expect(resolveCrumbs('/settings/city-tax')[0]).toMatchObject({ labelKey: 'settings' });
    expect(resolveCrumbs('/settings')).toEqual([{ labelKey: 'settings', ns: 'settings', path: '/settings' }]);
  });

  it('matches like the router: case-insensitive, trailing slash ignored', () => {
    expect(resolveCrumbs('/Reservations/')).toEqual(resolveCrumbs('/reservations'));
    expect(resolveCrumbs('/quotations/Q1/')).toHaveLength(3);
    expect(resolveCrumbs('/quotations/Q1/')[2].path).toBe('/quotations/Q1/');
    expect(resolveAnnouncement('/Reservations/NEW/')).toEqual({ key: 'new_reservation', ns: 'common' });
  });

  it('puts admin pages under the administration group', () => {
    expect(resolveCrumbs('/admin/users')).toEqual([
      { labelKey: 'nav_group_admin', ns: 'common' },
      { labelKey: 'settings_section_admin_users', ns: 'settings', path: '/admin/users' },
    ]);
  });
});

describe('settings route announcements', () => {
  it('announces each settings section by its own name, not as plain "Settings"', () => {
    const parent = resolveAnnouncement('/settings');
    for (const path of ['/settings/profile', '/settings/password', '/settings/appearance', '/settings/city-tax']) {
      expect(resolveAnnouncement(path), path).not.toEqual(parent);
    }
    expect(resolveAnnouncement('/settings/password').key).toBe('change_password');
  });
});

describe('getSettingsEntries', () => {
  it('gives front-desk staff only the personal sections', () => {
    expect(getSettingsEntries('RECEPTIONIST').map((e) => e.id)).toEqual([
      'settings-profile', 'settings-password', 'settings-appearance', 'settings-accessibility',
    ]);
  });

  it.each(['ADMIN', 'OWNER'] as const)('adds tourist tax, system and privacy for %s, in nav order', (role) => {
    expect(getSettingsEntries(role).map((e) => e.id)).toEqual([
      'settings-profile', 'settings-password', 'settings-appearance', 'settings-accessibility',
      'settings-city-tax', 'settings-system', 'settings-privacy',
    ]);
  });

  it('leaves out the admin pages that live elsewhere (hotel profile, users)', () => {
    const ids = getSettingsEntries('ADMIN').map((e) => e.id);
    expect(ids).not.toContain('hotel-profile');
    expect(ids).not.toContain('admin-users');
  });

  it('returns nothing sensitive without a role', () => {
    expect(getSettingsEntries(undefined).map((e) => e.id)).not.toContain('settings-system');
  });
});
