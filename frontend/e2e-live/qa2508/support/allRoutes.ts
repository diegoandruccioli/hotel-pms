// The full 30-route table from App.tsx:81-123, mirrored here for Blocco 2's
// exhaustive sweep. `roles` lists who the sidebar/route guard allows in;
// ADMIN and OWNER are allowed everywhere a RECEPTIONIST isn't explicitly
// excluded from (RBAC specifics are re-verified per-action in Blocco 9 —
// this file only drives "does the route load without breaking").
export type Role = 'ADMIN' | 'OWNER' | 'RECEPTIONIST';

export interface RouteSpec {
  path: string;
  roles: Role[];
  headingPattern: RegExp;
}

// Filled in at spec runtime with real fixture ids (see 02-route-sweep.spec.ts).
export const PARAMETRIC_PLACEHOLDERS = {
  reservationId: '__RESERVATION_ID__',
  quotationId: '__QUOTATION_ID__',
} as const;

const ALL: Role[] = ['ADMIN', 'OWNER', 'RECEPTIONIST'];
const ADMIN_OWNER: Role[] = ['ADMIN', 'OWNER'];

export const ROUTES: RouteSpec[] = [
  { path: '/', roles: ALL, headingPattern: /bentornato|welcome back/i },
  { path: '/guests', roles: ALL, headingPattern: /^ospiti$|^guests$/i },
  { path: '/reservations', roles: ALL, headingPattern: /prenotazioni|reservations/i },
  { path: '/reservations/new', roles: ALL, headingPattern: /nuova prenotazione|new reservation/i },
  { path: `/reservations/${PARAMETRIC_PLACEHOLDERS.reservationId}`, roles: ALL, headingPattern: /prenotazione|reservation/i },
  { path: `/reservations/edit/${PARAMETRIC_PLACEHOLDERS.reservationId}`, roles: ALL, headingPattern: /prenotazione|reservation/i },
  { path: '/quotations', roles: ALL, headingPattern: /preventivi|quotations/i },
  { path: '/quotations/new', roles: ALL, headingPattern: /nuovo preventivo|new quotation/i },
  // QuotationDetail.tsx:249-252 renders the guest's full name as the h1 (plus
  // a status chip), not any static "quotation" text — /./ just asserts a
  // non-empty heading rendered (page didn't blank/crash), since the exact
  // text is guest-data-dependent and can't be pattern-matched generically.
  { path: `/quotations/${PARAMETRIC_PLACEHOLDERS.quotationId}`, roles: ALL, headingPattern: /./ },
  { path: `/quotations/${PARAMETRIC_PLACEHOLDERS.quotationId}/edit`, roles: ALL, headingPattern: /preventivo|quotation/i },
  { path: '/stays', roles: ALL, headingPattern: /soggiorni|stays/i },
  { path: '/stays/walk-in', roles: ALL, headingPattern: /check-?in|walk-?in/i },
  { path: '/billing', roles: ALL, headingPattern: /fatturazione|billing/i },
  { path: '/restaurant', roles: ALL, headingPattern: /ristorante|restaurant/i },
  { path: '/calendar', roles: ALL, headingPattern: /calendario|calendar/i },
  { path: '/housekeeping', roles: ALL, headingPattern: /pulizie|housekeeping/i },
  { path: '/rooms', roles: ALL, headingPattern: /inventario|inventory/i }, // Rooms/index.tsx heading is rooms_title ("Inventory" in EN), not the nav_rooms label
  { path: '/rates', roles: ALL, headingPattern: /calendario tariffe|rate calendar/i },
  { path: '/settings', roles: ALL, headingPattern: /impostazioni|settings/i },
  { path: '/settings/profile', roles: ALL, headingPattern: /profilo|profile/i },
  { path: '/settings/password', roles: ALL, headingPattern: /password/i },
  { path: '/settings/accessibility', roles: ALL, headingPattern: /accessibilit|accessibility/i },
  { path: '/settings/appearance', roles: ALL, headingPattern: /aspetto|appearance/i },
  { path: '/owner-dashboard', roles: ADMIN_OWNER, headingPattern: /analytics|owner|pannello proprietario/i }, // owner_dashboard key IT = "Pannello Proprietario"
  { path: '/admin/users', roles: ADMIN_OWNER, headingPattern: /gestione utenti|user management/i },
  { path: '/profile/hotel', roles: ADMIN_OWNER, headingPattern: /struttura|hotel/i },
  { path: '/settings/system', roles: ADMIN_OWNER, headingPattern: /sistema|system/i },
  { path: '/settings/city-tax', roles: ADMIN_OWNER, headingPattern: /imposta di soggiorno|tourist tax/i }, // settings_section_city_tax EN = "Tourist Tax"
];

// /stays/check-in/:reservationId needs a CONFIRMED reservation without an
// existing stay — not safely reusable across repeated runs the way a
// read-only view route is, so it's covered explicitly in Blocco 3/7
// (check-in flow) instead of the generic sweep.
