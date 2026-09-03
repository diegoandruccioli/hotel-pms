import { describe, it, expect, afterEach } from 'vitest';
import { resolveDesignToken } from './designTokens';

describe('resolveDesignToken', () => {
  afterEach(() => {
    document.documentElement.style.removeProperty('--test-token');
  });

  it('reads the live computed value of a CSS custom property on the root element', () => {
    document.documentElement.style.setProperty('--test-token', '#1a3a5c');

    expect(resolveDesignToken('--test-token')).toBe('#1a3a5c');
  });

  it('returns an empty string for a custom property that is not set', () => {
    expect(resolveDesignToken('--does-not-exist')).toBe('');
  });
});
