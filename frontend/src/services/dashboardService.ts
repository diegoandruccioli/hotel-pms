import { guestService } from './guestService';
import { reservationService } from './reservationService';
import { stayService } from './stayService';
import { inventoryService } from './inventoryService';
import { billingReportService } from './billingReportService';
import type { RoomResponse } from '../types/inventory.types';

export interface DashboardStats {
  totalGuests: number;
  todayArrivals: number;
  todayDepartures: number;
  currentStays: number;
  availableRooms: number;
  /** null when caller is not OWNER/ADMIN — owner report endpoint is role-gated */
  pendingRevenue: number | null;
  rooms: RoomResponse[];
}

const getTodayDateString = (): string => {
  const now = new Date();
  return `${now.getFullYear()}-${String(now.getMonth() + 1).padStart(2, '0')}-${String(now.getDate()).padStart(2, '0')}`;
};

export const dashboardService = {
  getDashboardStats: async (isOwnerOrAdmin: boolean): Promise<DashboardStats> => {
    const today = getTodayDateString();

    const [guests, reservations, stays, roomsPage] = await Promise.all([
      guestService.getAllGuests(),
      reservationService.getAllReservations(),
      stayService.getAllStays(),
      inventoryService.getAllRooms(),
    ]).catch((error: unknown) => {
      console.error('Error fetching dashboard data:', error);
      throw error;
    });

    const todayArrivals = reservations.filter(
      r => r.checkInDate === today && (r.status === 'CONFIRMED' || r.status === 'PENDING'),
    ).length;

    const todayDepartures = reservations.filter(
      r => r.checkOutDate === today &&
        (r.status === 'CONFIRMED' || r.status === 'CHECKED_IN' || r.status === 'PENDING'),
    ).length;

    const currentStays = stays.content.filter(s => s.status === 'CHECKED_IN').length;

    const rooms: RoomResponse[] = roomsPage.content ?? [];
    const availableRooms = rooms.filter(r => r.status === 'CLEAN').length;

    let pendingRevenue: number | null = null;
    if (isOwnerOrAdmin) {
      try {
        const report = await billingReportService.getOwnerFinancialReport('2000-01-01', '2099-12-31');
        pendingRevenue = report.invoices
          .filter(i => i.status === 'ISSUED')
          .reduce((sum, inv) => sum + inv.totalAmount, 0);
      } catch {
        pendingRevenue = 0;
      }
    }

    return {
      totalGuests: guests.length,
      todayArrivals,
      todayDepartures,
      currentStays,
      availableRooms,
      pendingRevenue,
      rooms,
    };
  },
};
