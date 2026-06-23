import { describe, it, expect, vi, beforeEach } from 'vitest';
import { dashboardService } from './dashboardService';
import { reservationService } from './reservationService';
import { stayService } from './stayService';
import { inventoryService } from './inventoryService';
import { billingReportService } from './billingReportService';

vi.mock('./reservationService');
vi.mock('./stayService');
vi.mock('./inventoryService');
vi.mock('./billingReportService');

const TODAY = new Date().toISOString().slice(0, 10);

const BASE_STAYS_PAGE = {
  totalElements: 0, totalPages: 1, number: 0, size: 20,
  numberOfElements: 0, first: true, last: true, empty: true,
};

describe('dashboardService', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('aggregates today arrivals/departures and room counts for OWNER', async () => {
    vi.mocked(reservationService.getAllReservations).mockResolvedValueOnce([
      { id: 'r1', status: 'CONFIRMED', checkInDate: TODAY,        checkOutDate: '2099-12-31' },
      { id: 'r2', status: 'CONFIRMED', checkInDate: '2000-01-01', checkOutDate: TODAY },
      { id: 'r3', status: 'CANCELLED', checkInDate: TODAY,        checkOutDate: TODAY },
    ] as never);

    vi.mocked(stayService.getAllStays).mockResolvedValueOnce({
      ...BASE_STAYS_PAGE,
      content: [
        { id: 's1', status: 'CHECKED_IN', guests: [{ id: 'g1' }, { id: 'g2' }] },
        { id: 's2', status: 'CHECKED_OUT', guests: [{ id: 'g3' }] },
      ],
      totalElements: 2, numberOfElements: 2, empty: false,
    } as never);

    vi.mocked(inventoryService.getAllRooms).mockResolvedValueOnce({
      content: [
        { id: 'rm1', status: 'CLEAN' },
        { id: 'rm2', status: 'CLEAN' },
        { id: 'rm3', status: 'DIRTY' },
        { id: 'rm4', status: 'OCCUPIED' },
      ],
      totalElements: 4,
    } as never);

    vi.mocked(inventoryService.getAvailableRooms).mockResolvedValueOnce([
      { id: 'rm1', status: 'CLEAN' },
      { id: 'rm2', status: 'CLEAN' },
    ] as never);

    vi.mocked(billingReportService.getOwnerFinancialReport).mockResolvedValueOnce({
      invoices: [
        { status: 'ISSUED', totalAmount: 300 },
        { status: 'PAID',   totalAmount: 200 },
        { status: 'ISSUED', totalAmount: 150 },
      ],
    } as never);

    const result = await dashboardService.getDashboardStats(true);

    expect(result.guestsInHouse).toBe(2);    // s1 is the only CHECKED_IN stay: 2 guests
    expect(result.todayArrivals).toBe(1);    // r1: checkInDate=TODAY, CONFIRMED
    expect(result.todayDepartures).toBe(1);  // r2: checkOutDate=TODAY, CONFIRMED
    expect(result.currentStays).toBe(1);     // s1: CHECKED_IN
    expect(result.availableRooms).toBe(2);   // rm1 + rm2: CLEAN
    expect(result.pendingRevenue).toBe(450); // 300 + 150 ISSUED
    expect(result.rooms).toHaveLength(4);
    expect(stayService.getAllStays).toHaveBeenCalledWith(0, 500);
    expect(billingReportService.getOwnerFinancialReport).toHaveBeenCalledTimes(1);
  });

  it('does not call owner report and returns null pendingRevenue for non-owner', async () => {
    vi.mocked(reservationService.getAllReservations).mockResolvedValueOnce([]);
    vi.mocked(stayService.getAllStays).mockResolvedValueOnce(
      { ...BASE_STAYS_PAGE, content: [] } as never,
    );
    vi.mocked(inventoryService.getAllRooms).mockResolvedValueOnce(
      { content: [], totalElements: 0 } as never,
    );
    vi.mocked(inventoryService.getAvailableRooms).mockResolvedValueOnce([]);

    const result = await dashboardService.getDashboardStats(false);

    expect(result.pendingRevenue).toBeNull();
    expect(billingReportService.getOwnerFinancialReport).not.toHaveBeenCalled();
  });

  it('handles zero rooms and zero stays gracefully', async () => {
    vi.mocked(reservationService.getAllReservations).mockResolvedValueOnce([]);
    vi.mocked(stayService.getAllStays).mockResolvedValueOnce(
      { ...BASE_STAYS_PAGE, content: [] } as never,
    );
    vi.mocked(inventoryService.getAllRooms).mockResolvedValueOnce(
      { content: [], totalElements: 0 } as never,
    );
    vi.mocked(inventoryService.getAvailableRooms).mockResolvedValueOnce([]);
    vi.mocked(billingReportService.getOwnerFinancialReport).mockResolvedValueOnce(
      { invoices: [] } as never,
    );

    const result = await dashboardService.getDashboardStats(true);

    expect(result.guestsInHouse).toBe(0);
    expect(result.todayArrivals).toBe(0);
    expect(result.todayDepartures).toBe(0);
    expect(result.currentStays).toBe(0);
    expect(result.availableRooms).toBe(0);
    expect(result.pendingRevenue).toBe(0);
    expect(result.rooms).toHaveLength(0);
  });

  it('counts only CHECKED_IN stays toward guestsInHouse, ignoring guests on other statuses', async () => {
    vi.mocked(reservationService.getAllReservations).mockResolvedValueOnce([]);
    vi.mocked(stayService.getAllStays).mockResolvedValueOnce({
      ...BASE_STAYS_PAGE,
      content: [
        { id: 's1', status: 'CHECKED_IN', guests: [{ id: 'g1' }] },
        { id: 's2', status: 'CHECKED_IN' }, // no guests array at all
        { id: 's3', status: 'CHECKED_OUT', guests: [{ id: 'g2' }, { id: 'g3' }] },
      ],
      totalElements: 3, numberOfElements: 3, empty: false,
    } as never);
    vi.mocked(inventoryService.getAllRooms).mockResolvedValueOnce(
      { content: [], totalElements: 0 } as never,
    );
    vi.mocked(inventoryService.getAvailableRooms).mockResolvedValueOnce([]);

    const result = await dashboardService.getDashboardStats(false);

    expect(result.guestsInHouse).toBe(1);
    expect(result.currentStays).toBe(2);
  });

  it('returns pendingRevenue=0 when owner report fails (network error)', async () => {
    vi.mocked(reservationService.getAllReservations).mockResolvedValueOnce([]);
    vi.mocked(stayService.getAllStays).mockResolvedValueOnce(
      { ...BASE_STAYS_PAGE, content: [] } as never,
    );
    vi.mocked(inventoryService.getAllRooms).mockResolvedValueOnce(
      { content: [], totalElements: 0 } as never,
    );
    vi.mocked(inventoryService.getAvailableRooms).mockResolvedValueOnce([]);
    vi.mocked(billingReportService.getOwnerFinancialReport).mockRejectedValueOnce(
      new Error('Network error'),
    );

    const result = await dashboardService.getDashboardStats(true);

    expect(result.pendingRevenue).toBe(0);
  });
});
