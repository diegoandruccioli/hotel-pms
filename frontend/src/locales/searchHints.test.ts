import { describe, it, expect } from 'vitest';
import en from './en/common.json';
import it_ from './it/common.json';

/** The search field is ~30 characters wide: a longer placeholder is cut off mid-word. */
const MAX_HINT_LENGTH = 30;

const HINT_KEYS = ['invoice_search_hint', 'guests_search_hint', 'reservations_search_hint', 'stays_search_hint'] as const;

describe.each([['en', en], ['it', it_]] as const)('search placeholder hints (%s)', (_lang, messages) => {
  it.each(HINT_KEYS)('%s exists and fits the search field', (key) => {
    const text = (messages as Record<string, string>)[key];
    expect(text).toBeTruthy();
    expect(text.length).toBeLessThanOrEqual(MAX_HINT_LENGTH);
  });
});
