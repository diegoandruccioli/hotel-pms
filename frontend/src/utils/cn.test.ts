import { describe, it, expect } from 'vitest';
import { cn } from './cn';

describe('cn', () => {
  it('joins static class strings', () => {
    expect(cn('a', 'b')).toBe('a b');
  });

  it('drops falsy values', () => {
    const shown = false;
    expect(cn('a', shown && 'b', undefined, null, '', 'c')).toBe('a c');
  });

  it('applies conditional object syntax', () => {
    expect(cn('base', { active: true, hidden: false })).toBe('base active');
  });

  it('resolves conflicting Tailwind utilities in favor of the later one', () => {
    expect(cn('px-2', 'px-4')).toBe('px-4');
  });

  it('lets a caller override a component default via a later argument', () => {
    expect(cn('text-sm text-on-surface', 'text-error')).toBe('text-sm text-error');
  });
});
