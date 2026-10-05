import { describe, it, expect } from 'vitest';
import {
  toneChipClasses,
  toneDotClasses,
  toneOutlineButtonClasses,
  toneSolidClasses,
  toneSolidTokens,
} from './toneStyles';
import type { StatusTone } from './domainStatus';

const TONES: StatusTone[] = ['success', 'warning', 'error', 'info', 'neutral'];
const ROLE_OF: Record<Exclude<StatusTone, 'neutral'>, string> = {
  success: 'tertiary',
  warning: 'secondary',
  error: 'error',
  info: 'primary',
};

describe('toneStyles', () => {
  it.each([
    ['chip', toneChipClasses],
    ['outline button', toneOutlineButtonClasses],
    ['solid', toneSolidClasses],
    ['dot', toneDotClasses],
    ['solid tokens', toneSolidTokens],
  ] as const)('covers every tone in the %s variant', (_name, map) => {
    expect(Object.keys(map).sort()).toEqual([...TONES].sort());
  });

  it('keeps the semantic colour role consistent across class variants', () => {
    for (const tone of ['success', 'warning', 'error', 'info'] as const) {
      const role = ROLE_OF[tone];
      expect(toneChipClasses[tone]).toContain(`${role}-container`);
      expect(toneOutlineButtonClasses[tone]).toContain(`border-${role}`);
      expect(toneSolidClasses[tone]).toBe(`bg-${role} text-on-${role}`);
      expect(toneDotClasses[tone]).toBe(`bg-${role}`);
    }
  });

  it('uses the same colour roles for the calendar CSS tokens as for the solid classes', () => {
    for (const tone of ['success', 'warning', 'error', 'info'] as const) {
      const role = ROLE_OF[tone];
      expect(toneSolidTokens[tone]).toEqual({
        bg: `--md-${role}`,
        text: `--md-on-${role}`,
        border: `--md-${role}`,
      });
    }
  });

  it('renders the neutral solid as a tonal bar with an outline, never text on --md-outline', () => {
    expect(toneSolidClasses.neutral).toBe('bg-surface-container-highest text-on-surface border border-outline');
    expect(toneSolidTokens.neutral).toEqual({
      bg: '--md-surface-container-highest',
      text: '--md-on-surface',
      border: '--md-outline',
    });
  });
});
