import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, fireEvent } from '@testing-library/react';
import { axe } from 'vitest-axe';
import { StayStatusChip } from './StayStatusChip';

const renderChip = (props: Partial<React.ComponentProps<typeof StayStatusChip>> = {}) =>
  render(
    <StayStatusChip
      value="CHECKED_IN"
      active={false}
      label="Checked in"
      onClick={vi.fn()}
      {...props}
    />,
  );

describe('StayStatusChip', () => {
  beforeEach(() => vi.clearAllMocks());

  it('renders the label', () => {
    renderChip();
    expect(screen.getByText('Checked in')).toBeInTheDocument();
  });

  it('reflects the active state via aria-pressed', () => {
    renderChip({ active: true });
    expect(screen.getByRole('button', { name: 'Checked in' })).toHaveAttribute('aria-pressed', 'true');
  });

  it('calls onClick with the chip value', () => {
    const onClick = vi.fn();
    renderChip({ onClick, value: 'EXPECTED' });
    fireEvent.click(screen.getByText('Checked in'));
    expect(onClick).toHaveBeenCalledWith('EXPECTED');
  });

  it('passes axe accessibility check', async () => {
    const { container } = renderChip();
    expect(await axe(container)).toHaveNoViolations();
  }, 30000);
});
