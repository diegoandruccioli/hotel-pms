import { describe, it, expect } from 'vitest';
import { render, screen } from '@testing-library/react';
import { axe } from 'vitest-axe';
import { M3Avatar } from './M3Avatar';

describe('M3Avatar', () => {
  it('shows the uppercase first letter of the name', () => {
    render(<M3Avatar name="alice" size="md" />);
    expect(screen.getByText('A')).toBeInTheDocument();
  });

  it('falls back to a question mark without a name', () => {
    render(<M3Avatar name={null} size="md" />);
    expect(screen.getByText('?')).toBeInTheDocument();
  });

  it('falls back to a question mark for an empty name', () => {
    render(<M3Avatar name="" size="lg" />);
    expect(screen.getByText('?')).toBeInTheDocument();
  });

  it('applies the size classes', () => {
    const { rerender } = render(<M3Avatar name="a" size="md" />);
    expect(screen.getByText('A').className).toContain('w-10');
    rerender(<M3Avatar name="a" size="lg" />);
    expect(screen.getByText('A').className).toContain('w-16');
  });

  it('forwards aria-hidden and className', () => {
    render(<M3Avatar name="a" size="lg" aria-hidden="true" className="mx-auto" />);
    const el = screen.getByText('A');
    expect(el).toHaveAttribute('aria-hidden', 'true');
    expect(el.className).toContain('mx-auto');
  });

  it('passes axe accessibility check', async () => {
    const { container } = render(<M3Avatar name="alice" size="md" />);
    expect(await axe(container)).toHaveNoViolations();
  }, 30000);
});
