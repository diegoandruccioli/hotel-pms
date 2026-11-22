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

  it('grows to a 44 px touch target without changing the desktop chip height', () => {
    render(<M3FilterChip label="Paid" selected={false} onClick={vi.fn()} />);
    const chip = screen.getByRole('button', { name: 'Paid' });
    expect(chip.className).toContain('min-h-11');
    expect(chip.className).toContain('sm:pointer-fine:min-h-0');
  });

  it('is at least 44 px wide on small screens and reaches 40 px with a mouse through an invisible hit area', () => {
    render(<M3FilterChip label="All" selected={false} onClick={vi.fn()} />);
    const chip = screen.getByRole('button', { name: 'All' });
    expect(chip.className).toContain('min-w-11');
    expect(chip.className).toContain('justify-center');
    expect(chip.className).toContain('sm:pointer-fine:relative');
    expect(chip.className).toContain('sm:pointer-fine:after:absolute');
    expect(chip.className).toContain('sm:pointer-fine:after:-inset-y-1.5');
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
