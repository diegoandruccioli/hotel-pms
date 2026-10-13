import type { z } from 'zod';

export const CATEGORY_MAX_LENGTH = 20;

interface BackfillLine {
  amount: number;
  charged: boolean;
  skipReason?: string | null;
}

/** What a confirmed backfill would charge: stays not yet charged and not skipped (a skipped one,
 * e.g. on a closed invoice, is left alone). The response total also counts those, so it can't be shown. */
export const pendingBackfill = (lines: readonly BackfillLine[]): { count: number; total: number } => {
  const pending = lines.filter((l) => !l.charged && !l.skipReason);
  return { count: pending.length, total: pending.reduce((sum, l) => sum + l.amount, 0) };
};

/** First message per field of a failed zod parse, keyed by field name, for the forms' errorText props. */
export const zodFieldErrors = (error: z.ZodError): Record<string, string> => {
  const errors: Record<string, string> = {};
  for (const issue of error.issues) {
    const field = issue.path[0];
    if (typeof field === 'string' && !errors[field]) errors[field] = issue.message;
  }
  return errors;
};
