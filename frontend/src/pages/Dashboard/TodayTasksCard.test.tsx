import { render, screen } from '@testing-library/react';
import { describe, it, expect, vi } from 'vitest';
import { MemoryRouter } from 'react-router-dom';
import { axe } from 'vitest-axe';
import { TodayTasksCard } from './TodayTasksCard';
import type { DaySheetResponse } from '../../types';

vi.mock('react-i18next', () => ({
  useTranslation: () => ({ t: (key: string) => key }),
}));

const DAY_SHEET: DaySheetResponse = {
  date: '2026-10-04', todayArrivals: 2, todayDepartures: 3, guestsInHouse: 10, currentStays: 5,
  availableRooms: 4, roomStatusCounts: { CLEAN: 5, DIRTY: 2, MAINTENANCE: 1, OCCUPIED: 5 },
};

const renderCard = (daySheet: DaySheetResponse, role: Parameters<typeof TodayTasksCard>[0]['role']) =>
  render(<MemoryRouter><TodayTasksCard daySheet={daySheet} role={role} /></MemoryRouter>);

describe('TodayTasksCard', () => {
  it('lists every task for a receptionist when the day-sheet has work', () => {
    renderCard(DAY_SHEET, 'RECEPTIONIST');
    expect(screen.getAllByRole('link').map((l) => l.getAttribute('href')))
      .toEqual(['/housekeeping', '/housekeeping', '/stays', '/night-audit']);
  });

  it('drops tasks with nothing to do', () => {
    renderCard({ ...DAY_SHEET, todayDepartures: 0, roomStatusCounts: { CLEAN: 5 } }, 'ADMIN');
    expect(screen.getAllByRole('link').map((l) => l.getAttribute('href'))).toEqual(['/night-audit']);
    expect(screen.queryByText('dashboard_task_dirty_rooms')).not.toBeInTheDocument();
  });

  it('hides night audit from a role that cannot run it', () => {
    renderCard(DAY_SHEET, 'GUEST');
    expect(screen.queryByText('dashboard_task_night_audit')).not.toBeInTheDocument();
  });

  it('renders nothing when there are no tasks at all', () => {
    const { container } = renderCard({ ...DAY_SHEET, todayDepartures: 0, roomStatusCounts: {} }, undefined);
    expect(container).toBeEmptyDOMElement();
  });

  it('has no accessibility violations', async () => {
    const { container } = renderCard(DAY_SHEET, 'ADMIN');
    expect(await axe(container)).toHaveNoViolations();
  });
});
