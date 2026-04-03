import { useState, useCallback, memo } from 'react';
import { useTranslation } from 'react-i18next';
import { MaterialIcon } from '../../components/MaterialIcon';
import { RoomList } from './RoomList';
import { RoomTypeList } from './RoomTypeList';

type Tab = 'rooms' | 'room_types';

export const Rooms = memo(() => {
  const { t } = useTranslation('common');
  const [activeTab, setActiveTab] = useState<Tab>('rooms');

  const showRooms = useCallback(() => setActiveTab('rooms'), []);
  const showTypes = useCallback(() => setActiveTab('room_types'), []);

  return (
    <div className="space-y-6">
      <div className="flex flex-col sm:flex-row justify-between items-start sm:items-center gap-4">
        <div>
          <h1 className="text-2xl font-display font-bold tracking-tight text-on-surface flex items-center">
            <MaterialIcon name="meeting_room" className="mr-2 text-primary" />
            {t('rooms_title')}
          </h1>
          <p className="text-sm font-body text-on-surface-variant mt-1">{t('rooms_subtitle')}</p>
        </div>
      </div>

      {/* Segmented Button / Tabs */}
      <div className="flex p-1 space-x-1 bg-surface-container-highest rounded-shape-md w-max border border-outline-variant/30">
        <button
          className={`px-4 py-2 flex items-center gap-2 text-sm font-medium font-body rounded-shape-sm transition-colors ${
            activeTab === 'rooms'
              ? 'bg-primary text-on-primary shadow-elevation-1'
              : 'text-on-surface-variant hover:text-on-surface'
          }`}
          onClick={showRooms}
        >
          <MaterialIcon name="door_front" size={20} />
          {t('tab_rooms')}
        </button>
        <button
          className={`px-4 py-2 flex items-center gap-2 text-sm font-medium font-body rounded-shape-sm transition-colors ${
            activeTab === 'room_types'
              ? 'bg-primary text-on-primary shadow-elevation-1'
              : 'text-on-surface-variant hover:text-on-surface'
          }`}
          onClick={showTypes}
        >
          <MaterialIcon name="category" size={20} />
          {t('tab_room_types')}
        </button>
      </div>

      <div>
        {activeTab === 'rooms' && <RoomList />}
        {activeTab === 'room_types' && <RoomTypeList />}
      </div>
    </div>
  );
});

