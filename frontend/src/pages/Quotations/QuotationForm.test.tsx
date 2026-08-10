import type { ChangeEvent } from 'react';
import { render, screen, waitFor, fireEvent } from '@testing-library/react';
import { MemoryRouter, Routes, Route } from 'react-router-dom';
/* eslint-disable react-perf/jsx-no-new-array-as-prop, react-perf/jsx-no-new-function-as-prop -- test-only mock components, not the real perf-sensitive render path */
import { describe, it, expect, vi, beforeEach } from 'vitest';
import { axe } from 'vitest-axe';
import { QuotationForm } from './QuotationForm';
import { inventoryService } from '../../services/inventoryService';
import { reservationService } from '../../services/reservationService';
import { quotationService } from '../../services/quotationService';
import { guestService } from '../../services/guestService';

vi.mock('../../services/inventoryService');
vi.mock('../../services/reservationService');
vi.mock('../../services/guestService');
vi.mock('../../services/quotationService');

interface RoomMockProps {
  onCheckInChange: (v: string) => void;
  onCheckOutChange: (v: string) => void;
  onToggleRoom: (roomId: string) => void;
  selectedRoomIds: string[];
}

function RoomSelectionMock({ onCheckInChange, onCheckOutChange, onToggleRoom, selectedRoomIds }: RoomMockProps) {
  const handleCheckIn = (e: ChangeEvent<HTMLInputElement>) => onCheckInChange(e.target.value);
  const handleCheckOut = (e: ChangeEvent<HTMLInputElement>) => onCheckOutChange(e.target.value);
  const handleToggle = () => onToggleRoom('r1');
  return (
    <div data-testid="room-mock">
      <label htmlFor="mock-checkin">Mock Check-in</label>
      <input id="mock-checkin" onChange={handleCheckIn} />
      <label htmlFor="mock-checkout">Mock Check-out</label>
      <input id="mock-checkout" onChange={handleCheckOut} />
      <button type="button" onClick={handleToggle}>Toggle Room r1</button>
      <span>Selected: {selectedRoomIds.join(',')}</span>
    </div>
  );
}

vi.mock('../Reservations/RoomSelection', () => ({
  RoomSelection: (props: RoomMockProps) => RoomSelectionMock(props),
}));

const stableT = (key: string, opts?: Record<string, unknown>) => {
  if (opts && typeof opts.amount !== 'undefined') return `${key}:${opts.amount}`;
  return key;
};
vi.mock('react-i18next', () => ({
  useTranslation: () => ({ t: stableT, i18n: { language: 'en' } }),
  initReactI18next: { type: '3rdParty', init: vi.fn() },
}));

const mockNavigate = vi.fn();
vi.mock('react-router-dom', async (importOriginal) => {
  const actual = await importOriginal<Record<string, unknown>>();
  return { ...actual, useNavigate: () => mockNavigate };
});

vi.mock('../../store/toastStore', () => ({
  useToastStore: (sel: unknown) =>
    (sel as (s: { addToast: () => void }) => unknown)({ addToast: vi.fn() }),
}));

describe('QuotationForm', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    vi.mocked(inventoryService.getAllRooms).mockResolvedValue({
      content: [], totalElements: 0,
    } as never);
    vi.mocked(reservationService.getAllReservations).mockResolvedValue([]);
    vi.mocked(inventoryService.getAvailableRooms).mockResolvedValue([]);
  });

  const renderForm = () => render(
    <MemoryRouter initialEntries={['/quotations/new']}>
      <Routes>
        <Route path="/quotations/new" element={<QuotationForm />} />
      </Routes>
    </MemoryRouter>
  );

  const renderEditForm = () => render(
    <MemoryRouter initialEntries={['/quotations/q1/edit']}>
      <Routes>
        <Route path="/quotations/:id/edit" element={<QuotationForm />} />
      </Routes>
    </MemoryRouter>
  );

  it('renders heading and room mock after data loads', async () => {
    renderForm();
    await waitFor(() => {
      expect(screen.getByText('new_quotation')).toBeInTheDocument();
      expect(screen.getByTestId('room-mock')).toBeInTheDocument();
    });
  });

  it('defaults to existing-guest recipient mode', async () => {
    renderForm();
    await waitFor(() => expect(screen.getByText('guests:search_guest_placeholder')).toBeInTheDocument());
  });

  it('toggle_new_prospect switches to prospect fields', async () => {
    renderForm();
    await waitFor(() => expect(screen.getByText('toggle_new_prospect')).toBeInTheDocument());
    fireEvent.click(screen.getByText('toggle_new_prospect'));
    expect(screen.getByLabelText('label_prospect_first_name')).toBeInTheDocument();
    expect(screen.getByLabelText('label_prospect_last_name')).toBeInTheDocument();
    expect(screen.getByLabelText('label_prospect_email')).toBeInTheDocument();
  });

  it('blocks submission and shows an error when no recipient and no rooms are selected', async () => {
    renderForm();
    await waitFor(() => expect(screen.getByTestId('room-mock')).toBeInTheDocument());
    fireEvent.click(screen.getByText('common:save'));
    expect(await screen.findByText('err_select_recipient')).toBeInTheDocument();
    expect(quotationService.createQuotation).not.toHaveBeenCalled();
  });

  it('creates a quotation for a prospect and navigates to the list', async () => {
    vi.mocked(quotationService.createQuotation).mockResolvedValue({ id: 'q1' } as never);
    renderForm();
    await waitFor(() => expect(screen.getByTestId('room-mock')).toBeInTheDocument());

    fireEvent.click(screen.getByText('toggle_new_prospect'));
    fireEvent.change(screen.getByLabelText('label_prospect_first_name'), { target: { value: 'Mario' } });
    fireEvent.change(screen.getByLabelText('label_prospect_last_name'), { target: { value: 'Rossi' } });
    fireEvent.change(screen.getByLabelText('label_prospect_email'), { target: { value: 'mario@example.com' } });

    fireEvent.change(screen.getByLabelText('Mock Check-in'), { target: { value: '2026-09-01' } });
    fireEvent.change(screen.getByLabelText('Mock Check-out'), { target: { value: '2026-09-03' } });
    fireEvent.click(screen.getByText('Toggle Room r1'));

    fireEvent.click(screen.getByText('common:save'));

    await waitFor(() => expect(quotationService.createQuotation).toHaveBeenCalledWith(expect.objectContaining({
      guestId: null,
      prospectFirstName: 'Mario',
      prospectLastName: 'Rossi',
      prospectEmail: 'mario@example.com',
      checkInDate: '2026-09-01',
      checkOutDate: '2026-09-03',
      options: [{ label: 'Opzione 1', roomIds: ['r1'] }],
    })));
    expect(mockNavigate).toHaveBeenCalledWith('/quotations');
  });

  it('supports adding a second option, selecting rooms independently per option, and submits both', async () => {
    vi.mocked(quotationService.createQuotation).mockResolvedValue({ id: 'q1' } as never);
    renderForm();
    await waitFor(() => expect(screen.getByTestId('room-mock')).toBeInTheDocument());

    fireEvent.click(screen.getByText('toggle_new_prospect'));
    fireEvent.change(screen.getByLabelText('label_prospect_first_name'), { target: { value: 'Mario' } });
    fireEvent.change(screen.getByLabelText('label_prospect_last_name'), { target: { value: 'Rossi' } });
    fireEvent.change(screen.getByLabelText('label_prospect_email'), { target: { value: 'mario@example.com' } });
    fireEvent.change(screen.getByLabelText('Mock Check-in'), { target: { value: '2026-09-01' } });
    fireEvent.change(screen.getByLabelText('Mock Check-out'), { target: { value: '2026-09-03' } });

    fireEvent.click(screen.getByText('Toggle Room r1'));

    fireEvent.click(screen.getByText('action_add_option'));
    fireEvent.change(screen.getByLabelText('label_option_name'), { target: { value: 'Suite deluxe' } });
    fireEvent.click(screen.getByText('Toggle Room r1'));

    fireEvent.click(screen.getByText('common:save'));

    await waitFor(() => expect(quotationService.createQuotation).toHaveBeenCalledWith(expect.objectContaining({
      options: [
        { label: 'Opzione 1', roomIds: ['r1'] },
        { label: 'Suite deluxe', roomIds: ['r1'] },
      ],
    })));
  });

  it('removes an option via its tab close button', async () => {
    renderForm();
    await waitFor(() => expect(screen.getByTestId('room-mock')).toBeInTheDocument());

    fireEvent.click(screen.getByText('action_add_option'));
    const removeButtons = screen.getAllByLabelText(/^Rimuovi /);
    expect(removeButtons.length).toBeGreaterThan(0);
    fireEvent.click(removeButtons[0]);
    expect(screen.queryAllByLabelText(/^Rimuovi /).length).toBe(0);
  });

  it('falls back to basePrice x nights when a selected room has no resolved price', async () => {
    vi.mocked(inventoryService.getAllRooms).mockResolvedValue({
      content: [{
        id: 'r1',
        hotelId: 'h1',
        roomNumber: '101',
        roomType: { id: 'rt1', name: 'Standard', maxOccupancy: 2, basePrice: 100, active: true, createdAt: '', updatedAt: '' },
        status: 'CLEAN',
        active: true,
        createdAt: '',
        updatedAt: '',
      }],
      totalElements: 1,
    } as never);
    // getAvailableRooms deliberately returns no entry for r1 (e.g. stale/conflicting test data) —
    // resolvedPrices.get('r1') is undefined, so the total must fall back to basePrice x nights
    // instead of silently contributing 0.
    vi.mocked(inventoryService.getAvailableRooms).mockResolvedValue([]);

    renderForm();
    await waitFor(() => expect(screen.getByTestId('room-mock')).toBeInTheDocument());

    fireEvent.change(screen.getByLabelText('Mock Check-in'), { target: { value: '2026-09-01' } });
    fireEvent.change(screen.getByLabelText('Mock Check-out'), { target: { value: '2026-09-04' } });
    fireEvent.click(screen.getByText('Toggle Room r1'));

    await waitFor(() => expect(screen.getByText('quotation_total:€ 300.00')).toBeInTheDocument());
  });

  it('passes axe accessibility check', async () => {
    const { container } = renderForm();
    await waitFor(() => screen.getByTestId('room-mock'));
    expect(await axe(container)).toHaveNoViolations();
  });

  describe('edit mode', () => {
    const existingQuotation = {
      id: 'q1',
      guestId: 'g1',
      guestFullName: 'Mario Rossi',
      prospectEmail: null,
      checkInDate: '2026-09-01',
      checkOutDate: '2026-09-03',
      expectedGuests: 2,
      status: 'DRAFT',
      validUntil: '2026-08-25',
      totalPrice: 200,
      options: [{
        id: 'opt1',
        label: 'Opzione 1',
        position: 0,
        totalPrice: 200,
        lineItems: [{ id: 'li1', roomId: 'r1', roomNumber: '101', roomTypeName: 'Standard', price: 200 }],
      }],
      acceptedOptionId: null,
      sendFailed: false,
      sendFailureReason: null,
      createdAt: '2026-08-01T00:00:00',
      updatedAt: '2026-08-01T00:00:00',
    };

    beforeEach(() => {
      vi.mocked(quotationService.getQuotationById).mockResolvedValue(existingQuotation as never);
      vi.mocked(guestService.getGuestById).mockResolvedValue(
        { id: 'g1', firstName: 'Mario', lastName: 'Rossi', email: 'mario@example.com' } as never,
      );
    });

    it('shows the edit title and pre-fills the selected room from the existing quotation', async () => {
      renderEditForm();
      await waitFor(() => {
        expect(screen.getByText('edit_quotation')).toBeInTheDocument();
        expect(screen.getByText('Selected: r1')).toBeInTheDocument();
      });
    });

    it('calls updateQuotation instead of createQuotation and navigates to the detail page', async () => {
      vi.mocked(quotationService.updateQuotation).mockResolvedValue({ id: 'q1' } as never);
      renderEditForm();
      await waitFor(() => expect(screen.getByText('Selected: r1')).toBeInTheDocument());

      fireEvent.click(screen.getByText('common:save'));

      await waitFor(() => expect(quotationService.updateQuotation).toHaveBeenCalledWith('q1', expect.objectContaining({
        guestId: 'g1',
        checkInDate: '2026-09-01',
        checkOutDate: '2026-09-03',
        options: [{ label: 'Opzione 1', roomIds: ['r1'] }],
      })));
      expect(quotationService.createQuotation).not.toHaveBeenCalled();
      expect(mockNavigate).toHaveBeenCalledWith('/quotations/q1');
    });
  });
});
