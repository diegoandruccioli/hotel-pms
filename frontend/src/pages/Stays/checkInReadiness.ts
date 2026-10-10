import type { CityTaxUnassessedReason, TravellerType } from '../../types';
import { todayIsoDate } from '../../utils';
import { getGuestMissingFields, hasExpiredDocument, TYPES_WITHOUT_DOC } from './stayGuestFieldHelpers';
import type { IdentifiableGuest } from './stayGuestFieldHelpers';

export type ChecklistTone = 'ok' | 'warn' | 'pending';

export interface ChecklistItem {
  id: string;
  tone: ChecklistTone;
  /** i18n key (stays namespace). */
  titleKey: string;
  titleParams?: Record<string, unknown>;
  detailKey?: string;
  detailParams?: Record<string, unknown>;
  /** Name of the `detailParams` entry holding an ISO date the UI must format for display. */
  dateParam?: string;
}

interface ChecklistInput {
  guests: IdentifiableGuest[];
  /** `undefined` while the configuration check is unresolved, `null` when the tax is configured. */
  cityTaxWarning: CityTaxUnassessedReason | null | undefined;
  /** Reservation check-out (YYYY-MM-DD); enables the "document expires during the stay" warning. */
  checkOutDate?: string;
}

const cityTaxItem = (reason: ChecklistInput['cityTaxWarning']): ChecklistItem => {
  const base = { id: 'city-tax', titleKey: 'checklist_city_tax' };
  if (reason === undefined) return { ...base, tone: 'pending', detailKey: 'checklist_city_tax_unknown' };
  if (reason === null) return { ...base, tone: 'ok', detailKey: 'checklist_city_tax_configured' };
  if (reason === 'NOT_APPLICABLE') return { ...base, tone: 'ok', detailKey: 'checklist_city_tax_not_applicable' };
  return { ...base, tone: 'warn', detailKey: 'checklist_city_tax_not_configured' };
};

const guestName = (g: IdentifiableGuest, index: number): string =>
  `${g.firstName} ${g.lastName}`.trim() || `#${index + 1}`;

/**
 * Check-in readiness list: one entry per guest (complete / N fields missing), document-validity
 * warnings, the tourist-tax configuration and the Alloggiati submission. Pure — the form owns the
 * state, this only derives what the operator still has to do.
 */
export const buildCheckInChecklist = ({ guests, cityTaxWarning, checkOutDate }: ChecklistInput): ChecklistItem[] => {
  const items: ChecklistItem[] = [];
  const today = todayIsoDate();
  let allClear = guests.some((g) => g.isPrimaryGuest);

  if (!allClear) {
    items.push({ id: 'primary', tone: 'warn', titleKey: 'checklist_no_primary' });
  }

  guests.forEach((g, index) => {
    const missing = getGuestMissingFields(g).length;
    const expired = hasExpiredDocument(g);
    if (missing > 0 || expired) allClear = false;
    items.push({
      id: `guest-${index}`,
      tone: missing > 0 || expired ? 'warn' : 'ok',
      titleKey: g.isPrimaryGuest ? 'checklist_guest_primary' : 'checklist_guest',
      titleParams: { name: guestName(g, index) },
      detailKey: missing > 0 ? 'checklist_status_missing' : expired ? 'checklist_doc_expired_short' : 'checklist_status_complete',
      detailParams: missing > 0 ? { count: missing } : undefined,
    });
  });

  guests.forEach((g, index) => {
    const hasDoc = !TYPES_WITHOUT_DOC.includes(g.travellerType as TravellerType);
    const expiry = g.documentExpiryDate;
    if (!hasDoc || !expiry) return;
    const expired = hasExpiredDocument(g);
    const expiresInStay = !expired && !!checkOutDate && expiry < checkOutDate && expiry >= today;
    if (!expired && !expiresInStay) return;
    items.push({
      id: `doc-${index}`,
      tone: 'warn',
      titleKey: 'checklist_document',
      titleParams: { name: guestName(g, index) },
      detailKey: expired ? 'checklist_doc_expired' : 'checklist_doc_expires_in_stay',
      detailParams: { date: expiry },
      dateParam: 'date',
    });
  });

  items.push(cityTaxItem(cityTaxWarning));
  items.push({
    id: 'alloggiati',
    tone: allClear ? 'ok' : 'pending',
    titleKey: 'checklist_alloggiati',
    detailKey: allClear ? 'checklist_alloggiati_ready' : 'checklist_alloggiati_waiting',
  });
  return items;
};
