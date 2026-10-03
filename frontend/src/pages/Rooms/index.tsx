import { useState, memo } from 'react';
import { useTranslation } from 'react-i18next';
import { PageHeader } from '../../components/PageHeader';
import { M3SegmentedRow, type M3SegmentOption } from '../../components/m3';
import { RoomList } from './RoomList';
import { RoomTypeList } from './RoomTypeList';

type Tab = 'rooms' | 'room_types';

const TAB_OPTIONS: M3SegmentOption<Tab>[] = [
  { value: 'rooms', labelKey: 'tab_rooms', icon: 'door_front' },
  { value: 'room_types', labelKey: 'tab_room_types', icon: 'category' },
];

export const Rooms = memo(() => {
  const { t } = useTranslation('common');
  const [activeTab, setActiveTab] = useState<Tab>('rooms');

  return (
    <div className="space-y-6">
      <PageHeader icon="meeting_room" title={t('rooms_title')} subtitle={t('rooms_subtitle')} />

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

