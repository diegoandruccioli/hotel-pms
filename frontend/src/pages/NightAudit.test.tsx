import type { ReactElement } from 'react';
import { describe, it, expect, vi, beforeEach } from 'vitest';
import { screen, waitFor, fireEvent } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { renderWithQuery } from '../test-utils';
import { mockAxiosErrorWithDetail } from '../test-utils';
import { NightAudit } from './NightAudit';
import { nightAuditService } from '../services';
import { reservationService } from '../services';
import { stayService } from '../services';
import { useToastStore } from '../store';

// The pre-check widget links to /reservations and /stays (react-router-dom
// <Link>), so every render needs a Router context now, not just a QueryClient.
const render = (ui: ReactElement) => renderWithQuery(<MemoryRouter>{ui}</MemoryRouter>);

vi.mock('react-i18next', () => {
  const t = (key: string, opts?: Record<string, unknown>) =>
    opts ? `${key} ${JSON.stringify(opts)}` : key;
  return {
    useTranslation: () => ({ t, i18n: { language: 'en' } }),
    initReactI18next: { type: '3rdParty', init: vi.fn() },
  };
});

vi.mock('../services/nightAuditService', () => ({
  nightAuditService: {
    run: vi.fn(),
    getHistory: vi.fn(),
  },
}));

vi.mock('../services/reservationService', () => ({
  reservationService: { searchReservations: vi.fn() },
}));

vi.mock('../services/stayService', () => ({
  stayService: { searchStays: vi.fn() },
}));

vi.mock('../store/toastStore', () => ({
  useToastStore: vi.fn(),
}));

vi.mock('focus-trap-react', () => ({
  default: ({ children }: { children: React.ReactNode }) => <>{children}</>,
}));

const COMPLETED_RUN = {
  id: 'run-1',
  businessDate: '2026-06-15',
  status: 'COMPLETED',
  startedAt: '2026-06-16T03:30:00',
  completedAt: '2026-06-16T03:30:05',
  runBy: 'admin',
  arrivals: 3,
  departures: 2,
  guestsInHouse: 5,
  currentStays: 4,
  availableRooms: 10,
  noShowsMarked: 1,
  cashByMethod: [{ paymentMethod: 'CASH', total: 150 }],
  cashSummaryDegraded: false,
  failureReason: null,
};

const FAILED_RUN = {
  ...COMPLETED_RUN,
  id: 'run-2',
  businessDate: '2026-06-14',
  status: 'FAILED',
  failureReason: 'DAY_SHEET_BOOM',
};

const page = (content: unknown[], totalPages = 1) => ({ content, totalPages, totalElements: content.length });

const mockAddToast = vi.fn();

describe('NightAudit', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    vi.mocked(useToastStore).mockReturnValue(mockAddToast);
    vi.mocked(reservationService.searchReservations).mockResolvedValue(page([]) as never);
    vi.mocked(stayService.searchStays).mockResolvedValue(page([]) as never);
  });

  it('should show loading state initially', () => {
    vi.mocked(nightAuditService.getHistory).mockReturnValue(new Promise(() => {}));
    render(<NightAudit />);
    expect(screen.getByText('progress_activity')).toBeInTheDocument();
  });

  it('should render history rows on success', async () => {
    vi.mocked(nightAuditService.getHistory).mockResolvedValueOnce(page([COMPLETED_RUN]) as never);
    render(<NightAudit />);

    await waitFor(() => expect(screen.getByText('2026-06-15')).toBeInTheDocument());
  });

  it('should show an error state when history fails to load', async () => {
    vi.mocked(nightAuditService.getHistory).mockRejectedValueOnce(new Error('boom'));
    render(<NightAudit />);

    await waitFor(() => expect(screen.getAllByText('night_audit_load_failed').length).toBeGreaterThan(0));
  });

  it('should open the run confirmation dialog', async () => {
    vi.mocked(nightAuditService.getHistory).mockResolvedValueOnce(page([]) as never);
    render(<NightAudit />);

    await waitFor(() => expect(screen.getByText('night_audit_no_runs_found')).toBeInTheDocument());
    fireEvent.click(screen.getByRole('button', { name: 'night_audit_run_action' }));

    expect(screen.getByText(/night_audit_run_confirm/)).toBeInTheDocument();
  });

  it('should run the audit and show a success toast on confirm', async () => {
    vi.mocked(nightAuditService.getHistory)
      .mockResolvedValueOnce(page([]) as never)
      .mockResolvedValueOnce(page([COMPLETED_RUN]) as never);
    vi.mocked(nightAuditService.run).mockResolvedValueOnce(COMPLETED_RUN as never);
    render(<NightAudit />);

    await waitFor(() => expect(screen.getByText('night_audit_no_runs_found')).toBeInTheDocument());
    fireEvent.click(screen.getByRole('button', { name: 'night_audit_run_action' }));
    fireEvent.click(screen.getByRole('button', { name: 'confirm' }));

    await waitFor(() => {
      expect(nightAuditService.run).toHaveBeenCalled();
      expect(mockAddToast).toHaveBeenCalledWith(
        expect.stringContaining('night_audit_run_success'), 'success',
      );
    });
  });

  it('should show a failure toast when the run completes as FAILED', async () => {
    vi.mocked(nightAuditService.getHistory).mockResolvedValue(page([]) as never);
    vi.mocked(nightAuditService.run).mockResolvedValueOnce(FAILED_RUN as never);
    render(<NightAudit />);

    await waitFor(() => expect(screen.getByText('night_audit_no_runs_found')).toBeInTheDocument());
    fireEvent.click(screen.getByRole('button', { name: 'night_audit_run_action' }));
    fireEvent.click(screen.getByRole('button', { name: 'confirm' }));

    await waitFor(() => {
      expect(mockAddToast).toHaveBeenCalledWith(
        expect.stringContaining('night_audit_run_failed_detail'), 'error',
      );
    });
  });

  it('shows the backend detail instead of the generic fallback when the run request itself fails', async () => {
    vi.mocked(nightAuditService.getHistory).mockResolvedValue(page([]) as never);
    vi.mocked(nightAuditService.run).mockRejectedValueOnce(
      mockAxiosErrorWithDetail('NIGHT_AUDIT_ALREADY_CLOSED', 409),
    );
    render(<NightAudit />);

    await waitFor(() => expect(screen.getByText('night_audit_no_runs_found')).toBeInTheDocument());
    fireEvent.click(screen.getByRole('button', { name: 'night_audit_run_action' }));
    fireEvent.click(screen.getByRole('button', { name: 'confirm' }));

    await waitFor(() => expect(mockAddToast).toHaveBeenCalledWith('NIGHT_AUDIT_ALREADY_CLOSED', 'error'));
  });

  it('should open the detail dialog with cash breakdown for a COMPLETED run', async () => {
    vi.mocked(nightAuditService.getHistory).mockResolvedValueOnce(page([COMPLETED_RUN]) as never);
    render(<NightAudit />);

    await waitFor(() => expect(screen.getByText('2026-06-15')).toBeInTheDocument());
    fireEvent.click(screen.getByRole('button', { name: 'view' }));

    expect(screen.getByText('CASH')).toBeInTheDocument();
  });

  it('shows the pre-check banner when arrivals and departures are still pending', async () => {
    vi.mocked(nightAuditService.getHistory).mockResolvedValueOnce(page([]) as never);
    vi.mocked(reservationService.searchReservations).mockResolvedValue({
      content: [], totalPages: 1, totalElements: 2,
    } as never);
    vi.mocked(stayService.searchStays).mockResolvedValue(
      page([
        { id: 's1', expectedCheckOutDate: '2026-06-14' },
        { id: 's2', expectedCheckOutDate: '2099-01-01' }, // not due yet — excluded
      ]) as never,
    );
    render(<NightAudit />);

    await waitFor(() => expect(screen.getByText('night_audit_no_runs_found')).toBeInTheDocument());
    expect(screen.getByText(/night_audit_precheck_pending_arrivals.*"count":2/)).toBeInTheDocument();
    expect(screen.getByText(/night_audit_precheck_pending_departures.*"count":1/)).toBeInTheDocument();
  });

  it('hides the pre-check banner once nothing is pending', async () => {
    vi.mocked(nightAuditService.getHistory).mockResolvedValueOnce(page([]) as never);
    render(<NightAudit />);

    await waitFor(() => expect(screen.getByText('night_audit_no_runs_found')).toBeInTheDocument());
    expect(screen.queryByText(/night_audit_precheck_pending_arrivals/)).not.toBeInTheDocument();
    expect(screen.queryByText(/night_audit_precheck_pending_departures/)).not.toBeInTheDocument();
  });

  it('should show the failure reason in the detail dialog for a FAILED run', async () => {
    vi.mocked(nightAuditService.getHistory).mockResolvedValueOnce(page([FAILED_RUN]) as never);
    render(<NightAudit />);

    await waitFor(() => expect(screen.getByText('2026-06-14')).toBeInTheDocument());
    fireEvent.click(screen.getByRole('button', { name: 'view' }));

    expect(screen.getByText('DAY_SHEET_BOOM')).toBeInTheDocument();
  });
});
