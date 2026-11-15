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
 *
 * Below `sm` the buttons are icon-only (the label stays for screen readers and as `aria-label`):
 * with a long label ("Precedente"/"Successivo") the row is wider than a 390 px screen's content
 * column and "Pagina X di Y" wraps onto two lines. Icon-only keeps one row whatever the language.
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
  if (totalPages <= 1) {
    // Nothing to navigate: a plain line, not an (empty) navigation landmark.
    return summary ? <p className="text-sm font-body text-on-surface-variant">{summary}</p> : null;
  }

  return (
    <nav
      aria-label={pageLabel}
      className={cn('flex flex-wrap items-center gap-3', summary ? 'justify-between' : 'justify-center')}
    >
      {summary && <span className="text-sm font-body text-on-surface-variant">{summary}</span>}
      <div className="flex items-center gap-3">
        <M3Button
          variant="outlined"
          icon="chevron_left"
          disabled={page === 0}
          onClick={onPrev}
          aria-label={prevLabel}
        >
          <span className="max-sm:sr-only">{prevLabel}</span>
        </M3Button>
        <span className="text-sm font-body text-on-surface-variant whitespace-nowrap">
          {pageOfLabel(page + 1, totalPages)}
        </span>
        <M3Button
          variant="outlined"
          icon="chevron_right"
          disabled={page >= totalPages - 1}
          onClick={onNext}
          aria-label={nextLabel}
        >
          <span className="max-sm:sr-only">{nextLabel}</span>
        </M3Button>
      </div>
    </nav>
  );
};
