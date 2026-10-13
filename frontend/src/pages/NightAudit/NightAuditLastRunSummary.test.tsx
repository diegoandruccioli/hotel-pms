import { describe, it, expect, vi } from 'vitest';
import { render, screen } from '@testing-library/react';
import { axe } from 'vitest-axe';
import { MemoryRouter } from 'react-router-dom';
import { NightAuditLastRunSummary } from './NightAuditLastRunSummary';
import type { NightAuditRunResponse } from '../../types';

vi.mock('react-i18next', () => ({
  useTranslation: () => ({
    t: (key: string, opts?: Record<string, unknown>) => (opts ? `${key} ${JSON.stringify(opts)}` : key),
    i18n: { language: 'en' },
  }),
  initReactI18next: { type: '3rdParty', init: vi.fn() },
}));

const RUN: NightAuditRunResponse = {
  id: 'r1',
  businessDate: '2026-06-15',
  status: 'COMPLETED',
  startedAt: '2026-06-16T03:30:00',
  completedAt: '2026-06-16T03:30:05',
  runBy: 'admin',
  arrivals: 7,
  departures: 5,
  guestsInHouse: 21,
  currentStays: 9,
  availableRooms: 3,
  noShowsMarked: 1,
  cashByMethod: [{ paymentMethod: 'CASH', total: 100 }, { paymentMethod: 'CARD', total: 50.5 }],
  cashSummaryDegraded: false,
  failureReason: null,
};

const setup = (run: NightAuditRunResponse = RUN) =>
  render(<MemoryRouter><NightAuditLastRunSummary run={run} /></MemoryRouter>);

describe('NightAuditLastRunSummary', () => {
  it('shows arrivals, departures, guests in house and the summed cash', () => {
    setup();
    expect(screen.getByText('7')).toBeInTheDocument();
    expect(screen.getByText('5')).toBeInTheDocument();
    expect(screen.getByText('21')).toBeInTheDocument();
    expect(screen.getByText('€150.50')).toBeInTheDocument();
  });

  it('shows a dash for figures the run did not record', () => {
    setup({ ...RUN, arrivals: null, departures: null });
    expect(screen.getAllByText('—')).toHaveLength(2);
  });

  it('warns when the cash total may be incomplete', () => {
    setup({ ...RUN, cashSummaryDegraded: true });
    expect(screen.getByText('night_audit_cash_degraded')).toBeInTheDocument();
  });

  it('does not warn when the cash total is complete', () => {
    setup();
    expect(screen.queryByText('night_audit_cash_degraded')).not.toBeInTheDocument();
  });

  it('has no accessibility violations', async () => {
    const { container } = setup({ ...RUN, cashSummaryDegraded: true });
    expect(await axe(container)).toHaveNoViolations();
  });
});
