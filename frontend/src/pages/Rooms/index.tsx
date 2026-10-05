import { useState, memo } from 'react';
import { useTranslation } from 'react-i18next';
import { PageHeader } from '../../components/PageHeader';
import { M3SegmentedRow, type M3SegmentOption } from '../../components/m3';
import { useRoomsList, useRoomTypes } from '../../hooks/queries';
import { RoomList } from './RoomList';
import { RoomTypeList } from './RoomTypeList';

type Tab = 'rooms' | 'room_types';

/** Page size `useRoomsList` asks the backend for. */
const ROOMS_PAGE_SIZE = 100;

const TAB_OPTIONS: M3SegmentOption<Tab>[] = [
  { value: 'rooms', labelKey: 'tab_rooms', icon: 'door_front' },
  { value: 'room_types', labelKey: 'tab_room_types', icon: 'category' },
];

export const Rooms = memo(() => {
  const { t } = useTranslation('common');
  const [activeTab, setActiveTab] = useState<Tab>('rooms');
  // Same queries as the tabs below (shared cache), only to say how many there are.
  const { data: rooms } = useRoomsList(false);
  const { data: roomTypes } = useRoomTypes();
  // The room list is one page of ROOMS_PAGE_SIZE: a full page may be only part of the hotel, so no count then.
  const subtitle = rooms && roomTypes && rooms.length < ROOMS_PAGE_SIZE
    ? t('rooms_count_summary', { rooms: rooms.length, types: roomTypes.length })
    : t('rooms_subtitle');

  return (
    <div className="space-y-6">
      <PageHeader icon="meeting_room" title={t('rooms_title')} subtitle={subtitle} />

      <M3SegmentedRow<Tab>
        ns="common"
        options={TAB_OPTIONS}
        value={activeTab}
        onChange={setActiveTab}
        ariaLabel={t('rooms_tabs_label')}
        className="w-max"
      />

      <div>
        {activeTab === 'rooms' && <RoomList />}
        {activeTab === 'room_types' && <RoomTypeList />}
      </div>
    </div>
  );
});

