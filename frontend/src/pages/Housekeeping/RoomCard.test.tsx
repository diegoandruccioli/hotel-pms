import { describe, it, expect, vi } from 'vitest';
import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import { axe } from 'vitest-axe';
import { RoomCard } from './RoomCard';
import type { RoomResponse } from '../../types';

vi.mock('react-i18next', () => ({
  useTranslation: () => ({ t: (key: string) => key, i18n: { language: 'en' } }),
  initReactI18next: { type: '3rdParty', init: vi.fn() },
}));

const room = (status: RoomResponse['status']): RoomResponse =>
  ({ id: '1', roomNumber: '101', status, roomType: { name: 'Standard', basePrice: 100 } }) as unknown as RoomResponse;

const setup = (status: RoomResponse['status'], onStatusChange = vi.fn().mockResolvedValue(undefined)) => {
  const onToggleSelected = vi.fn();
  const utils = render(
    <RoomCard room={room(status)} selected={false} onToggleSelected={onToggleSelected} onStatusChange={onStatusChange} />,
  );
  return { ...utils, onToggleSelected, onStatusChange };
};

describe('RoomCard', () => {
  it('offers the two other statuses as arrow-prefixed buttons', () => {
    setup('CLEAN');
    expect(screen.getByRole('button', { name: '→ room_status_dirty' })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: '→ room_status_maintenance' })).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: '→ room_status_clean' })).not.toBeInTheDocument();
  });

  it('reports the change and disables the buttons while it is in flight', async () => {
    let resolve: () => void = () => {};
    const onStatusChange = vi.fn(() => new Promise<void>((r) => { resolve = r; }));
    setup('CLEAN', onStatusChange);

    fireEvent.click(screen.getByRole('button', { name: '→ room_status_dirty' }));
    expect(onStatusChange).toHaveBeenCalledWith('1', 'DIRTY');
    expect(screen.getByText('saving')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: '→ room_status_maintenance' })).toBeDisabled();

    resolve();
    await waitFor(() => expect(screen.queryByText('saving')).not.toBeInTheDocument());
  });

  it('toggles selection through its checkbox', () => {
    const { onToggleSelected } = setup('DIRTY');
    fireEvent.click(screen.getByLabelText('select_room'));
    expect(onToggleSelected).toHaveBeenCalledWith('1');
  });

  it('has no checkbox and no actions when occupied', () => {
    setup('OCCUPIED');
    expect(screen.queryByLabelText('select_room')).not.toBeInTheDocument();
    expect(screen.queryByText(/→/)).not.toBeInTheDocument();
  });

  it.each([
    ['CLEAN', 'bg-tertiary'],
    ['DIRTY', 'bg-secondary'],
    ['MAINTENANCE', 'bg-error'],
  ] as const)('colours the top bar of a %s room with %s', (status, cls) => {
    setup(status);
    expect(screen.getByTestId('room-card-tone')).toHaveClass(cls);
  });

  it('has no accessibility violations', async () => {
    const { container } = setup('CLEAN');
    expect(await axe(container)).toHaveNoViolations();
  });
});
