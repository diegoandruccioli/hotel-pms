import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, waitFor, fireEvent } from '@testing-library/react';
import { axe } from 'vitest-axe';
import { RoomFormModal } from './RoomFormModal';
import { inventoryService } from '../../services/inventoryService';

vi.mock('react-i18next', () => ({
  useTranslation: () => ({ t: (key: string) => key, i18n: { language: 'en' } }),
  initReactI18next: { type: '3rdParty', init: vi.fn() },
}));

vi.mock('../../services/inventoryService', () => ({
  inventoryService: { createRoom: vi.fn(), updateRoom: vi.fn() },
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
  description: '', active: true, createdAt: '2026-01-01T00:00:00', updatedAt: '2026-01-01T00:00:00',
};

const ROOM_TYPES = [ROOM_TYPE];

const ROOM = {
  id: 'r1', hotelId: 'h1', roomNumber: '101', roomType: ROOM_TYPE,
  status: 'CLEAN' as const, active: true, createdAt: '2026-01-01T00:00:00', updatedAt: '2026-01-01T00:00:00',
};

describe('RoomFormModal', () => {
  const onClose = vi.fn();
  const onSaved = vi.fn();

  beforeEach(() => vi.clearAllMocks());

  it('renders add room heading when no room prop', () => {
    render(<RoomFormModal roomTypes={ROOM_TYPES} onClose={onClose} onSaved={onSaved} />);
    expect(screen.getByText('add_room')).toBeInTheDocument();
  });

  it('renders edit room heading when room prop provided', () => {
    render(<RoomFormModal room={ROOM} roomTypes={ROOM_TYPES} onClose={onClose} onSaved={onSaved} />);
    expect(screen.getByText('edit_room')).toBeInTheDocument();
  });

  it('pre-fills form fields from existing room', () => {
    render(<RoomFormModal room={ROOM} roomTypes={ROOM_TYPES} onClose={onClose} onSaved={onSaved} />);
    expect((screen.getByLabelText(/room_number/i) as HTMLInputElement).value).toBe('101');
  });

  it('calls onClose when cancel button clicked', () => {
    render(<RoomFormModal roomTypes={ROOM_TYPES} onClose={onClose} onSaved={onSaved} />);
    fireEvent.click(screen.getByText('cancel'));
    expect(onClose).toHaveBeenCalledOnce();
  });

  it('calls createRoom and onSaved on add form submission', async () => {
    vi.mocked(inventoryService.createRoom).mockResolvedValue(ROOM as never);
    render(<RoomFormModal roomTypes={ROOM_TYPES} onClose={onClose} onSaved={onSaved} />);
    fireEvent.change(screen.getByLabelText(/room_number/i), { target: { value: '202' } });
    fireEvent.submit(document.querySelector('form')!);
    await waitFor(() => expect(onSaved).toHaveBeenCalledOnce());
  });

  it('calls updateRoom and onSaved on edit form submission', async () => {
    vi.mocked(inventoryService.updateRoom).mockResolvedValue(ROOM as never);
    render(<RoomFormModal room={ROOM} roomTypes={ROOM_TYPES} onClose={onClose} onSaved={onSaved} />);
    fireEvent.submit(document.querySelector('form')!);
    await waitFor(() => expect(onSaved).toHaveBeenCalledOnce());
  });

  it('blocks submission and shows an error when room number is cleared', async () => {
    render(<RoomFormModal room={ROOM} roomTypes={ROOM_TYPES} onClose={onClose} onSaved={onSaved} />);
    fireEvent.change(screen.getByLabelText(/room_number/i), { target: { value: '' } });
    fireEvent.submit(document.querySelector('form')!);

    expect(await screen.findByText('common:err_required')).toBeInTheDocument();
    expect(inventoryService.updateRoom).not.toHaveBeenCalled();
  });

  it('renders room type options in select', () => {
    render(<RoomFormModal roomTypes={ROOM_TYPES} onClose={onClose} onSaved={onSaved} />);
    expect(screen.getByText('Single (Max 1 pax)')).toBeInTheDocument();
  });

  it('passes axe accessibility check', async () => {
    const { container } = render(<RoomFormModal roomTypes={ROOM_TYPES} onClose={onClose} onSaved={onSaved} />);
    expect(await axe(container)).toHaveNoViolations();
  });
});
