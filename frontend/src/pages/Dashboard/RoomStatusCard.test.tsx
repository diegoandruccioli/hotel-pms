import { render, screen, within } from '@testing-library/react';
import { describe, it, expect, vi } from 'vitest';
import { MemoryRouter } from 'react-router-dom';
import { axe } from 'vitest-axe';
import { RoomStatusCard } from './RoomStatusCard';

vi.mock('react-i18next', () => ({
  useTranslation: () => ({ t: (key: string) => key }),
}));

const renderCard = (counts: Parameters<typeof RoomStatusCard>[0]['counts']) =>
  render(<MemoryRouter><RoomStatusCard counts={counts} /></MemoryRouter>);

describe('RoomStatusCard', () => {
  it('sizes each bar segment by its share of the rooms', () => {
    renderCard({ CLEAN: 1, DIRTY: 1, MAINTENANCE: 0, OCCUPIED: 2 });
    const bar = screen.getByTestId('room-status-bar');
    const widthOf = (status: string) => (bar.querySelector(`[data-status="${status}"]`) as HTMLElement).style.width;
    expect(widthOf('CLEAN')).toBe('25%');
    expect(widthOf('DIRTY')).toBe('25%');
    expect(widthOf('MAINTENANCE')).toBe('0%');
    expect(widthOf('OCCUPIED')).toBe('50%');
  });

  it('treats a status missing from the map as zero rooms', () => {
    renderCard({ CLEAN: 3 });
    const legend = screen.getByTestId('room-status-summary');
    expect(within(legend).getByText('room_status_dirty').closest('li')).toHaveTextContent('0');
  });

  it('draws an empty neutral bar when there are no rooms', () => {
    renderCard({});
    const bar = screen.getByTestId('room-status-bar');
    expect(bar.className).toContain('bg-surface-container-highest');
    expect((bar.querySelector('[data-status="CLEAN"]') as HTMLElement).style.width).toBe('0%');
  });

  it('keeps the bar out of the accessibility tree and links to housekeeping', () => {
    renderCard({ CLEAN: 1 });
    expect(screen.getByTestId('room-status-bar')).toHaveAttribute('aria-hidden', 'true');
    expect(screen.getByRole('link', { name: 'dashboard_view_all_rooms' })).toHaveAttribute('href', '/housekeeping');
  });

  it('has no accessibility violations', async () => {
    const { container } = renderCard({ CLEAN: 1, DIRTY: 2, MAINTENANCE: 1, OCCUPIED: 4 });
    expect(await axe(container)).toHaveNoViolations();
  });
});
