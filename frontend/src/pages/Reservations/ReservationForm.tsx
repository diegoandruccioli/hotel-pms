import { useState, useEffect, useCallback, useMemo } from 'react';
import { useNavigate, useParams, useLocation } from 'react-router-dom';
import { MaterialIcon } from '../../components/MaterialIcon';
import { M3Button } from '../../components/m3/M3Button';
import { M3Card } from '../../components/m3/M3Card';
import { inventoryService } from '../../services/inventoryService';
import { reservationService } from '../../services/reservationService';
import { guestService } from '../../services/guestService';
import type { GuestResponseDTO } from '../../types/guest.types';
import type { RoomResponse } from '../../types/inventory.types';
import type { ReservationRequest, ReservationResponse } from '../../types/reservation.types';
import { GuestSearchAndCreate } from './GuestSearchAndCreate';
import { RoomSelection } from './RoomSelection';
import { useTranslation } from 'react-i18next';

export const ReservationForm = () => {
  const { t } = useTranslation('common');
  const navigate = useNavigate();
  const { id } = useParams<{ id: string }>();
  const location = useLocation();
  const isEdit = location.pathname.includes('/edit/');
  const isView = !!id && !isEdit;

  // Navigation / Loading / Error
  const [loading, setLoading] = useState(false);
  const [fetching, setFetching] = useState(false);
  const [error, setError] = useState<string | null>(null);

  // Data
  const [rooms, setRooms] = useState<RoomResponse[]>([]);
  const [allReservations, setAllReservations] = useState<ReservationResponse[]>([]);

  // Step 1: Guest Selection/Creation State
  const [selectedGuest, setSelectedGuest] = useState<GuestResponseDTO | null>(null);

  // Step 2: Reservation Details State
  const [checkInDate, setCheckInDate] = useState('');
  const [checkOutDate, setCheckOutDate] = useState('');
  const [expectedGuests, setExpectedGuests] = useState<number | string>(1);
  const [selectedRoomIds, setSelectedRoomIds] = useState<string[]>([]);
  const [status, setStatus] = useState<string>('CONFIRMED');

  const loadInitialData = useCallback(async () => {
    try {
      setFetching(true);
      setError(null);
      
      // Load rooms
      const roomsData = await inventoryService.getAllRooms();
      const allRooms = roomsData.content;
      
      const allRes = await reservationService.getAllReservations();
      setAllReservations(allRes);
      
      let initialRoomIds: string[] = [];

      // If Edit or View, load reservation
      if (id) {
        const res = await reservationService.getReservationById(id);
        setCheckInDate(res.checkInDate || '');
        setCheckOutDate(res.checkOutDate || '');
        setExpectedGuests(res.expectedGuests || 1);
        initialRoomIds = res.lineItems?.map(li => li.roomId) || [];
        setSelectedRoomIds(initialRoomIds);
        setStatus(res.status || 'CONFIRMED');
        
        // Load guest details
        if (res.guestId) {
          try {
            const guest = await guestService.getGuestById(res.guestId);
            setSelectedGuest(guest);
          } catch (err) {
            console.error('Failed to fetch guest details', err);
            setSelectedGuest({ id: res.guestId, firstName: res.guestFullName || '', lastName: '' } as GuestResponseDTO);
          }
        }
      }

      // Filter rooms: include CLEAN, DIRTY, and those already selected for this reservation
      setRooms(allRooms.filter((room: RoomResponse) => 
        room.status === 'CLEAN' || 
        room.status === 'DIRTY' || 
        initialRoomIds.includes(room.id)
      ));
    } catch (err: unknown) {
      const e = err as {response?: {data?: {detail?: string}}, message?: string};
      setError(e.response?.data?.detail || e.message || t('failed_load_data'));
    } finally {
      setFetching(false);
    }
  }, [id, t]);

  useEffect(() => {
    loadInitialData();
  }, [loadInitialData]);

  const handleSubmitReservation = useCallback(async (e: React.FormEvent) => {
    e.preventDefault();
    if (isView) return;

    if (!selectedGuest) {
      setError(t('msg_select_guest'));
      return;
    }
    if (selectedRoomIds.length === 0) {
      setError(t('msg_select_room'));
      return;
    }
    if (!checkInDate || !checkOutDate) {
      setError(t('msg_valid_dates'));
      return;
    }

    const hasOverlap = allReservations.some(r => {
      if (r.id === id || r.active === false || r.status === 'CANCELLED') return false;

      const overlapsAnyRoom = r.lineItems.some(li => li.active !== false && selectedRoomIds.includes(li.roomId));
      if (!overlapsAnyRoom) return false;

      const nIn = new Date(checkInDate).getTime();
      const nOut = new Date(checkOutDate).getTime();
      const rIn = new Date(r.checkInDate).getTime();
      const rOut = new Date(r.checkOutDate).getTime();

      return nIn < rOut && nOut > rIn;
    });

    if (hasOverlap) {
      setError(t('reservation_overlap_error'));
      return;
    }

    try {
      setLoading(true);
      setError(null);

      const request: ReservationRequest = {
        guestId: selectedGuest.id,
        checkInDate,
        checkOutDate,
        status: status,
        expectedGuests: Number(expectedGuests) || 1,
        lineItems: selectedRoomIds.map(roomId => {
          const room = rooms.find(r => r.id === roomId);
          return {
            roomId: roomId,
            price: room?.pricePerNight ?? room?.roomType?.basePrice ?? 0
          };
        })
      };

      if (id) {
        await reservationService.updateReservation(id, request);
      } else {
        await reservationService.createReservation(request);
      }
      navigate('/reservations');
    } catch (err: unknown) {
      const e = err as {response?: {data?: {detail?: string; errorCode?: string}}, message?: string};
      if (e.response?.data?.errorCode === 'GUEST_NOT_FOUND') {
         setError(t('err_guest_not_found'));
      } else {
         setError(e.response?.data?.detail || e.message || t(id ? 'failed_update_reservation' : 'failed_create_reservation'));
      }
    } finally {
      setLoading(false);
    }
  }, [isView, selectedGuest, selectedRoomIds, checkInDate, checkOutDate, status, expectedGuests, rooms, allReservations, id, t, navigate]);

  const toggleRoomSelection = useCallback((roomId: string) => {
    if (isView) return;
    setSelectedRoomIds(prev => 
      prev.includes(roomId) ? prev.filter(id => id !== roomId) : [...prev, roomId]
    );
  }, [isView]);

  const handleBackToReservations = useCallback(() => {
    navigate('/reservations');
  }, [navigate]);

  const handleClearGuest = useCallback(() => {
    setSelectedGuest(null);
  }, []);

  const titles = useMemo(() => {
    if (isEdit) return { title: t('edit_reservation'), subtitle: t('edit_reservation_subtitle') };
    if (isView) return { title: t('reservation_details'), subtitle: t('view_reservation_subtitle') };
    return { title: t('new_reservation'), subtitle: t('new_reservation_subtitle') };
  }, [isEdit, isView, t]);

  if (fetching) {
    return (
      <div className="flex justify-center items-center h-64">
        <MaterialIcon name="progress_activity" size={32} className="text-primary animate-spin" />
      </div>
    );
  }

  return (
    <form onSubmit={handleSubmitReservation} className="space-y-6 max-w-4xl mx-auto pb-10">
      <div className="flex items-center gap-4 border-b border-outline-variant pb-4">
        <button
          type="button"
          onClick={handleBackToReservations}
          className="p-2 rounded-full hover:bg-surface-variant transition-colors text-on-surface-variant"
        >
          <MaterialIcon name="arrow_back" />
        </button>
        <div>
          <h1 className="text-2xl font-display font-bold text-on-surface">{titles.title}</h1>
          <p className="text-sm text-on-surface-variant mt-1">{titles.subtitle}</p>
        </div>
      </div>

      {error && (
        <div className="p-4 bg-error-container text-on-error-container rounded-shape-sm flex items-start gap-3">
          <MaterialIcon name="error" />
          <p className="text-sm font-body mt-0.5">{error}</p>
        </div>
      )}

      {/* STEP 1: GUEST SELECTION OR CREATION */}
      <M3Card className="p-6 space-y-4">
        <div className="flex items-center gap-2 mb-4">
          <MaterialIcon name="person" className="text-primary" />
          <h2 className="text-lg font-medium text-on-surface">{t('step_primary_guest')}</h2>
        </div>
        <GuestSearchAndCreate 
          selectedGuest={selectedGuest}
          onSelectGuest={setSelectedGuest}
          onClearGuest={handleClearGuest}
          readOnly={isView}
        />
      </M3Card>

      {/* STEP 2: DATES & ROOMS */}
      <M3Card className="p-6 space-y-4">
        <div className="flex items-center gap-2 mb-4">
          <MaterialIcon name="event_seat" className="text-primary" />
          <h2 className="text-lg font-medium text-on-surface">{t('step_reservation_details')}</h2>
        </div>
        <RoomSelection 
          checkInDate={checkInDate}
          checkOutDate={checkOutDate}
          expectedGuests={expectedGuests}
          availableRooms={rooms}
          selectedRoomIds={selectedRoomIds}
          onCheckInChange={setCheckInDate}
          onCheckOutChange={setCheckOutDate}
          onExpectedGuestsChange={setExpectedGuests}
          onToggleRoom={toggleRoomSelection}
          readOnly={isView}
        />
      </M3Card>

      {/* SUBMIT */}
      <div className="flex justify-end pt-4 gap-3">
        <M3Button type="button" variant="text" onClick={handleBackToReservations}>
          {isView ? t('back') : t('cancel')}
        </M3Button>
        {!isView && (
          <M3Button 
            type="submit"
            loading={loading}
            disabled={!selectedGuest || !checkInDate || !checkOutDate || selectedRoomIds.length === 0}
          >
            {id ? t('update_reservation') : t('confirm_reservation')}
          </M3Button>
        )}
      </div>
    </form>
  );
};
