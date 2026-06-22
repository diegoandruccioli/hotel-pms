import { describe, it, expect } from 'vitest';
import { isPasswordValid, PASSWORD_REQUIREMENTS } from './passwordPolicy';

describe('passwordPolicy', () => {
  it('rejects a password shorter than 16 characters', () => {
    expect(isPasswordValid('Aa1!Aa1!Aa1!')).toBe(false);
  });

  it('rejects a 16+ character password with fewer than 2 uppercase letters', () => {
    expect(isPasswordValid('aaaaaaaaaaaaaaA1!!')).toBe(false);
  });

  it('rejects a 16+ character password with fewer than 2 digits', () => {
    expect(isPasswordValid('aaaaaaaaaaaaaaAA1!')).toBe(false);
  });

  it('rejects a 16+ character password with fewer than 2 special characters', () => {
    expect(isPasswordValid('aaaaaaaaaaaaaaAA11')).toBe(false);
  });

  it('accepts a password satisfying every requirement', () => {
    expect(isPasswordValid('HotelPms@@2026xx')).toBe(true);
  });

  it('exposes exactly 4 requirements', () => {
    expect(PASSWORD_REQUIREMENTS).toHaveLength(4);
  });
});
