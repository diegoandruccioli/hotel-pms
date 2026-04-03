import { describe, it, expect, vi, beforeEach } from 'vitest';
import { dashboardService } from './dashboardService';
import { guestService } from './guestService';
import { reservationService } from './reservationService';
import { stayService } from './stayService';
import { inventoryService } from './inventoryService';
import { billingReportService } from './billingReportService';

vi.mock('./guestService');
vi.mock('./reservationService');
vi.mock('./stayService');
vi.mock('./inventoryService');
vi.mock('./billingReportService');

describe('dashboardService', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('should aggregate stats from all services', async () => {
    vi.mocked(guestService.getAllGuests).mockResolvedValueOnce([
      { id: '1' }, { id: '2' }, { id: '3' },
    ] as never);

    vi.mocked(reservationService.getAllReservations).mockResolvedValueOnce([
      { id: 'r1', status: 'CONFIRMED', active: true },
      { id: 'r2', status: 'CANCELLED', active: false },
    ] as never);

    vi.mocked(stayService.getAllStays).mockResolvedValueOnce([
      { id: 's1', status: 'CHECKED_IN' },
      { id: 's2', status: 'CHECKED_OUT' },
    ] as never);

    vi.mocked(inventoryService.getAllRooms).mockResolvedValueOnce({
      content: [{ id: 'rm1' }, { id: 'rm2' }, { id: 'rm3' }, { id: 'rm4' }],
      totalElements: 4,
    } as never);

    vi.mocked(billingReportService.getOwnerFinancialReport).mockResolvedValueOnce({
      invoices: [
        { status: 'ISSUED', totalAmount: 300 },
        { status: 'PAID', totalAmount: 200 },
        { status: 'ISSUED', totalAmount: 150 },
      ],
    } as never);

    const result = await dashboardService.getDashboardStats();

    expect(result.totalGuests).toBe(3);
    expect(result.activeReservationsPercentage).toBe(25); // 1 active / 4 rooms
    expect(result.currentStaysPercentage).toBe(25); // 1 checked-in / 4 rooms
    expect(result.pendingRevenue).toBe(450); // 300 + 150 ISSUED
  });

  it('should handle zero rooms gracefully', async () => {
    vi.mocked(guestService.getAllGuests).mockResolvedValueOnce([]);
    vi.mocked(reservationService.getAllReservations).mockResolvedValueOnce([]);
    vi.mocked(stayService.getAllStays).mockResolvedValueOnce([]);
    vi.mocked(inventoryService.getAllRooms).mockResolvedValueOnce({ content: [], totalElements: 0 } as never);
    vi.mocked(billingReportService.getOwnerFinancialReport).mockResolvedValueOnce({
      invoices: [],
    } as never);

    const result = await dashboardService.getDashboardStats();

    expect(result.totalGuests).toBe(0);
    expect(result.activeReservationsPercentage).toBe(0);
    expect(result.currentStaysPercentage).toBe(0);
    expect(result.pendingRevenue).toBe(0);
  });
});
