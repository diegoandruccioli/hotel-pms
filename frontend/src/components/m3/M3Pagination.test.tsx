/* eslint-disable react-perf/jsx-no-new-function-as-prop */
import { describe, it, expect, vi } from 'vitest';
import { render, screen } from '@testing-library/react';
import { fireEvent } from '@testing-library/react';
import { axe } from 'vitest-axe';
import { M3Pagination } from './M3Pagination';

const pageOfLabel = (current: number, total: number) => `Page ${current} of ${total}`;

describe('M3Pagination', () => {
  it('renders nothing when there is only one page', () => {
    const { container } = render(
      <M3Pagination
        page={0}
        totalPages={1}
        onPrev={() => {}}
        onNext={() => {}}
        pageLabel="Pagination"
        prevLabel="Previous"
        nextLabel="Next"
        pageOfLabel={pageOfLabel}
      />,
    );
    expect(container).toBeEmptyDOMElement();
  });

  it('renders the page-of-pages label', () => {
    render(
      <M3Pagination
        page={1}
        totalPages={3}
        onPrev={() => {}}
        onNext={() => {}}
        pageLabel="Pagination"
        prevLabel="Previous"
        nextLabel="Next"
        pageOfLabel={pageOfLabel}
      />,
    );
    expect(screen.getByText('Page 2 of 3')).toBeInTheDocument();
  });

  it('disables Previous on the first page and calls onNext otherwise', () => {
    const onNext = vi.fn();
    render(
      <M3Pagination
        page={0}
        totalPages={3}
        onPrev={() => {}}
        onNext={onNext}
        pageLabel="Pagination"
        prevLabel="Previous"
        nextLabel="Next"
        pageOfLabel={pageOfLabel}
      />,
    );
    expect(screen.getByRole('button', { name: 'Previous' })).toBeDisabled();
    fireEvent.click(screen.getByRole('button', { name: 'Next' }));
    expect(onNext).toHaveBeenCalledTimes(1);
  });

  it('disables Next on the last page', () => {
    render(
      <M3Pagination
        page={2}
        totalPages={3}
        onPrev={() => {}}
        onNext={() => {}}
        pageLabel="Pagination"
        prevLabel="Previous"
        nextLabel="Next"
        pageOfLabel={pageOfLabel}
      />,
    );
    expect(screen.getByRole('button', { name: 'Next' })).toBeDisabled();
  });

  const baseProps = {
    onPrev: () => {}, onNext: () => {}, pageLabel: 'Pagination', prevLabel: 'Previous', nextLabel: 'Next', pageOfLabel,
  };

  it('shows the result summary and no page buttons when there is a single page', () => {
    render(<M3Pagination {...baseProps} page={0} totalPages={1} summary="1–5 of 5" />);
    expect(screen.getByText('1–5 of 5')).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Next' })).not.toBeInTheDocument();
  });

  it('shows the summary alongside the page controls', () => {
    render(<M3Pagination {...baseProps} page={0} totalPages={3} summary="1–20 of 50" />);
    expect(screen.getByText('1–20 of 50')).toBeInTheDocument();
    expect(screen.getByText('Page 1 of 3')).toBeInTheDocument();
  });

  it('has no accessibility violations', async () => {
    const { container } = render(
      <M3Pagination
        page={1}
        totalPages={3}
        onPrev={() => {}}
        onNext={() => {}}
        pageLabel="Pagination"
        prevLabel="Previous"
        nextLabel="Next"
        pageOfLabel={pageOfLabel}
      />,
    );
    const results = await axe(container);
    expect(results).toHaveNoViolations();
  });
});
