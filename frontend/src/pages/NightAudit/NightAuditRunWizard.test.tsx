import type { ReactElement } from 'react';
import { describe, it, expect, vi, beforeEach } from 'vitest';
import { screen, waitFor, fireEvent } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { axe } from 'vitest-axe';
import { renderWithQuery } from '../../test-utils';
import { NightAuditRunWizard } from './NightAuditRunWizard';
import { reservationService, stayService, nightAuditService } from '../../services';
import { useToastStore } from '../../store';

vi.mock('react-i18next', () => ({
  useTranslation: () => ({
    t: (key: string, opts?: Record<string, unknown>) => (opts ? `${key} ${JSON.stringify(opts)}` : key),
    i18n: { language: 'en' },
  }),
  initReactI18next: { type: '3rdParty', init: vi.fn() },
}));
vi.mock('../../services/reservationService', () => ({ reservationService: { searchReservations: vi.fn() } }));
vi.mock('../../services/stayService', () => ({ stayService: { searchStays: vi.fn() } }));
vi.mock('../../services/inventoryService', () => ({
  inventoryService: { getAllRooms: vi.fn().mockResolvedValue({ content: [] }) },
}));
vi.mock('../../services/nightAuditService', () => ({ nightAuditService: { run: vi.fn(), getHistory: vi.fn() } }));
vi.mock('../../store/toastStore', () => ({ useToastStore: vi.fn() }));
vi.mock('focus-trap-react', () => ({
  default: ({ children }: { children: React.ReactNode }) => <>{children}</>,
}));

const render = (ui: ReactElement) => renderWithQuery(<MemoryRouter>{ui}</MemoryRouter>);
const page = (content: unknown[], totalElements = content.length) => ({ content, totalPages: 1, totalElements });
const candidate = (id: string, status: string) => ({
  id, guestId: 'g', guestFullName: `Guest ${id}`, checkInDate: '2026-06-14', checkOutDate: '2099-01-01',
  status, expectedGuests: 1, lineItems: [], active: true, version: 1,
});
const mockAddToast = vi.fn();
const COMPLETED = { id: 'run', businessDate: '2026-06-14', status: 'COMPLETED' };

const clickContinue = () => fireEvent.click(screen.getByRole('button', { name: 'night_audit_continue' }));

describe('NightAuditRunWizard', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    vi.mocked(useToastStore).mockReturnValue(mockAddToast);
    vi.mocked(stayService.searchStays).mockResolvedValue(page([]) as never);
    vi.mocked(reservationService.searchReservations).mockResolvedValue(page([]) as never);
  });

  it('starts on the arrivals step and only exposes the run on the confirm step', () => {
    render(<NightAuditRunWizard />);

    expect(screen.getByRole('button', { name: 'night_audit_continue' })).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'night_audit_run_action' })).not.toBeInTheDocument();

    clickContinue();

    expect(screen.getByRole('button', { name: 'night_audit_run_action' })).toBeInTheDocument();
    expect(screen.queryByLabelText('night_audit_business_date')).not.toBeInTheDocument();
  });

  it('summarises how many reservations the run will mark as no-show', async () => {
    vi.mocked(reservationService.searchReservations).mockImplementation(((p: { status?: string }) =>
      Promise.resolve(p.status === 'PENDING'
        ? page([candidate('a', 'PENDING')])
        : page([candidate('b', 'CONFIRMED'), candidate('c', 'CONFIRMED')]))) as never);
    render(<NightAuditRunWizard />);

    await screen.findByText('Guest a');
    clickContinue();

    expect(screen.getByText(/night_audit_confirm_summary.*"count":3/)).toBeInTheDocument();
  });

  it('goes back to the arrivals step with Back and via the stepper', () => {
    render(<NightAuditRunWizard />);
    clickContinue();
    fireEvent.click(screen.getByRole('button', { name: 'back' }));
    expect(screen.getByLabelText('night_audit_business_date')).toBeInTheDocument();

    clickContinue();
    fireEvent.click(screen.getByRole('button', { name: /night_audit_step_noshows/ }));
    expect(screen.getByLabelText('night_audit_business_date')).toBeInTheDocument();
  });

  it('runs the closing for the chosen date after confirmation and returns to step 1', async () => {
    vi.mocked(nightAuditService.run).mockResolvedValue(COMPLETED as never);
    render(<NightAuditRunWizard />);

    fireEvent.change(screen.getByLabelText('night_audit_business_date'), { target: { value: '2026-06-14' } });
    clickContinue();
    fireEvent.click(screen.getByRole('button', { name: 'night_audit_run_action' }));
    fireEvent.click(screen.getByRole('button', { name: 'confirm' }));

    await waitFor(() => expect(nightAuditService.run).toHaveBeenCalledWith('2026-06-14'));
    await waitFor(() => expect(mockAddToast).toHaveBeenCalledWith(expect.stringContaining('night_audit_run_success'), 'success'));
    expect(await screen.findByLabelText('night_audit_business_date')).toBeInTheDocument();
  });

  it('stays on the confirm step when the run fails', async () => {
    vi.mocked(nightAuditService.run).mockResolvedValue({ ...COMPLETED, status: 'FAILED', failureReason: 'BOOM' } as never);
    render(<NightAuditRunWizard />);

    clickContinue();
    fireEvent.click(screen.getByRole('button', { name: 'night_audit_run_action' }));
    fireEvent.click(screen.getByRole('button', { name: 'confirm' }));

    await waitFor(() => expect(mockAddToast).toHaveBeenCalledWith(expect.stringContaining('night_audit_run_failed_detail'), 'error'));
    expect(screen.getByRole('button', { name: 'night_audit_run_action' })).toBeInTheDocument();
  });

  it('does not claim a count it could not load', async () => {
    vi.mocked(reservationService.searchReservations).mockRejectedValue(new Error('boom'));
    render(<NightAuditRunWizard />);

    await screen.findByRole('alert');
    clickContinue();

    expect(screen.getByText(/night_audit_confirm_summary_unknown/)).toBeInTheDocument();
    expect(screen.queryByText(/night_audit_confirm_summary \{/)).not.toBeInTheDocument();
  });

  it('blocks Continue and fetches nothing while the date is empty', async () => {
    render(<NightAuditRunWizard />);
    await waitFor(() => expect(reservationService.searchReservations).toHaveBeenCalled());
    vi.mocked(reservationService.searchReservations).mockClear();

    fireEvent.change(screen.getByLabelText('night_audit_business_date'), { target: { value: '' } });

    expect(screen.getByRole('button', { name: 'night_audit_continue' })).toBeDisabled();
    expect(reservationService.searchReservations).not.toHaveBeenCalled();
  });

  it('moves focus to the step heading when the step changes', () => {
    render(<NightAuditRunWizard />);
    clickContinue();
    expect(screen.getByRole('heading', { name: 'night_audit_step_confirm' })).toHaveFocus();
  });

  it('has no accessibility violations', async () => {
    const { container } = render(<NightAuditRunWizard />);
    await waitFor(() => expect(screen.getByText('night_audit_noshows_empty')).toBeInTheDocument());
    expect(await axe(container)).toHaveNoViolations();
  });
});
