import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import { axe } from 'vitest-axe';
import { CalendarPlanning } from './CalendarPlanning';

// Capture last props passed to the mocked Calendar / PlanningBoard so tests
// can invoke callbacks (eventPropGetter, onReservationMove) that the real
// child components would normally trigger via user interaction.
const lastCalendarProps: { current: Record<string, unknown> } = { current: {} };
const lastPlanningBoardProps: { current: Record<string, unknown> } = { current: {} };

// Default view is 'planning' → PlanningBoard renders; month view → Calendar renders
vi.mock('react-big-calendar', () => ({
  Calendar: (props: Record<string, unknown>) => {
    lastCalendarProps.current = props;
    return <div data-testid="rbc-calendar" />;
  },
  dateFnsLocalizer: () => ({}),
}));

vi.mock('@/pages/PlanningBoard', () => ({
  default: (props: Record<string, unknown>) => {
    lastPlanningBoardProps.current = props;
    return <div data-testid="planning-board">PlanningBoard</div>;
  },
}));

// Stable references — react-i18next's real useTranslation() memoizes `t`,
// and CalendarPlanning's loadData useCallback depends on `t`; a fresh
// function/object literal here would change identity every render and
// re-trigger the load effect in an infinite loop.
const stableT = (key: string) => key;
const stableI18n = { language: 'en' };
vi.mock('react-i18next', () => ({
  useTranslation: () => ({ t: stableT, i18n: stableI18n }),
  initReactI18next: { type: '3rdParty', init: vi.fn() },
}));

vi.mock('../services/reservationService', () => ({
  reservationService: { getAllReservations: vi.fn(), updateReservation: vi.fn() },
}));

vi.mock('../services/inventoryService', () => ({
  inventoryService: { getAllRooms: vi.fn(), updateRoomStatus: vi.fn() },
}));

const mockAddToast = vi.fn();
vi.mock('../store/toastStore', () => ({
  useToastStore: (selector: unknown) =>
    (selector as (s: { addToast: () => void }) => unknown)({ addToast: mockAddToast }),
}));

import { reservationService } from '../services/reservationService';
import { inventoryService } from '../services/inventoryService';

describe('CalendarPlanning', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    vi.mocked(reservationService.getAllReservations).mockResolvedValue([] as never);
    vi.mocked(inventoryService.getAllRooms).mockResolvedValue({
      content: [], totalElements: 0,
    } as never);
  });

  it('renders without crashing and shows planning board by default', async () => {
    render(<CalendarPlanning />);
    await waitFor(() => {
      expect(screen.getByTestId('planning-board')).toBeInTheDocument();
    });
  });

  it('renders month navigation buttons', async () => {
    render(<CalendarPlanning />);
    await waitFor(() => {
      expect(screen.getByText('chevron_left')).toBeInTheDocument();
      expect(screen.getByText('chevron_right')).toBeInTheDocument();
    });
  });

  it('renders view-toggle buttons for planning and month views', async () => {
    render(<CalendarPlanning />);
    await waitFor(() => {
      expect(screen.getByText('view_planning')).toBeInTheDocument();
      expect(screen.getByText('view_month')).toBeInTheDocument();
    });
  });

  it('switches to calendar view when month button clicked', async () => {
    render(<CalendarPlanning />);
    await waitFor(() => screen.getByText('view_month'));
    fireEvent.click(screen.getByText('view_month'));
    await waitFor(() => {
      expect(screen.getByTestId('rbc-calendar')).toBeInTheDocument();
    });
  });

  it('calls getAllReservations and getAllRooms on mount', async () => {
    render(<CalendarPlanning />);
    await waitFor(() => {
      expect(reservationService.getAllReservations).toHaveBeenCalledTimes(1);
      expect(inventoryService.getAllRooms).toHaveBeenCalledTimes(1);
    });
  });

  it('has no critical accessibility violations', async () => {
    const { container } = render(<CalendarPlanning />);
    await waitFor(() => screen.getByTestId('planning-board'));
    const results = await axe(container);
    expect((results as unknown as { violations: { impact: string }[] }).violations
      .filter((v) => v.impact === 'critical')).toHaveLength(0);
  });

  it('navigates to the previous and next month', async () => {
    render(<CalendarPlanning />);
    await waitFor(() => screen.getByText('chevron_left'));
    const monthLabelBefore = document.querySelector('.capitalize')?.textContent;

    fireEvent.click(screen.getByLabelText('prev_month'));
    const monthLabelAfterPrev = document.querySelector('.capitalize')?.textContent;
    expect(monthLabelAfterPrev).not.toBe(monthLabelBefore);

    fireEvent.click(screen.getByLabelText('next_month'));
    fireEvent.click(screen.getByLabelText('next_month'));
    const monthLabelAfterNext = document.querySelector('.capitalize')?.textContent;
    expect(monthLabelAfterNext).not.toBe(monthLabelAfterPrev);
  });

  it('jumps to the selected month via the native month picker', async () => {
    render(<CalendarPlanning />);
    await waitFor(() => screen.getByTitle('select_month'));

    fireEvent.change(screen.getByTitle('select_month'), { target: { value: '2027-03' } });

    expect(screen.getByText('2027')).toBeInTheDocument();
  });

  it('ignores an empty value from the month picker', async () => {
    render(<CalendarPlanning />);
    await waitFor(() => screen.getByTitle('select_month'));
    const yearBefore = screen.getAllByText(/^\d{4}$/)[0].textContent;

    fireEvent.change(screen.getByTitle('select_month'), { target: { value: '' } });

    expect(screen.getAllByText(/^\d{4}$/)[0].textContent).toBe(yearBefore);
  });

  it('shows an error state with a retry button when loading fails, and reloads on retry', async () => {
    vi.mocked(reservationService.getAllReservations).mockRejectedValueOnce(new Error('network down'));
    render(<CalendarPlanning />);

    await waitFor(() => {
      expect(screen.getByText('error_loading_reservations')).toBeInTheDocument();
      expect(screen.getByText('network down')).toBeInTheDocument();
    });

    vi.mocked(reservationService.getAllReservations).mockResolvedValueOnce([] as never);
    fireEvent.click(screen.getByText('try_again'));

    await waitFor(() => {
      expect(screen.getByTestId('planning-board')).toBeInTheDocument();
    });
  });

  it('falls back to a translated message when the load error has no message', async () => {
    vi.mocked(reservationService.getAllReservations).mockRejectedValueOnce('not an Error instance');
    render(<CalendarPlanning />);

    await waitFor(() => {
      expect(screen.getByText('failed_load_data')).toBeInTheDocument();
    });
  });

  describe('eventPropGetter (month view)', () => {
    const reservation = (status: string) => ({
      id: 'r1', guestId: 'g1', guestFullName: 'Mario Rossi',
      checkInDate: '2026-06-20', checkOutDate: '2026-06-22',
      status, active: true, lineItems: [],
    });

    it.each([
      ['CONFIRMED', '#1A3A5C'],
      ['PENDING', '#5C4300'],
      ['CANCELLED', '#BA1A1A'],
      ['CHECKED_IN', '#2E7D6A'],
      ['CHECKED_OUT', '#73777F'],
      ['UNKNOWN_STATUS', '#1A3A5C'],
    ])('maps status %s to background color %s', async (status, expectedColor) => {
      vi.mocked(reservationService.getAllReservations).mockResolvedValue([reservation(status)] as never);
      render(<CalendarPlanning />);
      await waitFor(() => screen.getByText('view_month'));
      fireEvent.click(screen.getByText('view_month'));
      await waitFor(() => screen.getByTestId('rbc-calendar'));

      const getter = lastCalendarProps.current.eventPropGetter as (e: { resource: { status: string } }) => { style: { backgroundColor: string } };
      const result = getter({ resource: { status } });

      expect(result.style.backgroundColor).toBe(expectedColor);
    });
  });

  describe('handlePlanningBoardDrop (room move via drag-and-drop)', () => {
    const baseReservation = {
      id: 'r1', guestId: 'g1', guestFullName: 'Mario Rossi',
      checkInDate: '2026-06-20', checkOutDate: '2026-06-22', status: 'CONFIRMED',
      expectedGuests: 2, active: true,
      lineItems: [{ roomId: 'room-A', active: true, price: 80 }],
    };
    const overlappingReservation = {
      id: 'r2', guestId: 'g2', guestFullName: 'Anna Bianchi',
      checkInDate: '2026-06-21', checkOutDate: '2026-06-23', status: 'CONFIRMED',
      expectedGuests: 1, active: true,
      lineItems: [{ roomId: 'room-B', active: true, price: 80 }],
    };

    async function renderWithReservations(reservations: unknown[]) {
      vi.mocked(reservationService.getAllReservations).mockResolvedValue(reservations as never);
      render(<CalendarPlanning />);
      await waitFor(() => screen.getByTestId('planning-board'));
    }

    it('blocks the move and shows an error toast when the target room is already occupied for overlapping dates', async () => {
      await renderWithReservations([baseReservation, overlappingReservation]);

      const onMove = lastPlanningBoardProps.current.onReservationMove as (id: string, oldRoom: string, newRoom: string) => Promise<void>;
      await onMove('r1', 'room-A', 'room-B');

      expect(mockAddToast).toHaveBeenCalledWith('room_move_overlap_error', 'error');
      expect(reservationService.updateReservation).not.toHaveBeenCalled();
    });

    it('moves the reservation to the new room and shows a success toast when there is no overlap', async () => {
      await renderWithReservations([baseReservation]);
      vi.mocked(reservationService.updateReservation).mockResolvedValueOnce({} as never);

      const onMove = lastPlanningBoardProps.current.onReservationMove as (id: string, oldRoom: string, newRoom: string) => Promise<void>;
      await onMove('r1', 'room-A', 'room-C');

      await waitFor(() => {
        expect(reservationService.updateReservation).toHaveBeenCalledWith('r1', expect.objectContaining({
          guestId: 'g1',
          lineItems: [{ roomId: 'room-C', price: 80 }],
        }));
      });
      expect(mockAddToast).toHaveBeenCalledWith('room_moved_success', 'success');
    });

    it('rolls back the optimistic move and shows an error toast when the API call fails', async () => {
      await renderWithReservations([baseReservation]);
      vi.mocked(reservationService.updateReservation).mockRejectedValueOnce(new Error('server error'));

      const onMove = lastPlanningBoardProps.current.onReservationMove as (id: string, oldRoom: string, newRoom: string) => Promise<void>;
      await onMove('r1', 'room-A', 'room-C');

      await waitFor(() => {
        expect(mockAddToast).toHaveBeenCalledWith('room_move_failed', 'error');
      });
    });

    it('does nothing when the reservation id is not found', async () => {
      await renderWithReservations([baseReservation]);

      const onMove = lastPlanningBoardProps.current.onReservationMove as (id: string, oldRoom: string, newRoom: string) => Promise<void>;
      await onMove('does-not-exist', 'room-A', 'room-C');

      expect(reservationService.updateReservation).not.toHaveBeenCalled();
      expect(mockAddToast).not.toHaveBeenCalled();
    });

    it('ignores cancelled or inactive reservations when checking for overlap', async () => {
      const cancelledOverlap = { ...overlappingReservation, id: 'r3', status: 'CANCELLED', lineItems: [{ roomId: 'room-C', active: true, price: 80 }] };
      await renderWithReservations([baseReservation, cancelledOverlap]);
      vi.mocked(reservationService.updateReservation).mockResolvedValueOnce({} as never);

      const onMove = lastPlanningBoardProps.current.onReservationMove as (id: string, oldRoom: string, newRoom: string) => Promise<void>;
      await onMove('r1', 'room-A', 'room-C');

      await waitFor(() => expect(reservationService.updateReservation).toHaveBeenCalled());
      expect(mockAddToast).toHaveBeenCalledWith('room_moved_success', 'success');
    });
  });
});
