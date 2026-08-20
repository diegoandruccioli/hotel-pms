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

  it('calls onRetry when the retry button is clicked', () => {
    const onRetry = vi.fn();
    render(<M3ErrorState title="Error" message="Something went wrong" retryLabel="Try again" onRetry={onRetry} />);
    fireEvent.click(screen.getByRole('button', { name: 'Try again' }));
    expect(onRetry).toHaveBeenCalledTimes(1);
  });

  it('has no accessibility violations', async () => {
    const { container } = render(
      <M3ErrorState title="Error loading guests" message="Network error" retryLabel="Try again" onRetry={vi.fn()} />,
    );
    const results = await axe(container);
    expect(results).toHaveNoViolations();
  });
});
