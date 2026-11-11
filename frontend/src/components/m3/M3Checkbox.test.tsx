/* eslint-disable react-perf/jsx-no-new-function-as-prop */
import { describe, it, expect, vi } from 'vitest';
import { render, screen, fireEvent } from '@testing-library/react';
import { axe } from 'vitest-axe';
import { M3Checkbox } from './M3Checkbox';

describe('M3Checkbox', () => {
  it('associates the label with the checkbox', () => {
    render(<M3Checkbox label="Auto-send" checked={false} onChange={() => {}} />);
    expect(screen.getByLabelText('Auto-send')).toBeInTheDocument();
  });

  it('renders as an unchecked/checked checkbox based on the checked prop', () => {
    const { rerender } = render(<M3Checkbox label="Auto-send" checked={false} onChange={() => {}} />);
    expect(screen.getByRole('checkbox')).not.toBeChecked();
    rerender(<M3Checkbox label="Auto-send" checked onChange={() => {}} />);
    expect(screen.getByRole('checkbox')).toBeChecked();
  });

  it('fires onChange when toggled', () => {
    const handleChange = vi.fn();
    render(<M3Checkbox label="Auto-send" checked={false} onChange={handleChange} />);
    fireEvent.click(screen.getByRole('checkbox'));
    expect(handleChange).toHaveBeenCalledTimes(1);
  });

  it('renders supporting text and links it via aria-describedby', () => {
    render(<M3Checkbox label="Auto-send" checked={false} onChange={() => {}} supportingText="Sends automatically at check-out" />);
    expect(screen.getByText('Sends automatically at check-out')).toBeInTheDocument();
    expect(screen.getByRole('checkbox')).toHaveAttribute('aria-describedby');
  });

  it('has no accessibility violations', async () => {
    const { container } = render(
      <M3Checkbox label="Auto-send" checked={false} onChange={() => {}} supportingText="Sends automatically at check-out" />,
    );
    const results = await axe(container);
    expect(results).toHaveNoViolations();
  });

  it('stretches the label over the whole row so the tap area is 44 px on touch without moving the desktop layout', () => {
    render(<M3Checkbox label="Auto-send" checked={false} onChange={() => {}} />);
    const label = screen.getByText('Auto-send');
    expect(label.className).toContain('after:absolute');
    expect(label.className).toContain('after:inset-0');
    expect(label.className).not.toContain('block');
    const row = label.closest('div')?.parentElement;
    expect(row?.className).toContain('relative');
    expect(row?.className).toContain('min-h-11');
    expect(row?.className).toContain('sm:pointer-fine:min-h-0');
  });
});
