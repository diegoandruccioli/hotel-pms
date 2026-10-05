import type { z } from 'zod';

export const CATEGORY_MAX_LENGTH = 20;

/** First message per field of a failed zod parse, keyed by field name, for the forms' errorText props. */
export const zodFieldErrors = (error: z.ZodError): Record<string, string> => {
  const errors: Record<string, string> = {};
  for (const issue of error.issues) {
    const field = issue.path[0];
    if (typeof field === 'string' && !errors[field]) errors[field] = issue.message;
  }
  return errors;
};
