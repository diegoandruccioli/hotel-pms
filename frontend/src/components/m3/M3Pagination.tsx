import { cn } from '../../utils';
import { M3Button } from './M3Button';

interface M3PaginationProps {
  /** Zero-based current page, matching SpringPage semantics used everywhere else. */
  page: number;
  totalPages: number;
  onPrev: () => void;
  onNext: () => void;
  pageLabel: string;
  prevLabel: string;
  nextLabel: string;
  pageOfLabel: (current: number, total: number) => string;
  /** Result range ("1–20 of 214"), already translated. Shown even with a single page. */
  summary?: string;
}

/**
 * Extracted from the pagination block Guests.tsx already had — every list
 * page needs the same prev/next + "page X of Y" control over a SpringPage
 * response, previously copy-pasted per page.
 */
export const M3Pagination = ({
  page,
  totalPages,
  onPrev,
  onNext,
  pageLabel,
  prevLabel,
  nextLabel,
  pageOfLabel,
  summary,
}: M3PaginationProps) => {
  if (totalPages <= 1 && !summary) return null;

  return (
    <nav
      aria-label={pageLabel}
      className={cn('flex flex-wrap items-center gap-3', summary ? 'justify-between' : 'justify-center')}
    >
      {summary && <span className="text-sm font-body text-on-surface-variant">{summary}</span>}
      {totalPages > 1 && (
        <div className="flex items-center gap-3">
          <M3Button
            variant="outlined"
            icon="chevron_left"
            disabled={page === 0}
            onClick={onPrev}
            aria-label={prevLabel}
          >
            {prevLabel}
          </M3Button>
          <span className="text-sm font-body text-on-surface-variant">
            {pageOfLabel(page + 1, totalPages)}
          </span>
          <M3Button
            variant="outlined"
            icon="chevron_right"
            disabled={page >= totalPages - 1}
            onClick={onNext}
            aria-label={nextLabel}
          >
            {nextLabel}
          </M3Button>
        </div>
      )}
    </nav>
  );
};
