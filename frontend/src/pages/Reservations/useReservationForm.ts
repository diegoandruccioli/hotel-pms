import { useState, useEffect, useCallback, useMemo, useRef } from 'react';
import { useNavigate, useParams, useLocation } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import { z } from 'zod';
import { inventoryService, reservationService, guestService, stayService } from '../../services';
import { fetchAllRooms } from '../../lib';
import type { GuestResponseDTO, RoomResponse } from '../../types';
import type { ReservationRequest, ReservationResponse, ReservationStatus } from '../../types';
import { addDays, format, isValid } from 'date-fns';
import { isReservationEditable } from './reservationStatus';

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

/** Day (and room) picked on the calendar, handed over through router state; anything malformed is ignored. */
const slotFromState = (state: unknown): { checkInDate: string; checkOutDate: string; roomId: string | null } | null => {
  if (typeof state !== 'object' || state === null) return null;
  const { checkInDate, roomId } = state as Record<string, unknown>;
  if (typeof checkInDate !== 'string' || !/^\d{4}-\d{2}-\d{2}$/.test(checkInDate)) return null;
  const arrival = new Date(`${checkInDate}T00:00:00`);
  if (!isValid(arrival)) return null;
  return {
    checkInDate,
    checkOutDate: format(addDays(arrival, 1), 'yyyy-MM-dd'),
    roomId: typeof roomId === 'string' ? roomId : null,
  };
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
  const slot = useMemo(() => (id ? null : slotFromState(location.state)), [id, location.state]);
  const [checkInDate, setCheckInDate] = useState(() => slot?.checkInDate ?? '');
  const [checkOutDate, setCheckOutDate] = useState(() => slot?.checkOutDate ?? '');
  const [expectedGuests, setExpectedGuests] = useState<number | string>(1);
  const [selectedRoomIds, setSelectedRoomIds] = useState<string[]>(() => (slot?.roomId ? [slot.roomId] : []));
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
  const [groupId, setGroupId] = useState<string | null>(null);
  // The reservation being viewed/edited could not be read: the defaults below (status, dates...) are
  // placeholders, so the form must not present them as that reservation's data or offer its actions.
  const [loadFailed, setLoadFailed] = useState(false);

  const reservationSchema = useMemo(() => z.object({
    checkInDate: z.string().min(1, t('msg_valid_dates')),
    checkOutDate: z.string().min(1, t('msg_valid_dates')),
    expectedGuests: z.coerce.number().int().positive(t('err_must_be_positive')),
  }).refine(
    (data) => new Date(data.checkOutDate).getTime() > new Date(data.checkInDate).getTime(),
    { message: t('msg_valid_dates'), path: ['checkOutDate'] },
  ), [t]);

  // Each load gets a number; answers of an older load (another reservation, a retry, a closed page)
  // are dropped instead of overwriting the state of the current one.
  const loadSeq = useRef(0);

  const loadInitialData = useCallback(async () => {
    const seq = ++loadSeq.current;
    const isCurrent = () => seq === loadSeq.current;
    try {
      setFetching(true);
      setError(null);
      setLoadFailed(false);

      // The three reads that decide what the page shows go out together instead of one after the
      // other: one slow round trip no longer adds up with the rest.
      const [allRooms, allRes, res] = await Promise.all([
        fetchAllRooms(),
        reservationService.getAllReservations(),
        id ? reservationService.getReservationById(id) : Promise.resolve(null),
      ]);
      if (!isCurrent()) return;
      setAllReservations(allRes);

      if (id && res) {
        if (isEdit && !isReservationEditable(res.status)) {
          navigate(`/reservations/${id}`, { replace: true });
          return;
        }
        setCheckInDate(res.checkInDate || '');
        setCheckOutDate(res.checkOutDate || '');
        setExpectedGuests(res.expectedGuests || 1);
        setSelectedRoomIds(res.lineItems?.map(li => li.roomId) || []);
        setStatus(res.status || 'CONFIRMED');
        setVersion(res.version ?? null);
        setGroupId(res.groupId ?? null);
        setSavedPricing({
          checkInDate: res.checkInDate || '',
          checkOutDate: res.checkOutDate || '',
          prices: new Map(
            (res.lineItems ?? [])
              .filter(li => li.active !== false && typeof li.price === 'number')
              .map(li => [li.roomId, li.price] as const),
          ),
        });

        // Secondary lookups: they enrich the page but never hold it back. The page opens as soon as
        // the reservation is read; a slow or failed answer here just leaves the banner hidden / the
        // guest named from the reservation itself.
        stayService.getStaysByReservationId(id)
          .then(stays => {
            if (!isCurrent()) return;
            const openStay = stays.content.find(s => s.status === 'CHECKED_IN');
            setCheckedInStayId(openStay?.id ?? null);
          })
          .catch(() => {
            if (isCurrent()) setCheckedInStayId(null);
          });

        if (res.guestId) {
          const guestId = res.guestId;
          setSelectedGuest({ id: guestId, firstName: res.guestFullName || '', lastName: '' } as GuestResponseDTO);
          guestService.getGuestById(guestId)
            .then(guest => {
              // Only if the operator has not picked another guest while the profile was on its way.
              if (isCurrent()) setSelectedGuest(current => (current?.id === guestId ? guest : current));
            })
            .catch((err: unknown) => {
              console.error('Failed to fetch guest details', err);
            });
        }
      }

      setRooms(allRooms);
    } catch (err: unknown) {
      if (!isCurrent()) return;
      const e = err as {response?: {data?: {detail?: string}}, message?: string};
      setError(e.response?.data?.detail || e.message || t('failed_load_data'));
      if (id) setLoadFailed(true);
    } finally {
      if (isCurrent()) setFetching(false);
    }
  }, [id, isEdit, navigate, t]);

  useEffect(() => {
    loadInitialData();
    return () => { loadSeq.current += 1; };
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

  // Back to wherever the user came from (group detail, night audit, quotation...);
  // a direct visit has no in-app history to return to, so fall back to the list.
  const goBack = useCallback(() => {
    if (location.key !== 'default') navigate(-1);
    else navigate('/reservations');
  }, [location.key, navigate]);

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
      if (id) goBack(); else navigate('/reservations');
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
      status, selectedRoomIds, version, id, navigate, goBack, t]);

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

  const goToList = goBack;

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

  const goToCheckIn = useCallback(() => {
    if (!id || !selectedGuest) return;
    navigate(`/stays/check-in/${id}`, {
      state: { roomId: selectedRoomIds[0] ?? '', expectedGuests: Number(expectedGuests), guestId: selectedGuest.id },
    });
  }, [id, selectedGuest, selectedRoomIds, expectedGuests, navigate]);

  const goToEdit = useCallback(() => {
    if (id) navigate(`/reservations/edit/${id}`);
  }, [id, navigate]);

  return {
    id, isEdit, isView, status, groupId, loadFailed, reload: loadInitialData,
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
    goToList, goToStay, goToEdit, goToCheckIn,
  };
};
