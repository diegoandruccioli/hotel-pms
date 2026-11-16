export type RoomsTab = 'rooms' | 'room_types';

export interface RoomListParams {
  tab: RoomsTab;
  /** Room type id, or an empty string for all types. */
  typeId: string;
  availableToday: boolean;
}

// Shape only; whether the id is one of the hotel's room types is checked against the loaded list.
const TYPE_ID = /^[A-Za-z0-9_-]{1,64}$/;

/**
 * Reads the page state from the query string (`?tab=types&type=<uuid>&available=1`). A missing or
 * malformed value is ignored in silence and falls back to the default: a hand-edited URL is an
 * approximation of intent, not a mistake. A type id that is well formed but unknown (a deleted
 * type) is dropped by the list once the room types are loaded. The search text is never part of
 * it, as on the other lists.
 */
export const readRoomListParams = (params: URLSearchParams): RoomListParams => {
  const type = params.get('type') ?? '';
  return {
    tab: params.get('tab') === 'types' ? 'room_types' : 'rooms',
    typeId: TYPE_ID.test(type) ? type : '',
    availableToday: params.get('available') === '1',
  };
};

/**
 * Query string for a page state; defaults are left out to keep the URL clean, and the filters only
 * count on the rooms tab (the room types tab has none).
 */
export const roomListParamsToSearch = (state: RoomListParams): URLSearchParams => {
  const search = new URLSearchParams();
  if (state.tab === 'room_types') {
    search.set('tab', 'types');
    return search;
  }
  if (state.typeId) search.set('type', state.typeId);
  if (state.availableToday) search.set('available', '1');
  return search;
};
