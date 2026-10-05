import { describe, it, expect, vi, beforeEach } from 'vitest';
import { screen, fireEvent, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { axe } from 'vitest-axe';
import { renderWithQuery as render } from '../test-utils';
import { Rooms } from './Rooms';

vi.mock('react-i18next', () => ({
  useTranslation: () => ({ t: (key: string) => key, i18n: { language: 'en' } }),
  initReactI18next: { type: '3rdParty', init: vi.fn() },
}));

vi.mock('../services/inventoryService', () => ({
  inventoryService: {
    getAllRooms: vi.fn().mockResolvedValue({
      content: [], totalElements: 0, totalPages: 1, number: 0, size: 20,
    }),
    getAllRoomTypes: vi.fn().mockResolvedValue([]),
    createRoom: vi.fn(),
    updateRoom: vi.fn(),
    deleteRoom: vi.fn(),
    createRoomType: vi.fn(),
    updateRoomType: vi.fn(),
    deleteRoomType: vi.fn(),
  },
}));

vi.mock('../store/toastStore', () => ({
  useToastStore: (selector: unknown) =>
    (selector as (s: { addToast: () => void }) => unknown)({ addToast: vi.fn() }),
}));

vi.mock('react-router-dom', () => ({
  useNavigate: () => vi.fn(),
  useLocation: () => ({ pathname: '/rooms', state: null, search: '', hash: '', key: 'test' }),
}));

describe('Rooms', () => {
  beforeEach(() => vi.clearAllMocks());

  it('renders the page heading', async () => {
    render(<Rooms />);
    await waitFor(() => {
      expect(screen.getByText('rooms_title')).toBeInTheDocument();
    });
  });

  it('renders both tabs as radios in a labelled group', () => {
    render(<Rooms />);
    expect(screen.getByRole('radiogroup', { name: 'rooms_tabs_label' })).toBeInTheDocument();
    expect(screen.getByRole('radio', { name: 'tab_rooms' })).toBeInTheDocument();
    expect(screen.getByRole('radio', { name: 'tab_room_types' })).toBeInTheDocument();
  });

  it('Rooms tab is active by default', () => {
    render(<Rooms />);
    expect(screen.getByRole('radio', { name: 'tab_rooms' })).toHaveAttribute('aria-checked', 'true');
    expect(screen.getByRole('radio', { name: 'tab_room_types' })).toHaveAttribute('aria-checked', 'false');
  });

  it('Room Types tab becomes active after click', async () => {
    render(<Rooms />);
    fireEvent.click(screen.getByRole('radio', { name: 'tab_room_types' }));
    await waitFor(() => {
      expect(screen.getByRole('radio', { name: 'tab_room_types' })).toHaveAttribute('aria-checked', 'true');
    });
    expect(screen.getByRole('radio', { name: 'tab_rooms' })).toHaveAttribute('aria-checked', 'false');
  });

  it('switches tab with the arrow keys', async () => {
    const user = userEvent.setup();
    render(<Rooms />);
    screen.getByRole('radio', { name: 'tab_rooms' }).focus();
    await user.keyboard('{ArrowRight}');
    expect(screen.getByRole('radio', { name: 'tab_room_types' })).toHaveAttribute('aria-checked', 'true');
    expect(screen.getByRole('radio', { name: 'tab_room_types' })).toHaveFocus();
  });

  it('shows rooms subtitle text', async () => {
    render(<Rooms />);
    await waitFor(() => {
      expect(screen.getByText('rooms_subtitle')).toBeInTheDocument();
    });
  });

  it('summarises the counts in the subtitle once rooms and room types are loaded', async () => {
    const { inventoryService } = await import('../services/inventoryService');
    vi.mocked(inventoryService.getAllRooms).mockResolvedValueOnce({
      content: [{ id: 'r1', roomNumber: '101', status: 'CLEAN', roomType: { id: 't1', name: 'Single' } }, { id: 'r2', roomNumber: '102', status: 'CLEAN', roomType: { id: 't1', name: 'Single' } }], totalElements: 2, totalPages: 1, number: 0, size: 100,
    } as never);
    vi.mocked(inventoryService.getAllRoomTypes).mockResolvedValueOnce([{ id: 't1', name: 'Single', maxOccupancy: 1, basePrice: 50 }] as never);
    render(<Rooms />);
    expect(await screen.findByText('rooms_count_summary')).toBeInTheDocument();
    expect(screen.queryByText('rooms_subtitle')).not.toBeInTheDocument();
  });

  it('has no critical accessibility violations', async () => {
    const { container } = render(<Rooms />);
    await waitFor(() => screen.getByText('rooms_title'));
    const results = await axe(container);
    expect((results as unknown as { violations: { impact: string }[] }).violations
      .filter((v) => v.impact === 'critical')).toHaveLength(0);
  });
});
