import { useCallback, useMemo, memo } from 'react';
import { useSearchParams } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import { PageHeader } from '../../components/PageHeader';
import { M3SegmentedRow, type M3SegmentOption } from '../../components/m3';
import { useRoomsList, useRoomTypes } from '../../hooks/queries';
import { RoomList } from './RoomList';
import { RoomTypeList } from './RoomTypeList';
import { readRoomListParams, roomListParamsToSearch, type RoomsTab } from './roomListParams';

const TAB_OPTIONS: M3SegmentOption<RoomsTab>[] = [
  { value: 'rooms', labelKey: 'tab_rooms', icon: 'door_front' },
  { value: 'room_types', labelKey: 'tab_room_types', icon: 'category' },
];

export const Rooms = memo(() => {
  const { t } = useTranslation('common');
  const [urlParams, setUrlParams] = useSearchParams();
  const urlState = useMemo(() => readRoomListParams(urlParams), [urlParams]);
  const activeTab = urlState.tab;
  // A tab change is a navigation (Back returns to the other tab); the filters below replace.
  const setActiveTab = useCallback((tab: RoomsTab) => {
    setUrlParams(roomListParamsToSearch({ tab, typeId: '', availableToday: false }));
  }, [setUrlParams]);
  const showTypes = useCallback(() => {
    setActiveTab('room_types');
  }, [setActiveTab]);
  // Same queries as the tabs below (shared cache), only to say how many there are.
  const { data: rooms } = useRoomsList(false);
  const { data: roomTypes } = useRoomTypes();
  const subtitle = rooms && roomTypes
    ? t('rooms_count_summary', { rooms: rooms.length, types: roomTypes.length })
    : t('rooms_subtitle');

  return (
    <div className="space-y-6">
      <PageHeader icon="meeting_room" title={t('rooms_title')} subtitle={subtitle} />

      <M3SegmentedRow<RoomsTab>
        ns="common"
        options={TAB_OPTIONS}
        value={activeTab}
        onChange={setActiveTab}
        ariaLabel={t('rooms_tabs_label')}
        className="w-max"
      />

      <div>
        {activeTab === 'rooms' && <RoomList onGoToTypes={showTypes} />}
        {activeTab === 'room_types' && <RoomTypeList />}
      </div>
    </div>
  );
});

