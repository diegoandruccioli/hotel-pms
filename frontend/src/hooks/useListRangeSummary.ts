import { useMemo } from 'react';
import { useTranslation } from 'react-i18next';

/**
 * "1–20 of 214" line for a paged list, numbers formatted for the active
 * language. `undefined` when there is nothing to show (empty list), so the
 * caller doesn't render "1–0 of 0".
 */
export const useListRangeSummary = (page: number, pageSize: number, shown: number, total: number): string | undefined => {
  const { t, i18n } = useTranslation('common');
  return useMemo(() => {
    if (total === 0 || shown === 0) return undefined;
    const numbers = new Intl.NumberFormat(i18n.language);
    const from = page * pageSize + 1;
    return t('list_range_summary', {
      from: numbers.format(from),
      to: numbers.format(from + shown - 1),
      total: numbers.format(total),
    });
  }, [t, i18n.language, page, pageSize, shown, total]);
};
