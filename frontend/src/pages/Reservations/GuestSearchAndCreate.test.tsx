import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, waitFor, fireEvent } from '@testing-library/react';
import { axe } from 'vitest-axe';
import { GuestSearchAndCreate } from './GuestSearchAndCreate';
import { guestService } from '../../services/guestService';

vi.mock('react-i18next', () => ({
  useTranslation: () => ({ t: (key: string) => key, i18n: { language: 'en' } }),
  initReactI18next: { type: '3rdParty', init: vi.fn() },
}));

vi.mock('../../services/guestService', () => ({
  guestService: { searchGuests: vi.fn(), createGuest: vi.fn() },
}));

vi.mock('../../components/m3/M3TextField', () => ({
  M3TextField: ({ label, name, value, onChange, required }: {
    label: string; name?: string; value: string; onChange?: (e: React.ChangeEvent<HTMLInputElement>) => void; required?: boolean;
  }) => <input aria-label={label} name={name} value={value} onChange={onChange} required={required} />,
}));

const GUEST = {
  id: 'g1', firstName: 'John', lastName: 'Doe', email: 'john@test.com',
  phone: '123', city: 'Rome', country: 'IT',
};

describe('GuestSearchAndCreate', () => {
  const onSelectGuest = vi.fn();
  const onClearGuest = vi.fn();

  beforeEach(() => vi.clearAllMocks());

  it('renders search input when no guest selected', () => {
    render(<GuestSearchAndCreate selectedGuest={null} onSelectGuest={onSelectGuest} onClearGuest={onClearGuest} />);
    expect(screen.getByLabelText('search_guest_placeholder')).toBeInTheDocument();
  });

  it('renders selected guest card when guest is provided', () => {
    render(<GuestSearchAndCreate selectedGuest={GUEST} onSelectGuest={onSelectGuest} onClearGuest={onClearGuest} />);
    expect(screen.getByText('John Doe')).toBeInTheDocument();
    expect(screen.getByText('john@test.com', { exact: false })).toBeInTheDocument();
  });

  it('change button calls onClearGuest', () => {
    render(<GuestSearchAndCreate selectedGuest={GUEST} onSelectGuest={onSelectGuest} onClearGuest={onClearGuest} />);
    fireEvent.click(screen.getByText('btn_change'));
    expect(onClearGuest).toHaveBeenCalledOnce();
  });

  it('hides change button when readOnly=true', () => {
    render(<GuestSearchAndCreate selectedGuest={GUEST} onSelectGuest={onSelectGuest} onClearGuest={onClearGuest} readOnly />);
    expect(screen.queryByText('btn_change')).not.toBeInTheDocument();
  });

  it('shows create guest form on create button click', () => {
    render(<GuestSearchAndCreate selectedGuest={null} onSelectGuest={onSelectGuest} onClearGuest={onClearGuest} />);
    fireEvent.click(screen.getByText('btn_new_guest'));
    expect(screen.getByText('heading_create_guest')).toBeInTheDocument();
  });

  it('calls createGuest and onSelectGuest on submit', async () => {
    vi.mocked(guestService.createGuest).mockResolvedValue(GUEST as never);
    render(<GuestSearchAndCreate selectedGuest={null} onSelectGuest={onSelectGuest} onClearGuest={onClearGuest} />);
    fireEvent.click(screen.getByText('btn_new_guest'));
    fireEvent.click(screen.getByText('btn_save_guest'));
    await waitFor(() => expect(onSelectGuest).toHaveBeenCalledWith(GUEST));
  });

  it('passes axe accessibility check on search view', async () => {
    const { container } = render(
      <GuestSearchAndCreate selectedGuest={null} onSelectGuest={onSelectGuest} onClearGuest={onClearGuest} />
    );
    expect(await axe(container)).toHaveNoViolations();
  });
});
