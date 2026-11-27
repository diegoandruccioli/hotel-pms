import { describe, it, expect, vi } from 'vitest';
import { render, screen, fireEvent } from '@testing-library/react';
import { axe } from 'vitest-axe';
import { M3ErrorState } from './M3ErrorState';

describe('M3ErrorState', () => {
  it('renders title and message with an alert role', () => {
    render(<M3ErrorState title="Error loading guests" message="Network error" />);
    const alert = screen.getByRole('alert');
    expect(alert).toHaveTextContent('Error loading guests');
    expect(alert).toHaveTextContent('Network error');
  });

  it('does not render a retry button when onRetry is omitted', () => {
    render(<M3ErrorState title="Error" message="Something went wrong" />);
    expect(screen.queryByRole('button')).not.toBeInTheDocument();
  });

  it('does not render a retry button when retryLabel is omitted', () => {
    const onRetry = vi.fn();
    render(<M3ErrorState title="Error" message="Something went wrong" onRetry={onRetry} />);
    expect(screen.queryByRole('button')).not.toBeInTheDocument();
  });

  it('gives the retry button a 44 px touch target (40 px with a mouse)', () => {
    render(<M3ErrorState title="Error" message="Something went wrong" retryLabel="Try again" onRetry={vi.fn()} />);
    const retry = screen.getByRole('button', { name: 'Try again' });
    expect(retry.className).toContain('min-h-11');
    expect(retry.className).toContain('sm:pointer-fine:min-h-10');
  });

  it('calls onRetry when the retry button is clicked', () => {
    const onRetry = vi.fn();
    render(<M3ErrorState title="Error" message="Something went wrong" retryLabel="Try again" onRetry={onRetry} />);
    fireEvent.click(screen.getByRole('button', { name: 'Try again' }));
    expect(onRetry).toHaveBeenCalledTimes(1);
  });

  it('renders the title as an h2 by default, so it sits right under a page h1', async () => {
    const { container } = render(
      <main>
        <h1>Page</h1>
        <M3ErrorState title="Error loading guests" message="Network error" />
      </main>,
    );
    expect(screen.getByRole('heading', { level: 2, name: 'Error loading guests' })).toBeInTheDocument();
    expect(await axe(container)).toHaveNoViolations();
  });

  it('renders the title as an h3 when headingLevel is 3, under a section h2', async () => {
    const { container } = render(
      <main>
        <h1>Page</h1>
        <h2>Section</h2>
        <M3ErrorState title="Error loading guests" message="Network error" headingLevel={3} />
      </main>,
    );
    expect(screen.getByRole('heading', { level: 3, name: 'Error loading guests' })).toBeInTheDocument();
    expect(await axe(container)).toHaveNoViolations();
  });

  it('has no accessibility violations', async () => {
    const { container } = render(
      <M3ErrorState title="Error loading guests" message="Network error" retryLabel="Try again" onRetry={vi.fn()} />,
    );
    const results = await axe(container);
    expect(results).toHaveNoViolations();
  });
});
