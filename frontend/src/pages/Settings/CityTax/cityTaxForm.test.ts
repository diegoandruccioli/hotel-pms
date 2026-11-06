import { describe, it, expect } from 'vitest';
import { z } from 'zod';
import { pendingBackfill, zodFieldErrors } from './cityTaxForm';

const line = (amount: number, charged: boolean, skipReason: string | null) =>
  ({ stayId: 's' + amount, checkInDate: '2026-05-01', amount, charged, skipReason });

describe('pendingBackfill', () => {
  it('counts and sums only the stays that would actually be charged', () => {
    const lines = [
      line(10, false, null),
      line(20, false, null),
      line(500, false, 'INVOICE_NOT_OPEN'),
      line(40, true, null),
    ];
    expect(pendingBackfill(lines)).toEqual({ count: 2, total: 30 });
  });

  it('is empty when nothing is left to charge', () => {
    expect(pendingBackfill([line(5, true, null)])).toEqual({ count: 0, total: 0 });
  });
});

describe('zodFieldErrors', () => {
  it('keeps the first message per field', () => {
    const schema = z.object({ name: z.string().min(2, 'too short').regex(/^a/, 'must start with a'), age: z.number('nan') });
    const result = schema.safeParse({ name: 'b', age: 'x' });
    if (result.success) throw new Error('expected failure');
    expect(zodFieldErrors(result.error)).toEqual({ name: 'too short', age: 'nan' });
  });
});
