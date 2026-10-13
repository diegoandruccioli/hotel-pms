import { describe, it, expect, vi } from 'vitest';
import { render, screen } from '@testing-library/react';
import { axe } from 'vitest-axe';
import { GuestNameCell } from './ReservationRowCells';

vi.mock('react-i18next', () => ({
  useTranslation: () => ({ t: (key: string) => key }),
}));

describe('GuestNameCell', () => {
  it('shows the initial in a decorative avatar and the full name', () => {
    render(<GuestNameCell name="Marco Bianchi" />);
    expect(screen.getByText('Marco Bianchi')).toBeInTheDocument();
    expect(screen.getByText('M')).toHaveAttribute('aria-hidden', 'true');
  });

  it('falls back to a placeholder initial without a name', () => {
    render(<GuestNameCell />);
    expect(screen.getByText('?')).toBeInTheDocument();
  });

  it('has no accessibility violations', async () => {
    const { container } = render(<GuestNameCell name="Marco Bianchi" />);
    expect(await axe(container)).toHaveNoViolations();
  });
});
