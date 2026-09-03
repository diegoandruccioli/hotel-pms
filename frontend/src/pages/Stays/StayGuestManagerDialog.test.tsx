import { describe, it, expect, vi, beforeEach } from 'vitest';
import { screen, waitFor, fireEvent } from '@testing-library/react';
import { renderWithQuery as render } from '../../test-utils';
import { StayGuestManagerDialog } from './StayGuestManagerDialog';
import { stayService } from '../../services';
import type { StayGuestResponse, StayResponse } from '../../types';

vi.mock('react-i18next', () => {
  const t = (key: string, opts?: Record<string, unknown>) =>
    opts ? `${key}:${JSON.stringify(opts)}` : key;
  return {
    useTranslation: () => ({ t }),
    initReactI18next: { type: '3rdParty', init: vi.fn() },
  };
});

vi.mock('focus-trap-react', () => ({
  default: ({ children }: { children: React.ReactNode }) => <>{children}</>,
}));

vi.mock('../../services/stayService', () => ({
  stayService: {
    getStayById: vi.fn(),
    getLookupStati: vi.fn(),
    getLookupTipdoc: vi.fn(),
    addGuest: vi.fn(),
    updateGuest: vi.fn(),
    removeGuest: vi.fn(),
    recordGuestDeparture: vi.fn(),
    promoteGuestToPrimary: vi.fn(),
  },
}));

const addToastMock = vi.hoisted(() => vi.fn());
vi.mock('../../store/toastStore', () => ({
  useToastStore: (selector: unknown) =>
    (selector as (s: { addToast: typeof addToastMock }) => unknown)({ addToast: addToastMock }),
}));

const STAY_ID = 'stay-1';

const primaryGuest: StayGuestResponse = {
  id: 'guest-primary',
  firstName: 'Mario',
  lastName: 'Rossi',
  gender: '1',
  dateOfBirth: '1990-01-01',
  placeOfBirth: '058091000',
  citizenship: '100000100',
  documentType: 'PASOR',
  documentNumber: 'AA1234567',
  documentPlaceOfIssue: '058091000',
  isPrimaryGuest: true,
  travellerType: 'CAPOFAMIGLIA',
  arrivalDate: '2026-05-01',
  alloggiatiSent: true,
  needsResubmit: false,
};

const secondaryGuest: StayGuestResponse = {
  id: 'guest-secondary',
  firstName: 'Anna',
  lastName: 'Verdi',
  gender: '2',
  dateOfBirth: '1992-03-03',
  placeOfBirth: '058091000',
  citizenship: '100000100',
  isPrimaryGuest: false,
  travellerType: 'FAMILIARE',
  arrivalDate: '2026-05-02',
  alloggiatiSent: false,
  needsResubmit: false,
};

const stayWith = (guests: StayGuestResponse[]): StayResponse => ({
  id: STAY_ID,
  reservationId: 'res-1',
  guestId: 'guest-1',
  roomId: 'room-1',
  roomNumber: '101',
  status: 'CHECKED_IN',
  createdAt: '2026-05-01T10:00:00Z',
  updatedAt: '2026-05-01T10:00:00Z',
  alloggiatiSent: true,
  alloggiatiSendFailed: false,
  guests,
  invoiceCreationFailed: false,
  checkoutEmailFailed: false,
});

describe('StayGuestManagerDialog', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    vi.mocked(stayService.getLookupStati).mockResolvedValue([
      { codice: '100000100', descrizione: 'ITALIA' },
    ]);
    vi.mocked(stayService.getLookupTipdoc).mockResolvedValue([
      { codice: 'PASOR', descrizione: 'Passaporto' },
    ]);
  });

  it('renders nothing when stayId is null', () => {
    const { container } = render(<StayGuestManagerDialog stayId={null} onClose={vi.fn()} />);
    expect(container).toBeEmptyDOMElement();
  });

  it('lists guests with primary/sent/needs-resubmit badges', async () => {
    vi.mocked(stayService.getStayById).mockResolvedValue(stayWith([primaryGuest, secondaryGuest]));

    render(<StayGuestManagerDialog stayId={STAY_ID} onClose={vi.fn()} />);

    await waitFor(() => expect(screen.getByText('Rossi Mario')).toBeInTheDocument());
    expect(screen.getByText('Verdi Anna')).toBeInTheDocument();
    expect(screen.getByText('guest_badge_primary')).toBeInTheDocument();
    expect(screen.getByText('guest_badge_sent')).toBeInTheDocument();
  });

  it('disables remove for a guest already sent to Alloggiati Web', async () => {
    vi.mocked(stayService.getStayById).mockResolvedValue(stayWith([primaryGuest]));

    render(<StayGuestManagerDialog stayId={STAY_ID} onClose={vi.fn()} />);

    await waitFor(() => expect(screen.getByText('Rossi Mario')).toBeInTheDocument());
    const removeButton = screen.getByRole('button', { name: 'btn_remove' });
    expect(removeButton).toBeDisabled();
  });

  it('removes a never-sent, non-primary guest after confirmation', async () => {
    vi.mocked(stayService.getStayById)
      .mockResolvedValueOnce(stayWith([primaryGuest, secondaryGuest]))
      .mockResolvedValueOnce(stayWith([primaryGuest]));
    vi.mocked(stayService.removeGuest).mockResolvedValue(undefined);

    render(<StayGuestManagerDialog stayId={STAY_ID} onClose={vi.fn()} />);

    await waitFor(() => expect(screen.getByText('Verdi Anna')).toBeInTheDocument());
    const removeButtons = screen.getAllByRole('button', { name: 'btn_remove' });
    fireEvent.click(removeButtons[removeButtons.length - 1]);
    fireEvent.click(screen.getByRole('button', { name: 'btn_confirm' }));

    await waitFor(() => expect(stayService.removeGuest).toHaveBeenCalledWith(STAY_ID, 'guest-secondary'));
    await waitFor(() => expect(screen.queryByText('Verdi Anna')).not.toBeInTheDocument());
  });

  it('promotes a non-primary guest to primary', async () => {
    vi.mocked(stayService.getStayById)
      .mockResolvedValueOnce(stayWith([primaryGuest, secondaryGuest]))
      .mockResolvedValueOnce(stayWith([primaryGuest, { ...secondaryGuest, isPrimaryGuest: true }]));
    vi.mocked(stayService.promoteGuestToPrimary).mockResolvedValue({ ...secondaryGuest, isPrimaryGuest: true });

    render(<StayGuestManagerDialog stayId={STAY_ID} onClose={vi.fn()} />);

    await waitFor(() => expect(screen.getByText('Verdi Anna')).toBeInTheDocument());
    fireEvent.click(screen.getByRole('button', { name: 'btn_promote_primary' }));

    await waitFor(() =>
      expect(stayService.promoteGuestToPrimary).toHaveBeenCalledWith(STAY_ID, 'guest-secondary'));
  });

  it('records an early departure for a guest', async () => {
    vi.mocked(stayService.getStayById)
      .mockResolvedValueOnce(stayWith([primaryGuest, secondaryGuest]))
      .mockResolvedValueOnce(stayWith([primaryGuest, { ...secondaryGuest, departureDate: '2026-05-03' }]));
    vi.mocked(stayService.recordGuestDeparture).mockResolvedValue({ ...secondaryGuest, departureDate: '2026-05-03' });

    render(<StayGuestManagerDialog stayId={STAY_ID} onClose={vi.fn()} />);

    await waitFor(() => expect(screen.getByText('Verdi Anna')).toBeInTheDocument());
    const departureButtons = screen.getAllByRole('button', { name: 'btn_record_departure' });
    fireEvent.click(departureButtons[departureButtons.length - 1]);
    fireEvent.click(screen.getByRole('button', { name: 'btn_confirm' }));

    await waitFor(() => expect(stayService.recordGuestDeparture).toHaveBeenCalledWith(
      STAY_ID, 'guest-secondary', expect.any(String),
    ));
  });

  it('adds a new guest via the reused GuestFieldSection form', async () => {
    vi.mocked(stayService.getStayById)
      .mockResolvedValueOnce(stayWith([primaryGuest]))
      .mockResolvedValueOnce(stayWith([primaryGuest, secondaryGuest]));
    vi.mocked(stayService.addGuest).mockResolvedValue(secondaryGuest);

    render(<StayGuestManagerDialog stayId={STAY_ID} onClose={vi.fn()} />);

    await waitFor(() => expect(screen.getByText('Rossi Mario')).toBeInTheDocument());
    fireEvent.click(screen.getByRole('button', { name: 'btn_add_guest' }));

    expect(await screen.findByText('guest_number:{"number":1}')).toBeInTheDocument();
  });
});
