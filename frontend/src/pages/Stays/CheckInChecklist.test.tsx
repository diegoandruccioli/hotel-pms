import { render, screen } from '@testing-library/react';
import { describe, expect, it, vi } from 'vitest';
import { axe } from 'vitest-axe';
import { CheckInChecklist } from './CheckInChecklist';
import type { ChecklistItem } from './checkInReadiness';

vi.mock('react-i18next', () => ({
  useTranslation: () => ({
    t: (key: string, opts?: Record<string, unknown>) =>
      opts ? `${key}:${Object.values(opts).join(',')}` : key,
    i18n: { language: 'en' },
  }),
}));

const items: ChecklistItem[] = [
  { id: 'guest-0', tone: 'ok', titleKey: 'checklist_guest_primary', titleParams: { name: 'Ada' }, detailKey: 'checklist_status_complete' },
  { id: 'guest-1', tone: 'warn', titleKey: 'checklist_guest', titleParams: { name: 'Bo' }, detailKey: 'checklist_status_missing', detailParams: { count: 3 } },
  { id: 'alloggiati', tone: 'pending', titleKey: 'checklist_alloggiati', detailKey: 'checklist_alloggiati_waiting' },
];

describe('CheckInChecklist', () => {
  it('renders one entry per item with title and detail', () => {
    render(<CheckInChecklist items={items} />);
    expect(screen.getAllByRole('listitem')).toHaveLength(3);
    expect(screen.getByText('checklist_guest_primary:Ada')).toBeInTheDocument();
    expect(screen.getByText('checklist_status_missing:3')).toBeInTheDocument();
  });

  it('announces progress as complete items over total', () => {
    render(<CheckInChecklist items={items} />);
    expect(screen.getByRole('status')).toHaveTextContent('checklist_progress:1,3');
  });

  it('exposes each tone as text, not only as an icon', () => {
    render(<CheckInChecklist items={items} />);
    expect(screen.getByText('checklist_tone_ok:', { exact: false })).toBeInTheDocument();
    expect(screen.getByText('checklist_tone_warn:', { exact: false })).toBeInTheDocument();
    expect(screen.getByText('checklist_tone_pending:', { exact: false })).toBeInTheDocument();
  });

  it('has no accessibility violations', async () => {
    const { container } = render(<CheckInChecklist items={items} />);
    expect(await axe(container)).toHaveNoViolations();
  });
});
