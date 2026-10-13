import { describe, it, expect, vi } from 'vitest';
import { render, screen, fireEvent, createEvent } from '@testing-library/react';
import { axe } from 'vitest-axe';
import { HousekeepingColumn } from './HousekeepingColumn';
import type { RoomResponse, RoomStatus } from '../../types';

vi.mock('react-i18next', () => ({
  useTranslation: () => ({
    t: (key: string, opts?: Record<string, unknown>) => (opts?.count !== undefined ? `${key} ${opts.count}` : key),
    i18n: { language: 'en' },
  }),
  initReactI18next: { type: '3rdParty', init: vi.fn() },
}));

const room = (id: string, status: RoomStatus): RoomResponse =>
  ({ id, roomNumber: `10${id}`, status, roomType: { name: 'Standard', basePrice: 100 } }) as unknown as RoomResponse;

const setup = (status: RoomStatus, rooms: RoomResponse[], droppable = true, priorityRooms: string[] = []) => {
  const onDropRoom = vi.fn();
  const utils = render(
    <HousekeepingColumn
      status={status}
      rooms={rooms}
      droppable={droppable}
      selectedIds={new Set()}
      priorityRooms={new Set(priorityRooms)}
      busyIds={new Set()}
      focusRoomId={null}
      onFocusHandled={vi.fn()}
      onToggleSelected={vi.fn()}
      onStatusChange={vi.fn().mockResolvedValue(undefined)}
      onDropRoom={onDropRoom}
    />,
  );
  return { ...utils, onDropRoom, column: screen.getByRole('group', { name: 'room_status_clean' }) };
};

const drop = (roomId: string) => ({ dataTransfer: { getData: () => roomId } });

describe('HousekeepingColumn', () => {
  it('shows the title and the number of rooms', () => {
    const { column } = setup('CLEAN', [room('1', 'CLEAN'), room('2', 'CLEAN')]);
    expect(column).toBeInTheDocument();
    expect(screen.getByText('housekeeping_column_count 2')).toHaveClass('sr-only');
  });

  it('shows an empty message when there are no rooms', () => {
    setup('CLEAN', []);
    expect(screen.getByText('housekeeping_column_empty')).toBeInTheDocument();
  });

  it('reports the dropped room and the target status', () => {
    const { column, onDropRoom } = setup('CLEAN', []);
    fireEvent.drop(column, drop('7'));
    expect(onDropRoom).toHaveBeenCalledWith('7', 'CLEAN');
  });

  it('ignores a drop without a room id', () => {
    const { column, onDropRoom } = setup('CLEAN', []);
    fireEvent.drop(column, drop(''));
    expect(onDropRoom).not.toHaveBeenCalled();
  });

  it('is not a drop target when it is not droppable', () => {
    const { column, onDropRoom } = setup('CLEAN', [], false);
    fireEvent.drop(column, drop('7'));
    expect(onDropRoom).not.toHaveBeenCalled();
    expect(fireEvent.dragOver(column, { dataTransfer: {} })).toBe(true); // not default-prevented: drop not allowed
  });

  it('allows the drop by cancelling dragover and highlights while hovering', () => {
    const { column } = setup('CLEAN', []);
    expect(fireEvent.dragOver(column, { dataTransfer: {} })).toBe(false); // preventDefault called
    expect(column).toHaveClass('outline-primary');
    fireEvent.dragLeave(column, { relatedTarget: null });
    expect(column).not.toHaveClass('outline-primary');
  });

  it('keeps the highlight while the pointer moves over a child of the column', () => {
    const { column } = setup('CLEAN', [room('1', 'CLEAN')]);
    fireEvent.dragOver(column, { dataTransfer: {} });
    // jsdom has no DragEvent, so relatedTarget is set on the event by hand.
    const leave = createEvent.dragLeave(column);
    Object.defineProperty(leave, 'relatedTarget', { value: screen.getByRole('heading', { level: 3 }) });
    fireEvent(column, leave);
    expect(column).toHaveClass('outline-primary');
  });

  it('marks priority only on dirty columns', () => {
    setup('CLEAN', [room('1', 'CLEAN')], true, ['101']);
    expect(screen.queryByText('housekeeping_priority')).not.toBeInTheDocument();
  });

  it('has no accessibility violations', async () => {
    const { container } = setup('CLEAN', [room('1', 'CLEAN')]);
    expect(await axe(container)).toHaveNoViolations();
  });
});
