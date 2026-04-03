import { describe, it, expect } from 'vitest';
import { render, screen } from '@testing-library/react';
import { M3StatusChip } from './M3StatusChip';

describe('M3StatusChip', () => {
  it('should render label text', () => {
    render(<M3StatusChip label="Active" />);
    expect(screen.getByText('Active')).toBeInTheDocument();
  });

  it('should apply neutral tone by default', () => {
    render(<M3StatusChip label="Neutral" />);
    const el = screen.getByText('Neutral').closest('span');
    expect(el?.className).toContain('bg-surface-container-highest');
  });

  it('should apply success tone classes', () => {
    render(<M3StatusChip label="Paid" tone="success" />);
    const el = screen.getByText('Paid').closest('span');
    expect(el?.className).toContain('bg-tertiary-container');
  });

  it('should apply error tone classes', () => {
    render(<M3StatusChip label="Error" tone="error" />);
    const el = screen.getByText('Error').closest('span');
    expect(el?.className).toContain('bg-error-container');
  });

  it('should render icon when provided', () => {
    render(<M3StatusChip label="Info" icon="info" />);
    expect(screen.getByText('info')).toBeInTheDocument();
  });

  it('should not render icon when not provided', () => {
    const { container } = render(<M3StatusChip label="Plain" />);
    const iconSpans = container.querySelectorAll('.material-symbols-outlined');
    expect(iconSpans).toHaveLength(0);
  });
});
