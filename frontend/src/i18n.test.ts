import { afterEach, describe, expect, it } from 'vitest';
import i18n from './i18n';

describe('i18n html lang sync', () => {
  afterEach(async () => {
    await i18n.changeLanguage('en');
  });

  it('sets <html lang> to the language after init', () => {
    expect(document.documentElement.lang).toBe(i18n.resolvedLanguage);
  });

  it('updates <html lang> when the language changes', async () => {
    await i18n.changeLanguage('it');
    expect(document.documentElement.lang).toBe('it');

    await i18n.changeLanguage('en');
    expect(document.documentElement.lang).toBe('en');
  });

  it('uses the resolved language for regional codes', async () => {
    await i18n.changeLanguage('it-IT');
    expect(document.documentElement.lang).toBe('it');
  });
});
