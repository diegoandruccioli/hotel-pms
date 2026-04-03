import { guestService } from './guestService';
import { reservationService } from './reservationService';
import { stayService } from './stayService';
import { inventoryService } from './inventoryService';
import { billingReportService } from './billingReportService';

export interface DashboardStats {
  totalGuests: number;
  activeReservationsPercentage: number;
  currentStaysPercentage: number;
  pendingRevenue: number;
}

export const dashboardService = {
  getDashboardStats: async (): Promise<DashboardStats> => {
    // We use a wide date range to get all invoices for the pending revenue calculation
    const [guests, reservations, stays, rooms, report] = await Promise.all([
      guestService.getAllGuests(),
      reservationService.getAllReservations(),
      stayService.getAllStays(),
      inventoryService.getAllRooms(),
      billingReportService.getOwnerFinancialReport('2000-01-01', '2099-12-31')
    ]).catch(error => {
      console.error('Error fetching dashboard data:', error);
      throw error;
    });

    const totalRooms = rooms.totalElements > 0 ? rooms.totalElements : 1; // avoid division by zero

    const activeReservations = reservations.filter(r => r.active || r.status === 'CONFIRMED').length;
    const activeReservationsPercentage = (activeReservations / totalRooms) * 100;

    const currentStays = stays.filter(s => s.status === 'CHECKED_IN').length;
    const currentStaysPercentage = (currentStays / totalRooms) * 100;

    const pendingRevenue = report.invoices
      .filter(i => i.status === 'ISSUED')
      .reduce((sum, inv) => sum + inv.totalAmount, 0);

    return {
      totalGuests: guests.length,
      activeReservationsPercentage,
      currentStaysPercentage,
      pendingRevenue,
    };
  }
};
