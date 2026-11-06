import { describe, it, expect } from 'vitest';
import { render, screen } from '@testing-library/react';
import { axe } from 'vitest-axe';
import { M3LoadingState } from './M3LoadingState';

describe('M3LoadingState', () => {
  it('renders a status role with the given accessible label', () => {
    render(<M3LoadingState label="Loading guests" />);
    expect(screen.getByRole('status')).toHaveTextContent('Loading guests');
  });

  it('renders the spinning icon', () => {
    render(<M3LoadingState label="Loading" />);
    expect(screen.getByText('progress_activity')).toBeInTheDocument();
  });

  it('applies additional className', () => {
    render(<M3LoadingState label="Loading" className="custom-class" />);
    expect(screen.getByRole('status').className).toContain('custom-class');
  });

  it('renders the card surface by default and drops it when plain', () => {
    const { rerender } = render(<M3LoadingState label="Loading" />);
    expect(screen.getByRole('status')).toHaveClass('bg-surface', 'shadow-elevation-1');
    rerender(<M3LoadingState label="Loading" plain />);
    expect(screen.getByRole('status')).not.toHaveClass('bg-surface');
    expect(screen.getByRole('status')).not.toHaveClass('shadow-elevation-1');
  });

  it('has no accessibility violations', async () => {
    const { container } = render(<M3LoadingState label="Loading guests" />);
    const results = await axe(container);
    expect(results).toHaveNoViolations();
  });
});
