/**
 * Below `md` a Rooms table keeps only its first column (the name, with the details stacked under
 * it) and the row actions, so the pinned actions column no longer covers the state or the price
 * and no sideways scroll is needed. The edge padding shrinks to 16 px to fit 390 px exactly.
 */
export const HIDE_DETAIL_COLUMNS_ON_PHONE = [
  'max-md:[&_th:not(:first-child):not(:last-child)]:hidden',
  'max-md:[&_td:not(:first-child):not(:last-child):not([colspan])]:hidden',
  'max-md:[&_th:first-child]:pl-4 max-md:[&_td:first-child]:pl-4',
  'max-md:[&_th:last-child]:pr-4 max-md:[&_td:last-child]:pr-4',
].join(' ');
