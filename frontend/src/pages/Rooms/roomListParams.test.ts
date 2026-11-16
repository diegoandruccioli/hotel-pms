import { describe, it, expect } from 'vitest';
import { readRoomListParams, roomListParamsToSearch } from './roomListParams';

const read = (qs: string) => readRoomListParams(new URLSearchParams(qs));

describe('readRoomListParams', () => {
  it('defaults to the rooms tab with no filters', () => {
    expect(read('')).toEqual({ tab: 'rooms', typeId: '', availableToday: false });
  });

  it('reads the tab, the type and the availability flag', () => {
    expect(read('tab=types').tab).toBe('room_types');
    expect(read('type=abc-123').typeId).toBe('abc-123');
    expect(read('available=1').availableToday).toBe(true);
  });

  it('ignores malformed values in silence', () => {
    expect(read('tab=nope&type=a b<c>&available=yes')).toEqual({ tab: 'rooms', typeId: '', availableToday: false });
  });
});

describe('roomListParamsToSearch', () => {
  it('writes nothing for the defaults', () => {
    expect(roomListParamsToSearch({ tab: 'rooms', typeId: '', availableToday: false }).toString()).toBe('');
  });

  it('writes the type and the availability flag on the rooms tab', () => {
    const search = roomListParamsToSearch({ tab: 'rooms', typeId: 'rt1', availableToday: true });
    expect(search.get('type')).toBe('rt1');
    expect(search.get('available')).toBe('1');
  });

  it('drops the filters on the room types tab', () => {
    expect(roomListParamsToSearch({ tab: 'room_types', typeId: 'rt1', availableToday: true }).toString()).toBe('tab=types');
  });
});
