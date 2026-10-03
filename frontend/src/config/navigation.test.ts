import { describe, it, expect } from 'vitest';
import {
  NAV_ENTRIES,
  OWNER_ADMIN_ROLES,
  NIGHT_AUDIT_ROLES,
  getSidebarSections,
  getPaletteEntries,
  resolveAnnouncement,
} from './navigation';
import commonIt from '../locales/it/common.json';
import commonEn from '../locales/en/common.json';
import settingsIt from '../locales/it/settings.json';
import settingsEn from '../locales/en/settings.json';
import adminIt from '../locales/it/admin.json';
import adminEn from '../locales/en/admin.json';

const BUNDLES: Record<string, Record<string, Record<string, unknown>>> = {
  it: { common: commonIt, settings: settingsIt, admin: adminIt },
  en: { common: commonEn, settings: settingsEn, admin: adminEn },
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
    ['/reservations/new', 'nav_reservations', 'common'],
    ['/reservations/edit/abc', 'nav_reservations', 'common'],
    ['/reservations/groups', 'nav_reservation_groups', 'common'],
    ['/reservations/groups/xyz', 'nav_reservation_groups', 'common'],
    ['/night-audit', 'nav_night_audit', 'common'],
    ['/owner-dashboard', 'nav_owner_dashboard', 'common'],
    ['/admin/users', 'page_title', 'admin'],
    ['/profile/hotel', 'hotel_profile_title', 'admin'],
    ['/settings', 'settings', 'settings'],
    ['/settings/privacy', 'settings', 'settings'],
    ['/', 'nav_dashboard', 'common'],
    ['/unknown/path', 'nav_dashboard', 'common'],
  ])('%s announces %s (%s)', (path, key, ns) => {
    expect(resolveAnnouncement(path)).toEqual({ key, ns });
  });

  it('matches whole path segments only', () => {
    expect(resolveAnnouncement('/guestsfoo')).toEqual({ key: 'nav_dashboard', ns: 'common' });
  });
});
