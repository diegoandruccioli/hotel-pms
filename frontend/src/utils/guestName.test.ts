import { describe, it, expect } from 'vitest';
import { formatGuestName } from './guestName';

describe('formatGuestName', () => {
  it('puts the surname before the first name', () => {
    expect(formatGuestName({ firstName: 'Mario', lastName: 'Rossi' })).toBe('Rossi Mario');
  });

  it('keeps a surname made of several words together', () => {
    expect(formatGuestName({ firstName: 'Live', lastName: 'Suite Guest' })).toBe('Suite Guest Live');
  });

  it('returns what is there when a part is missing, without stray spaces', () => {
    expect(formatGuestName({ firstName: 'Mario', lastName: '' })).toBe('Mario');
    expect(formatGuestName({ firstName: null, lastName: 'Rossi' })).toBe('Rossi');
    expect(formatGuestName({})).toBe('');
  });
});
