import { describe, it, expect, vi } from 'vitest';
import { render, screen, fireEvent } from '@testing-library/react';
import { RoomingListRow, type RoomingListRowState } from './RoomingListRow';

vi.mock('react-i18next', () => ({
  useTranslation: () => ({ t: (key: string) => key }),
  initReactI18next: { type: '3rdParty', init: vi.fn() },
}));

vi.mock('../Reservations/GuestSearchAndCreate', () => ({
  GuestSearchAndCreate: () => <div>guest-search-mock</div>,
}));

const ROOM = { id: 'room1', roomNumber: '101', roomType: { id: 'rt1', name: 'Standard' } };

const baseRow: RoomingListRowState = {
  tempId: 'row-1', guest: null, roomId: '', expectedGuests: 1, billedToMasterFolio: false,
};

const noneTaken = () => false;
const allTaken = () => true;

describe('RoomingListRow', () => {
  it('should call onChange with the new expected guests count', () => {
    const onChange = vi.fn();
    render(
      <RoomingListRow
        row={baseRow}
        availableRooms={[ROOM] as never}
        roomTakenElsewhere={noneTaken}
        openMasterFolio={false}
        onChange={onChange}
        onRemove={vi.fn()}
        canRemove
      />,
    );

    fireEvent.change(screen.getByLabelText(/label_expected_guests/), { target: { value: '3' } });

    expect(onChange).toHaveBeenCalledWith('row-1', { expectedGuests: 3 });
  });

  it('should call onChange with the selected room', () => {
    const onChange = vi.fn();
    render(
      <RoomingListRow
        row={baseRow}
        availableRooms={[ROOM] as never}
        roomTakenElsewhere={noneTaken}
        openMasterFolio={false}
        onChange={onChange}
        onRemove={vi.fn()}
        canRemove
      />,
    );

    fireEvent.change(screen.getByLabelText(/label_room/), { target: { value: 'room1' } });

    expect(onChange).toHaveBeenCalledWith('row-1', { roomId: 'room1' });
  });

  it('should call onChange with the billedToMasterFolio flag when the checkbox is shown', () => {
    const onChange = vi.fn();
    render(
      <RoomingListRow
        row={baseRow}
        availableRooms={[ROOM] as never}
        roomTakenElsewhere={noneTaken}
        openMasterFolio
        onChange={onChange}
        onRemove={vi.fn()}
        canRemove
      />,
    );

    fireEvent.click(screen.getByLabelText('billed_to_master_folio'));

    expect(onChange).toHaveBeenCalledWith('row-1', { billedToMasterFolio: true });
  });

  it('should call onRemove when the remove button is clicked', () => {
    const onRemove = vi.fn();
    render(
      <RoomingListRow
        row={baseRow}
        availableRooms={[ROOM] as never}
        roomTakenElsewhere={noneTaken}
        openMasterFolio={false}
        onChange={vi.fn()}
        onRemove={onRemove}
        canRemove
      />,
    );

    fireEvent.click(screen.getByRole('button', { name: 'remove_room' }));

    expect(onRemove).toHaveBeenCalledWith('row-1');
  });

  it('should not show a remove button when canRemove is false', () => {
    render(
      <RoomingListRow
        row={baseRow}
        availableRooms={[ROOM] as never}
        roomTakenElsewhere={noneTaken}
        openMasterFolio={false}
        onChange={vi.fn()}
        onRemove={vi.fn()}
        canRemove={false}
      />,
    );

    expect(screen.queryByRole('button', { name: 'remove_room' })).not.toBeInTheDocument();
  });

  it('should show the no-rooms-available hint when every room is taken elsewhere', () => {
    render(
      <RoomingListRow
        row={baseRow}
        availableRooms={[ROOM] as never}
        roomTakenElsewhere={allTaken}
        openMasterFolio={false}
        onChange={vi.fn()}
        onRemove={vi.fn()}
        canRemove
      />,
    );

    expect(screen.getByText('no_rooms_available')).toBeInTheDocument();
  });
});
