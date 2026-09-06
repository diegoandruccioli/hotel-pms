/**
 * A single night-audit closing record — mirrors `NightAuditRunResponse` in
 * frontdesk-service. `status=RUNNING` is never actually observed by the
 * frontend: the run completes synchronously within the triggering request.
 */
export interface NightAuditRunResponse {
  id: string;
  businessDate: string;
  status: 'RUNNING' | 'COMPLETED' | 'FAILED';
  startedAt: string;
  completedAt: string | null;
  runBy: string;
  arrivals: number | null;
  departures: number | null;
  guestsInHouse: number | null;
  currentStays: number | null;
  availableRooms: number | null;
  noShowsMarked: number | null;
  cashByMethod: Array<{ paymentMethod: string; total: number }>;
  cashSummaryDegraded: boolean;
  failureReason: string | null;
}
