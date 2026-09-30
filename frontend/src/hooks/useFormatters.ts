import { useMemo } from 'react';
import { useTranslation } from 'react-i18next';
import { formatCurrency } from '../utils';

/** Formatters bound to the active language; identities only change when the language does. */
export function useFormatters() {
  const { i18n } = useTranslation();
  const { language } = i18n;

  return useMemo(
    () => ({
      formatCurrency: (amount: number | null | undefined) => formatCurrency(amount, language),
    }),
    [language],
  );
}
