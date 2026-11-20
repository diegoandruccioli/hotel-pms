import { describe, expect, it } from 'vitest';
import errorsEn from './en/errors.json';
import errorsIt from './it/errors.json';

/**
 * Codes the backend answers with (`detail: "CODE"`) on a 4xx that the user can act on.
 * `services/api.ts` swaps the code for `errors:<CODE>` when that text exists; without it the
 * operator would read the raw code. Keep in step with the exceptions thrown by the services.
 */
const USER_FACING_CODES = [
  'QUOTATION_NOT_FOUND', 'QUOTATION_NOT_EDITABLE', 'QUOTATION_EXPIRED', 'QUOTATION_DECLINED',
  'QUOTATION_ALREADY_ACCEPTED', 'QUOTATION_OPTION_NOT_FOUND', 'QUOTATION_OPTION_REQUIRED',
  'RATE_SEASON_NOT_FOUND', 'RATE_SEASON_OVERLAP', 'RATE_SEASON_CONCURRENT_MODIFICATION',
  'NIGHT_AUDIT_ALREADY_CLOSED', 'NIGHT_AUDIT_BUSINESS_DATE_IN_FUTURE',
  'RESERVATION_LINE_ITEM_NOT_FOUND', 'RESERVATION_NO_SHOW_BEFORE_CHECKIN_DATE', 'RESERVATION_NO_SHOW_HAS_STAY',
  'EXTENSION_MUST_BE_LATER_THAN_CURRENT_CHECKOUT', 'STAY_NOT_CHECKED_IN', 'STAY_GUEST_NOT_FOUND',
  'STAY_GUEST_IS_PRIMARY', 'STAY_GUEST_ALREADY_SENT', 'STAY_GUEST_DEPARTURE_BEFORE_ARRIVAL',
  'HOTEL_FISCAL_IDENTITY_INCOMPLETE', 'EXPORT_PERIOD_INVALID', 'MASTER_FOLIO_NOT_FOUND_FOR_GROUP',
  'CITY_TAX_ASSESSMENT_NOT_FOUND', 'INVALID_TIMEZONE', 'DAY_SHEET_TREND_DAYS_OUT_OF_RANGE',
];

const en = errorsEn as Record<string, string>;
const it_ = errorsIt as Record<string, string>;

describe('backend error codes shown to users', () => {
  it.each(USER_FACING_CODES)('%s has a sentence in English and in Italian', (code) => {
    expect(en[code], `missing en text for ${code}`).toBeTruthy();
    expect(it_[code], `missing it text for ${code}`).toBeTruthy();
    expect(en[code]).not.toBe(code);
    expect(it_[code]).not.toBe(code);
  });

  it('English and Italian define the same codes', () => {
    expect(Object.keys(it_).sort()).toEqual(Object.keys(en).sort());
  });
});
