// Shared "wrong data" dataset for the 2026-08-25 exhaustive round — reused
// across every form spec instead of ad-hoc strings per test, so a boundary
// case fixed for one form doesn't get silently skipped in another.

export interface NegativeCase {
  label: string;
  value: string;
  /** What we expect the field/form to do — used in assertion messages, not enforced generically. */
  expect: string;
}

export const TEXT_CASES: NegativeCase[] = [
  { label: 'empty', value: '', expect: 'required-field error' },
  { label: 'whitespace-only', value: '   ', expect: 'treated as empty, required-field error' },
  { label: 'single-char', value: 'a', expect: 'below min length if one is enforced' },
  { label: 'very-long-10000', value: 'a'.repeat(10_000), expect: 'rejected or truncated, no layout break, no 500' },
  { label: 'script-tag', value: '<script>alert(1)</script>', expect: 'rendered as inert text everywhere it is echoed back, never executed' },
  { label: 'sql-injection', value: "' OR 1=1 --", expect: 'treated as literal string, no query error' },
  { label: 'path-traversal', value: '../../etc/passwd', expect: 'treated as literal string' },
  { label: 'unicode-mixed', value: '🏨 مرحبا ​ test', expect: 'accepted or rejected consistently, no mojibake, no layout break' },
];

export const NUMBER_CASES: NegativeCase[] = [
  { label: 'negative', value: '-1', expect: 'rejected where a positive/zero value is required' },
  { label: 'zero', value: '0', expect: 'accepted or rejected per field semantics (e.g. quantity 0 should block order submit)' },
  { label: 'huge', value: '999999999', expect: 'rejected or handled without overflow/NaN display' },
  { label: 'comma-decimal', value: '1,5', expect: 'either normalized to 1.5 or rejected — never silently read as 15' },
  { label: 'dot-decimal', value: '1.5', expect: 'accepted where decimals are valid' },
];

export const DATE_CASES: NegativeCase[] = [
  { label: 'checkout-before-checkin', value: '', expect: 'blocked with an inline cross-field error, not a 500' },
  { label: 'past-date', value: '2020-01-01', expect: 'rejected where only future/today dates make sense' },
  { label: 'far-future-10y', value: '2036-01-01', expect: 'accepted or rejected consistently, no crash' },
  { label: 'invalid-calendar-date', value: '2027-02-29', expect: 'rejected — 2027 is not a leap year' },
];

// Tarati sui vincoli reali lato server (HotelSettingsRequest.java) — vedi
// frontdesk-service/.../hotel/dto/HotelSettingsRequest.java.
export const FISCAL_CASES = {
  vatNumber: [
    { label: 'too-short', value: '123', expect: 'rejected: vatNumber must be 11 digits' },
    { label: 'too-long', value: '123456789012', expect: 'rejected: vatNumber must be 11 digits' },
    { label: 'letters', value: 'ABCDEFGHIJK', expect: 'rejected: vatNumber must be 11 digits' },
    { label: 'valid-11-digits', value: '12345678903', expect: 'accepted by the @Pattern (checksum not validated server-side)' },
  ] as NegativeCase[],
  cap: [
    { label: 'too-short', value: '123', expect: 'rejected: CAP must be 5 digits' },
    { label: 'letters', value: 'ABCDE', expect: 'rejected: CAP must be 5 digits' },
  ] as NegativeCase[],
  provincia: [
    { label: 'too-long', value: 'ROM', expect: 'rejected: Provincia must be 2 letters' },
    { label: 'digits', value: '12', expect: 'rejected: Provincia must be 2 letters' },
  ] as NegativeCase[],
  fiscalCode: [
    { label: 'obviously-malformed', value: 'NOTAFISCALCODE!!', expect: 'no @Pattern on the server for this field — check whether client-only validation is the only gate (same shape as the vatNumber gap fixed 2026-08-24)' },
  ] as NegativeCase[],
  logoUrl: [
    { label: 'not-a-url', value: 'not a url', expect: 'rejected: logoUrl must be a valid http(s) URL' },
    { label: 'javascript-scheme', value: 'javascript:alert(1)', expect: 'rejected by the http(s)-only @Pattern' },
  ] as NegativeCase[],
  sdiCode: [
    { label: 'too-long', value: 'X'.repeat(20), expect: 'rejected or truncated per field length' },
  ] as NegativeCase[],
  pecEmail: [
    { label: 'not-an-email', value: 'not-an-email', expect: 'rejected as invalid email format' },
  ] as NegativeCase[],
};

// CityTaxRateRequest.java boundaries.
export const CITY_TAX_CASES = {
  amountPerNight: [
    { label: 'negative', value: '-1', expect: 'rejected: @PositiveOrZero' },
  ] as NegativeCase[],
  exemptUnderAge: [
    { label: 'over-120', value: '121', expect: 'rejected: @Max(120)' },
    { label: 'negative', value: '-1', expect: 'rejected: @Min(0)' },
  ] as NegativeCase[],
  category: [
    { label: 'over-20-chars', value: 'X'.repeat(21), expect: 'rejected: @Size(max=20)' },
    { label: 'blank', value: '', expect: 'rejected: @NotBlank' },
  ] as NegativeCase[],
  maxTaxableNights: [
    { label: 'zero-or-negative', value: '0', expect: 'rejected: @Positive' },
  ] as NegativeCase[],
};

export const LOGIN_CASES: NegativeCase[] = [
  { label: 'empty-both', value: '', expect: 'submit blocked or generic invalid-credentials error, never which-field-was-wrong' },
  { label: 'wrong-password', value: 'DefinitelyWrongPassword123!!', expect: 'generic 401, same message as unknown username' },
  { label: 'sql-injection-username', value: "admin' --", expect: 'treated as literal username, 401' },
  { label: 'brute-force-6th-attempt', value: '', expect: '429 after 5 attempts/burst 10, generic message, no lockout leak of which field' },
];
