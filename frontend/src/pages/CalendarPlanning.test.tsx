import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import { axe } from 'vitest-axe';
import { CalendarPlanning } from './CalendarPlanning';

// Default view is 'planning' → PlanningBoard renders; month view → Calendar renders
vi.mock('react-big-calendar', () => ({
  Calendar: () => <div data-testid="rbc-calendar" />,
  dateFnsLocalizer: () => ({}),
}));

vi.mock('@/pages/PlanningBoard', () => ({
  default: () => <div data-testid="planning-board">PlanningBoard</div>,
}));

vi.mock('react-i18next', () => ({
  useTranslation: () => ({ t: (key: string) => key, i18n: { language: 'en' } }),
  initReactI18next: { type: '3rdParty', init: vi.fn() },
}));

vi.mock('../services/reservationService', () => ({
  reservationService: { getAllReservations: vi.fn() },
}));

vi.mock('../services/inventoryService', () => ({
  inventoryService: { getAllRooms: vi.fn(), updateRoomStatus: vi.fn() },
}));

vi.mock('../store/toastStore', () => ({
  useToastStore: (selector: unknown) =>
    (selector as (s: { addToast: () => void }) => unknown)({ addToast: vi.fn() }),
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
});
