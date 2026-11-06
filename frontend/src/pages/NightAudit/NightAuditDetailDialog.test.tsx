import { describe, it, expect, vi } from 'vitest';
import { render, screen, fireEvent } from '@testing-library/react';
import { NightAuditDetailDialog } from './NightAuditDetailDialog';
import type { NightAuditRunResponse } from '../../types';

vi.mock('react-i18next', () => ({
  useTranslation: () => ({
    t: (key: string, opts?: Record<string, unknown>) => (opts ? `${key} ${JSON.stringify(opts)}` : key),
    i18n: { language: 'en' },
  }),
  initReactI18next: { type: '3rdParty', init: vi.fn() },
}));

vi.mock('focus-trap-react', () => ({
  default: ({ children }: { children: React.ReactNode }) => <>{children}</>,
}));

const COMPLETED: NightAuditRunResponse = {
  id: 'r1',
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

const NO_CASH: NightAuditRunResponse = { ...COMPLETED, cashByMethod: [] };
const DEGRADED: NightAuditRunResponse = { ...COMPLETED, cashSummaryDegraded: true };
const FAILED: NightAuditRunResponse = { ...COMPLETED, status: 'FAILED', failureReason: 'DAY_SHEET_BOOM' };

describe('NightAuditDetailDialog', () => {
  it('lists occupancy figures and the cash breakdown of a completed run', () => {
    render(<NightAuditDetailDialog run={COMPLETED} onClose={vi.fn()} />);
    expect(screen.getByText('night_audit_guests_in_house')).toBeInTheDocument();
    expect(screen.getByText('CASH')).toBeInTheDocument();
    expect(screen.getByText('€150.00')).toBeInTheDocument();
  });

  it('says so when no payments were recorded', () => {
    render(<NightAuditDetailDialog run={NO_CASH} onClose={vi.fn()} />);
    expect(screen.getByText('night_audit_no_cash_activity')).toBeInTheDocument();
  });

  it('flags a degraded cash summary', () => {
    render(<NightAuditDetailDialog run={DEGRADED} onClose={vi.fn()} />);
    expect(screen.getByText('night_audit_cash_degraded')).toBeInTheDocument();
  });

  it('shows only the failure reason of a failed run', () => {
    render(
      <NightAuditDetailDialog run={FAILED} onClose={vi.fn()} />,
    );
    expect(screen.getByText('DAY_SHEET_BOOM')).toBeInTheDocument();
    expect(screen.queryByText('night_audit_guests_in_house')).not.toBeInTheDocument();
  });

  it('closes from the footer button', () => {
    const onClose = vi.fn();
    render(<NightAuditDetailDialog run={COMPLETED} onClose={onClose} />);
    // The dialog header also has a "close" icon button; the footer one is last.
    fireEvent.click(screen.getAllByRole('button', { name: 'close' }).at(-1)!);
    expect(onClose).toHaveBeenCalled();
  });
});
