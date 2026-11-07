import { describe, it, expect, vi } from 'vitest';
import { render, screen, fireEvent } from '@testing-library/react';
import { axe } from 'vitest-axe';
import { M3FilterChip } from './M3FilterChip';

describe('M3FilterChip', () => {
  it('renders the label as a button', () => {
    render(<M3FilterChip label="Checked in" selected={false} onClick={vi.fn()} />);
    expect(screen.getByRole('button', { name: 'Checked in' })).toHaveAttribute('type', 'button');
  });

  it('reflects the selected state via aria-pressed', () => {
    const { rerender } = render(<M3FilterChip label="Paid" selected onClick={vi.fn()} />);
    const chip = screen.getByRole('button', { name: 'Paid' });
    expect(chip).toHaveAttribute('aria-pressed', 'true');
    expect(chip.className).toContain('bg-primary');

    rerender(<M3FilterChip label="Paid" selected={false} onClick={vi.fn()} />);
    expect(chip).toHaveAttribute('aria-pressed', 'false');
    expect(chip.className).toContain('border-outline-variant');
  });

  it('calls onClick when pressed', () => {
    const onClick = vi.fn();
    render(<M3FilterChip label="Paid" selected={false} onClick={onClick} />);
    fireEvent.click(screen.getByRole('button', { name: 'Paid' }));
    expect(onClick).toHaveBeenCalledTimes(1);
  });

  it('reports its value to onValueSelect', () => {
    const onValueSelect = vi.fn();
    const onClick = vi.fn();
    render(<M3FilterChip label="Paid" value="PAID" selected={false} onValueSelect={onValueSelect} onClick={onClick} />);
    fireEvent.click(screen.getByRole('button', { name: 'Paid' }));
    expect(onValueSelect).toHaveBeenCalledWith('PAID');
    expect(onClick).toHaveBeenCalledTimes(1);
  });

  it('does not call onValueSelect without a value', () => {
    const onValueSelect = vi.fn();
    render(<M3FilterChip label="Paid" selected={false} onValueSelect={onValueSelect} />);
    fireEvent.click(screen.getByRole('button', { name: 'Paid' }));
    expect(onValueSelect).not.toHaveBeenCalled();
  });

  it('merges a custom className', () => {
    render(<M3FilterChip label="Paid" selected={false} className="ml-2" onClick={vi.fn()} />);
    expect(screen.getByRole('button', { name: 'Paid' }).className).toContain('ml-2');
  });

  it('passes axe accessibility check', async () => {
    const { container } = render(<M3FilterChip label="Paid" selected onClick={vi.fn()} />);
    expect(await axe(container)).toHaveNoViolations();
  }, 30000);
});
