import { describe, it, expect } from 'vitest';
import baseline from './uiTextBaseline.json';

/**
 * UI wording guard (docs/UI_GUIDELINES.md, R8): a user-facing message says what happened, why
 * and what to do — it never leads with "error"/"failed"/"invalid", exposes a code, or shouts.
 *
 * Only the *vocabulary* is checked here; whether a message is actually complete and kind stays
 * a review matter (PR checklist). Existing messages that break the rule are frozen in
 * `uiTextBaseline.json`: new or reworded texts must comply, and the frozen list can only shrink
 * (fix a text, then delete its entry — a stale entry fails the second test below).
 */

const FORBIDDEN: Record<'en' | 'it', RegExp> = {
  en: /\b(error|errors|failed|failure|failures|invalid|fatal)\b/i,
  it: /\b(errore|errori|fallit[oaie]|fallimento|fallimenti|non valid[oaie]|invalid[oaie]|fatale)\b/i,
};
/** A reference code a user can neither read nor act on (ERR-1234). */
const CODE = /\b[A-Z]{2,5}-\d{2,}\b/;
const EXCLAMATION = /!/;

type Messages = Record<string, unknown>;

const files = import.meta.glob<Messages>('./{en,it}/*.json', { eager: true, import: 'default' });

/** `lang:namespace:key` of every text that uses forbidden wording. */
function findViolations(): Map<string, string> {
  const found = new Map<string, string>();
  for (const [path, messages] of Object.entries(files)) {
    const [, lang, file] = path.split('/');
    const namespace = file.replace('.json', '');
    for (const [key, value] of Object.entries(messages)) {
      if (typeof value !== 'string') continue;
      const words = FORBIDDEN[lang as 'en' | 'it'];
      if (words.test(value) || CODE.test(value) || EXCLAMATION.test(value)) {
        found.set(`${lang}:${namespace}:${key}`, value);
      }
    }
  }
  return found;
}

const violations = findViolations();
const frozen = new Set<string>(baseline.frozen);
const allowed = baseline.allowed as Record<string, string>;

describe('UI wording guard', () => {
  it('new or reworded texts avoid error/failed/invalid wording, codes and exclamation marks', () => {
    const fresh = [...violations].filter(([id]) => !frozen.has(id) && !(id in allowed));
    const report = fresh.map(([id, text]) => `  ${id}: "${text}"`).join('\n');
    expect(
      fresh,
      `Rewrite these texts as what happened / why / what to do (docs/UI_GUIDELINES.md, R8), e.g. ` +
      `"We couldn't save the room. Check the connection and try again." If the wording is ` +
      `genuinely right here (a technical page), add the key to "allowed" in ` +
      `src/locales/uiTextBaseline.json with the reason.\n${report}`,
    ).toEqual([]);
  });

  it('the frozen list only shrinks: remove entries whose text was fixed', () => {
    const stale = [...frozen].filter((id) => !violations.has(id));
    expect(
      stale,
      `These texts no longer break the rule (or were deleted): remove them from "frozen" in ` +
      `src/locales/uiTextBaseline.json:\n${stale.map((id) => `  ${id}`).join('\n')}`,
    ).toEqual([]);
  });

  it('every allowed exception has a reason and still exists', () => {
    const problems = Object.entries(allowed)
      .filter(([id, reason]) => reason.trim().length < 10 || !violations.has(id))
      .map(([id]) => id);
    expect(problems, 'An "allowed" entry needs a written reason and a text that still matches').toEqual([]);
  });

  it('the frozen list has no duplicates and stays sorted', () => {
    const sorted = [...baseline.frozen].sort();
    expect(baseline.frozen).toEqual(sorted);
    expect(new Set(baseline.frozen).size).toBe(baseline.frozen.length);
  });
});
