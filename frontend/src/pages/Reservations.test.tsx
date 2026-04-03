import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { Reservations } from './Reservations';
import { reservationService } from '../services/reservationService';
import { inventoryService } from '../services/inventoryService';

vi.mock('react-i18next', () => ({
  useTranslation: () => ({ t: (key: string) => key, i18n: { language: 'en' } }),
  initReactI18next: { type: '3rdParty', init: vi.fn() },
}));

vi.mock('../services/reservationService', () => ({
  reservationService: { getAllReservations: vi.fn() },
}));

vi.mock('../services/inventoryService', () => ({
  inventoryService: { getAllRooms: vi.fn() },
}));

describe('Reservations', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    vi.mocked(inventoryService.getAllRooms).mockResolvedValue({ content: [], totalElements: 0 } as never);
  });

  it('should show loading spinner initially', () => {
    vi.mocked(reservationService.getAllReservations).mockReturnValue(new Promise(() => {}));
    render(
      <MemoryRouter>
        <Reservations />
      </MemoryRouter>
    );
    expect(screen.getByText('progress_activity')).toBeInTheDocument();
  });

  it('should render reservations on success', async () => {
    vi.mocked(reservationService.getAllReservations).mockResolvedValueOnce([
      { id: '1', guestId: 'g1', guestFullName: 'John Doe', checkInDate: '2026-04-01', checkOutDate: '2026-04-03', status: 'CONFIRMED' },
    ] as never);

    render(
      <MemoryRouter>
        <Reservations />
      </MemoryRouter>
    );

    await waitFor(() => {
      expect(screen.getByText('John Doe')).toBeInTheDocument();
    });
  });

  it('should show empty state message when no reservations', async () => {
    vi.mocked(reservationService.getAllReservations).mockResolvedValueOnce([]);
    render(
      <MemoryRouter>
        <Reservations />
      </MemoryRouter>
    );

    await waitFor(() => {
      expect(screen.getByText('no_reservations_found')).toBeInTheDocument();
    });
  });

  it('should show error on failure', async () => {
    vi.mocked(reservationService.getAllReservations).mockRejectedValueOnce(new Error('Network error'));
    render(
      <MemoryRouter>
        <Reservations />
      </MemoryRouter>
    );

    await waitFor(() => {
      expect(screen.getByText('error_loading_reservations')).toBeInTheDocument();
    });
  });
});
