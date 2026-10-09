import { useState, useEffect, useCallback, useMemo, useRef } from 'react';
import { useNavigate, useParams, useLocation } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import { z } from 'zod';
import { inventoryService, reservationService, guestService, stayService } from '../../services';
import type { GuestResponseDTO, RoomResponse } from '../../types';
import type { ReservationRequest, ReservationResponse, ReservationStatus } from '../../types';

export const STEP_DATES = 0;
export const STEP_ROOMS = 1;
export const STEP_GUEST = 2;
export const STEP_SUMMARY = 3;

/** Steps that carry their own validation, in the order they are checked on save. */
const VALIDATED_STEPS = [STEP_DATES, STEP_ROOMS, STEP_GUEST];

/** Guest handed over through router state (e.g. "New reservation" from the guest sheet); anything malformed is ignored. */
const guestFromState = (state: unknown): GuestResponseDTO | null => {
  if (typeof state !== 'object' || state === null || !('guest' in state)) return null;
  const guest = state.guest;
  if (typeof guest !== 'object' || guest === null) return null;
  const { id, firstName, lastName } = guest as Record<string, unknown>;
  return typeof id === 'string' && typeof firstName === 'string' && typeof lastName === 'string'
    ? (guest as GuestResponseDTO)
    : null;
};

const overlapsExisting = (
  allReservations: ReservationResponse[],
  currentId: string | undefined,
  roomIds: string[],
  checkInDate: string,
  checkOutDate: string,
): boolean => allReservations.some(r => {
  if (r.id === currentId || r.active === false || r.status === 'CANCELLED') return false;

  const overlapsAnyRoom = r.lineItems.some(li => li.active !== false && roomIds.includes(li.roomId));
  if (!overlapsAnyRoom) return false;

  const nIn = new Date(checkInDate).getTime();
  const nOut = new Date(checkOutDate).getTime();
  const rIn = new Date(r.checkInDate).getTime();
  const rOut = new Date(r.checkOutDate).getTime();

  return nIn < rOut && nOut > rIn;
});

/**
 * State and actions of the reservation flow (new / edit / view). The page component only
 * lays out the steps; validation, the optimistic-lock version, the overlap check and the
 * stale-version conflict all live here.
 */
export const useReservationForm = () => {
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
  const [step, setStep] = useState(STEP_DATES);
  // Furthest step reached while creating; editing can jump anywhere.
  const [maxStep, setMaxStep] = useState(STEP_DATES);

  // Data
  const [rooms, setRooms] = useState<RoomResponse[]>([]);
  const [allReservations, setAllReservations] = useState<ReservationResponse[]>([]);
  const [resolvedPrices, setResolvedPrices] = useState<Map<string, number>>(new Map());
  // Stay totals already stored on the reservation being edited, with the dates they were
  // priced for. The availability lookup leaves out the rooms this reservation holds (they
  // overlap with itself), so without these the edit summary would have no prices at all.
  const [savedPricing, setSavedPricing] = useState<{
    checkInDate: string; checkOutDate: string; prices: Map<string, number>;
  } | null>(null);

  // Reservation state
  const [selectedGuest, setSelectedGuest] = useState<GuestResponseDTO | null>(
    () => (id ? null : guestFromState(location.state)),
  );
  const [checkInDate, setCheckInDate] = useState('');
  const [checkOutDate, setCheckOutDate] = useState('');
  const [expectedGuests, setExpectedGuests] = useState<number | string>(1);
  const [selectedRoomIds, setSelectedRoomIds] = useState<string[]>([]);
  const [status, setStatus] = useState<ReservationStatus>('CONFIRMED');
  // Optimistic-lock version last read from the server; echoed back on update so a
  // stale save (someone else changed this reservation while this tab sat open) is
  // rejected with a conflict instead of silently overwriting their change.
  const [version, setVersion] = useState<number | null>(null);
  const [staleConflict, setStaleConflict] = useState(false);

  // Parte 3: this reservation's own stay is a separate record (see StayGuestManagerDialog /
  // the extend-stay action on the Stays list) — editing dates/rooms here never touches it.
  // A non-blocking banner steers the operator there instead of leaving them confused why a
  // save here didn't move the guest's room or dates.
  const [checkedInStayId, setCheckedInStayId] = useState<string | null>(null);

  const reservationSchema = useMemo(() => z.object({
    checkInDate: z.string().min(1, t('msg_valid_dates')),
    checkOutDate: z.string().min(1, t('msg_valid_dates')),
    expectedGuests: z.coerce.number().int().positive(t('err_must_be_positive')),
  }).refine(
    (data) => new Date(data.checkOutDate).getTime() > new Date(data.checkInDate).getTime(),
    { message: t('msg_valid_dates'), path: ['checkOutDate'] },
  ), [t]);

  const loadInitialData = useCallback(async () => {
    try {
      setFetching(true);
      setError(null);

      const roomsData = await inventoryService.getAllRooms();
      const allRooms = roomsData.content;

      const allRes = await reservationService.getAllReservations();
      setAllReservations(allRes);

      if (id) {
        const res = await reservationService.getReservationById(id);
        setCheckInDate(res.checkInDate || '');
        setCheckOutDate(res.checkOutDate || '');
        setExpectedGuests(res.expectedGuests || 1);
        setSelectedRoomIds(res.lineItems?.map(li => li.roomId) || []);
        setStatus(res.status || 'CONFIRMED');
        setVersion(res.version ?? null);
        setSavedPricing({
          checkInDate: res.checkInDate || '',
          checkOutDate: res.checkOutDate || '',
          prices: new Map(
            (res.lineItems ?? [])
              .filter(li => li.active !== false && typeof li.price === 'number')
              .map(li => [li.roomId, li.price] as const),
          ),
        });

        // Non-blocking: a stay lookup failure must never prevent viewing/editing the
        // reservation itself, so any error here just leaves the banner hidden.
        try {
          const stays = await stayService.getStaysByReservationId(id);
          const openStay = stays.content.find(s => s.status === 'CHECKED_IN');
          setCheckedInStayId(openStay?.id ?? null);
        } catch {
          setCheckedInStayId(null);
        }

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

      setRooms(allRooms);
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

  // Resolves a date-aware price per room once both dates are set (RatePricingService,
  // via the same availability endpoint). Display-only: the server always recomputes
  // the authoritative price on submit, this just shows staff a real number up front
  // instead of the flat, date-blind RoomType.basePrice.
  useEffect(() => {
    if (!checkInDate || !checkOutDate || new Date(checkOutDate) <= new Date(checkInDate)) {
      setResolvedPrices(new Map());
      return;
    }
    let cancelled = false;
    inventoryService.getAvailableRooms(checkInDate, checkOutDate)
      .then(availableRooms => {
        if (cancelled) return;
        const priceMap = new Map<string, number>();
        availableRooms.forEach(room => {
          if (room.resolvedTotalPrice !== undefined) {
            priceMap.set(room.id, room.resolvedTotalPrice);
          }
        });
        setResolvedPrices(priceMap);
      })
      .catch(() => {
        if (!cancelled) setResolvedPrices(new Map());
      });
    return () => { cancelled = true; };
  }, [checkInDate, checkOutDate]);

  // Saved totals only hold for the dates they were priced for; fresh lookups win over them.
  const displayPrices = useMemo(() => {
    if (!savedPricing || savedPricing.checkInDate !== checkInDate || savedPricing.checkOutDate !== checkOutDate) {
      return resolvedPrices;
    }
    const merged = new Map(savedPricing.prices);
    resolvedPrices.forEach((price, roomId) => merged.set(roomId, price));
    return merged;
  }, [savedPricing, checkInDate, checkOutDate, resolvedPrices]);

  /** Error message for one step, or `null` when it is valid. */
  const validateStep = useCallback((target: number): string | null => {
    if (target === STEP_DATES) {
      const parsed = reservationSchema.safeParse({ checkInDate, checkOutDate, expectedGuests });
      return parsed.success ? null : (parsed.error.issues[0]?.message ?? t('msg_valid_dates'));
    }
    if (target === STEP_ROOMS) {
      if (selectedRoomIds.length === 0) return t('msg_select_room');
      return overlapsExisting(allReservations, id, selectedRoomIds, checkInDate, checkOutDate)
        ? t('reservation_overlap_error')
        : null;
    }
    if (target === STEP_GUEST) {
      return selectedGuest ? null : t('msg_select_guest');
    }
    return null;
  }, [reservationSchema, checkInDate, checkOutDate, expectedGuests, selectedRoomIds, allReservations, id, selectedGuest, t]);

  const isStepSelectable = useCallback(
    (target: number) => isEdit || target <= maxStep,
    [isEdit, maxStep],
  );

  const goToStep = useCallback((target: number) => {
    if (!isStepSelectable(target)) return;
    // Creating: jumping forward must pass every step on the way, like Next does.
    if (!isEdit && target > step) {
      for (let s = step; s < target; s++) {
        const message = validateStep(s);
        if (message) {
          setError(message);
          setStep(s);
          return;
        }
      }
    }
    setError(null);
    setStep(target);
  }, [isStepSelectable, isEdit, step, validateStep]);

  const goNext = useCallback(() => {
    const message = validateStep(step);
    if (message) {
      setError(message);
      return;
    }
    const next = Math.min(step + 1, STEP_SUMMARY);
    setError(null);
    setStep(next);
    setMaxStep(prev => Math.max(prev, next));
  }, [step, validateStep]);

  const goPrevious = useCallback(() => {
    setError(null);
    setStep(prev => Math.max(prev - 1, STEP_DATES));
  }, []);

  // Set synchronously: `loading` only disables the buttons after the next render, so a fast
  // double click (or panel Save + Confirm) could otherwise send the request twice.
  const savingRef = useRef(false);

  const save = useCallback(async () => {
    if (isView || savingRef.current) return;

    for (const target of VALIDATED_STEPS) {
      const message = validateStep(target);
      if (message) {
        setError(message);
        setStep(target);
        return;
      }
    }
    if (!selectedGuest) return;

    const parsed = reservationSchema.parse({ checkInDate, checkOutDate, expectedGuests });

    try {
      savingRef.current = true;
      setLoading(true);
      setError(null);

      const request: ReservationRequest = {
        guestId: selectedGuest.id,
        checkInDate: parsed.checkInDate,
        checkOutDate: parsed.checkOutDate,
        status: status,
        expectedGuests: parsed.expectedGuests,
        // price is resolved server-side (RatePricingService) and never
        // accepted from the client — see ReservationLineItemRequest.
        lineItems: selectedRoomIds.map(roomId => ({ roomId })),
        version
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
      } else if (e.response?.data?.errorCode === 'RESERVATION_STALE_VERSION') {
         // Someone else saved this reservation while this tab sat open — surface a
         // reload/cancel choice instead of an inline error the operator might just
         // retry through, silently clobbering the other edit a second time.
         setStaleConflict(true);
      } else {
         setError(e.response?.data?.detail || e.message || t(id ? 'failed_update_reservation' : 'failed_create_reservation'));
      }
    } finally {
      savingRef.current = false;
      setLoading(false);
    }
  }, [isView, validateStep, selectedGuest, reservationSchema, checkInDate, checkOutDate, expectedGuests,
      status, selectedRoomIds, version, id, navigate, t]);

  /** Enter inside a step moves forward; only the last step actually saves. */
  const handleFormSubmit = useCallback((e: React.FormEvent) => {
    e.preventDefault();
    if (isView) return;
    if (step < STEP_SUMMARY) {
      goNext();
    } else {
      void save();
    }
  }, [isView, step, goNext, save]);

  const handleReloadAfterConflict = useCallback(() => {
    setStaleConflict(false);
    loadInitialData();
  }, [loadInitialData]);

  const goToList = useCallback(() => navigate('/reservations'), [navigate]);

  const handleCancelAfterConflict = useCallback(() => {
    setStaleConflict(false);
    goToList();
  }, [goToList]);

  const toggleRoom = useCallback((roomId: string) => {
    if (isView || checkedInStayId) return;
    setSelectedRoomIds(prev =>
      prev.includes(roomId) ? prev.filter(rid => rid !== roomId) : [...prev, roomId]
    );
  }, [isView, checkedInStayId]);

  const clearGuest = useCallback(() => setSelectedGuest(null), []);

  const goToStay = useCallback(() => {
    navigate('/stays', { state: { statusFilter: 'CHECKED_IN' } });
  }, [navigate]);

  const goToEdit = useCallback(() => {
    if (id) navigate(`/reservations/edit/${id}`);
  }, [id, navigate]);

  return {
    id, isEdit, isView,
    loading, fetching, error,
    step, isStepSelectable, goToStep, goNext, goPrevious,
    rooms, allReservations, resolvedPrices: displayPrices,
    selectedGuest, setSelectedGuest, clearGuest,
    checkInDate, setCheckInDate, checkOutDate, setCheckOutDate,
    expectedGuests, setExpectedGuests,
    selectedRoomIds, toggleRoom,
    checkedInStayId, staleConflict,
    save, handleFormSubmit,
    handleReloadAfterConflict, handleCancelAfterConflict,
    goToList, goToStay, goToEdit,
  };
};
