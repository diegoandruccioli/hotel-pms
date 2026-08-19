import { useEffect, useRef } from 'react';
import { useLocation } from 'react-router-dom';
import { useTranslation } from 'react-i18next';

interface RouteEntry {
  prefix: string;
  key: string;
  ns: string;
}

// Ordered top-level-segment table for the 22 routes under MainLayout
// (see App.tsx). Sub-routes of a section (e.g. /reservations/new,
// /reservations/:id) intentionally announce the section name rather than a
// bespoke per-view title — that matches what the sidebar/nav already calls
// that area, and keeps this table from having to track every nested form
// route separately. '/' is handled as an exact match below, not a prefix,
// since every path starts with '/'.
const ROUTES: RouteEntry[] = [
  { prefix: '/guests', key: 'nav_guests', ns: 'common' },
  { prefix: '/reservations', key: 'nav_reservations', ns: 'common' },
  { prefix: '/quotations', key: 'nav_quotations', ns: 'common' },
  { prefix: '/stays', key: 'nav_stays', ns: 'common' },
  { prefix: '/billing', key: 'nav_billing', ns: 'common' },
  { prefix: '/restaurant', key: 'nav_restaurant', ns: 'common' },
  { prefix: '/calendar', key: 'nav_calendar', ns: 'common' },
  { prefix: '/housekeeping', key: 'nav_housekeeping', ns: 'common' },
  { prefix: '/rooms', key: 'nav_rooms', ns: 'common' },
  { prefix: '/rates', key: 'nav_rates', ns: 'common' },
  { prefix: '/owner-dashboard', key: 'nav_owner_dashboard', ns: 'common' },
  { prefix: '/admin/users', key: 'page_title', ns: 'admin' },
  { prefix: '/profile/hotel', key: 'hotel_profile_title', ns: 'admin' },
  { prefix: '/settings', key: 'settings', ns: 'settings' },
];

const DASHBOARD_ENTRY: RouteEntry = { prefix: '/', key: 'nav_dashboard', ns: 'common' };

function resolveRouteEntry(pathname: string): RouteEntry {
  if (pathname === '/') return DASHBOARD_ENTRY;
  return ROUTES.find((r) => pathname.startsWith(r.prefix)) ?? DASHBOARD_ENTRY;
}

/**
 * Announces navigation to assistive technology. A client-side route change
 * doesn't trigger a full page load, so a screen reader has nothing telling
 * it the view changed — this was the app's only unannounced-navigation gap
 * (docs/COMPLIANCE_AUDIT_2026-08.md claims WCAG 2.2 AA; before this, the
 * single other aria-live in the whole app was in OrderFormModal). Mounted
 * once in MainLayout, above the routed <Outlet>.
 */
export const RouteAnnouncer = () => {
  const location = useLocation();
  const { t } = useTranslation();
  const liveRef = useRef<HTMLDivElement>(null);

  useEffect(() => {
    const entry = resolveRouteEntry(location.pathname);
    const label = t(entry.key, { ns: entry.ns });
    document.title = `${label} · Hotel PMS`;

    if (liveRef.current) {
      liveRef.current.textContent = label;
    }

    // Move focus to the main landmark so keyboard and screen-reader users
    // land somewhere meaningful after navigating, instead of focus staying
    // on the (now stale) nav link/button that triggered the navigation.
    // The skip link already targets this same element (MainLayout.tsx).
    document.getElementById('main-content')?.focus();
  }, [location.pathname, t]);

  return <div aria-live="polite" aria-atomic="true" className="sr-only" ref={liveRef} />;
};
