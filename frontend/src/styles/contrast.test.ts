/// <reference types="node" />
import { readFileSync } from 'node:fs';
import { describe, it, expect } from 'vitest';

// Read from disk: Vitest empties CSS modules, even with the `?raw` suffix.
const css = readFileSync('src/styles/m3-base.css', 'utf8');

// Resolves the effective token set of each theme following the real cascade of
// m3-base.css (`[data-contrast]` comes after `.dark` with equal specificity).
const readBlock = (selector: string): Record<string, string> => {
  const start = css.indexOf(`\n${selector} {`);
  if (start === -1) {
    throw new Error(`m3-base.css: block "${selector}" not found`);
  }
  const body = css.slice(start, css.indexOf('}', start));
  const tokens: Record<string, string> = {};
  for (const m of body.matchAll(/--md-([\w-]+):\s*(#[0-9A-Fa-f]{6})/g)) {
    tokens[m[1]] = m[2];
  }
  return tokens;
};

const root = readBlock(':root');
const dark = readBlock('.dark');
const hc = readBlock('[data-contrast="high"]');
const darkHc = readBlock('.dark[data-contrast="high"]');

const themes: Record<string, Record<string, string>> = {
  light: root,
  dark: { ...root, ...dark },
  'high-contrast light': { ...root, ...hc },
  'high-contrast dark': { ...root, ...dark, ...hc, ...darkHc },
};

const luminance = (hex: string): number => {
  const [r, g, b] = [1, 3, 5].map((i) => {
    const v = parseInt(hex.slice(i, i + 2), 16) / 255;
    return v <= 0.03928 ? v / 12.92 : ((v + 0.055) / 1.055) ** 2.4;
  });
  return 0.2126 * r + 0.7152 * g + 0.0722 * b;
};

const ratio = (a: string, b: string): number => {
  const [hi, lo] = [luminance(a), luminance(b)].sort((x, y) => y - x);
  return (hi + 0.05) / (lo + 0.05);
};

const surfaces = ['surface-container-low', 'surface-container-lowest'];
const texts = ['on-surface', 'on-surface-variant', 'primary', 'secondary', 'tertiary', 'error'];

describe('active navigation item contrast (WCAG AAA, 7:1)', () => {
  it.each(Object.keys(themes))('%s: on-primary-container vs primary-container', (theme) => {
    const tokens = themes[theme];
    expect(ratio(tokens['on-primary-container'], tokens['primary-container'])).toBeGreaterThanOrEqual(7);
  });
});

describe('tooltip contrast (WCAG AAA, 7:1)', () => {
  it.each(Object.keys(themes))('%s: inverse-on-surface vs inverse-surface', (theme) => {
    const tokens = themes[theme];
    expect(ratio(tokens['inverse-on-surface'], tokens['inverse-surface'])).toBeGreaterThanOrEqual(7);
  });
});

describe('page and solid-card surface contrast (WCAG AAA, 7:1)', () => {
  const cases = Object.keys(themes).flatMap((theme) =>
    surfaces.flatMap((surface) => texts.map((text) => [theme, surface, text] as const)),
  );

  it('should resolve every tested token to a 6-digit hex in every theme', () => {
    for (const tokens of Object.values(themes)) {
      for (const key of [...surfaces, ...texts]) {
        expect(tokens[key]).toMatch(/^#[0-9A-Fa-f]{6}$/);
      }
    }
  });

  it.each(cases)('%s: %s vs %s', (theme, surface, text) => {
    const tokens = themes[theme];
    expect(ratio(tokens[text], tokens[surface])).toBeGreaterThanOrEqual(7);
  });
});
