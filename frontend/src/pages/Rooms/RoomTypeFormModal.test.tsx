import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, waitFor, fireEvent } from '@testing-library/react';
import { axe } from 'vitest-axe';
import { RoomTypeFormModal } from './RoomTypeFormModal';
import { inventoryService } from '../../services/inventoryService';

vi.mock('react-i18next', () => ({
  useTranslation: () => ({ t: (key: string) => key, i18n: { language: 'en' } }),
  initReactI18next: { type: '3rdParty', init: vi.fn() },
}));

vi.mock('../../services/inventoryService', () => ({
  inventoryService: {
    createRoomType: vi.fn(),
    updateRoomType: vi.fn(),
    deleteRoomType: vi.fn(),
  },
}));

vi.mock('../../store/toastStore', () => ({
  useToastStore: (sel: unknown) =>
    (sel as (s: { addToast: () => void }) => unknown)({ addToast: vi.fn() }),
}));

vi.mock('focus-trap-react', () => ({
  default: ({ children }: { children: React.ReactNode }) => <>{children}</>,
}));

const ROOM_TYPE = {
  id: 'rt1', name: 'Single', maxOccupancy: 1, basePrice: 50,
  description: 'A single room', active: true,
  createdAt: '2026-01-01T00:00:00', updatedAt: '2026-01-01T00:00:00',
};

describe('RoomTypeFormModal', () => {
  const onClose = vi.fn();
  const onSaved = vi.fn();

  beforeEach(() => vi.clearAllMocks());

  it('renders add room type heading when no roomType prop', () => {
    render(<RoomTypeFormModal onClose={onClose} onSaved={onSaved} />);
    expect(screen.getByText('add_room_type')).toBeInTheDocument();
  });

  it('renders edit room type heading when roomType prop provided', () => {
    render(<RoomTypeFormModal roomType={ROOM_TYPE} onClose={onClose} onSaved={onSaved} />);
    expect(screen.getByText('edit_room_type')).toBeInTheDocument();
  });

  it('pre-fills form fields from existing room type', () => {
    render(<RoomTypeFormModal roomType={ROOM_TYPE} onClose={onClose} onSaved={onSaved} />);
    expect((screen.getByLabelText(/^name/i) as HTMLInputElement).value).toBe('Single');
    expect((screen.getByLabelText(/base_price/i) as HTMLInputElement).value).toBe('50');
  });

  it('calls onClose when cancel clicked', () => {
    render(<RoomTypeFormModal onClose={onClose} onSaved={onSaved} />);
    fireEvent.click(screen.getByText('cancel'));
    expect(onClose).toHaveBeenCalledOnce();
  });

  it('shows delete button only in edit mode', () => {
    const { rerender } = render(<RoomTypeFormModal onClose={onClose} onSaved={onSaved} />);
    expect(screen.queryByText('delete')).not.toBeInTheDocument();
    rerender(<RoomTypeFormModal roomType={ROOM_TYPE} onClose={onClose} onSaved={onSaved} />);
    expect(screen.getByText('delete')).toBeInTheDocument();
  });

  it('shows delete confirmation panel on delete click', () => {
    render(<RoomTypeFormModal roomType={ROOM_TYPE} onClose={onClose} onSaved={onSaved} />);
    fireEvent.click(screen.getByText('delete'));
    expect(screen.getByText('confirm_delete_room_type')).toBeInTheDocument();
    expect(screen.getByText('btn_confirm')).toBeInTheDocument();
  });

  it('calls createRoomType and onSaved on add form submission', async () => {
    vi.mocked(inventoryService.createRoomType).mockResolvedValue(ROOM_TYPE as never);
    render(<RoomTypeFormModal onClose={onClose} onSaved={onSaved} />);
    fireEvent.change(screen.getByLabelText(/^name/i), { target: { value: 'Suite' } });
    fireEvent.submit(document.querySelector('form')!);
    await waitFor(() => expect(onSaved).toHaveBeenCalledOnce());
  });

  it('calls deleteRoomType and onSaved on confirm delete', async () => {
    vi.mocked(inventoryService.deleteRoomType).mockResolvedValue(undefined as never);
    render(<RoomTypeFormModal roomType={ROOM_TYPE} onClose={onClose} onSaved={onSaved} />);
    fireEvent.click(screen.getByText('delete'));
    fireEvent.click(screen.getByText('btn_confirm'));
    await waitFor(() => expect(onSaved).toHaveBeenCalledOnce());
  });

  it('passes axe accessibility check', async () => {
    const { container } = render(<RoomTypeFormModal onClose={onClose} onSaved={onSaved} />);
    expect(await axe(container)).toHaveNoViolations();
  });
});
