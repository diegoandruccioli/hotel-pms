import { useState, useCallback, useMemo } from 'react';
import { useParams } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import type { GuestResponseDTO, RoomResponse } from '../../types';
import { useDebounce } from '../../hooks';
import {
  useAvailableRoomPrices,
  useGuestById,
  useGuestSuggestions,
  useQuotation,
  useReservationsSnapshot,
  useRoomsList,
} from '../../hooks/queries';
import { addDaysIso, getErrorMessage, todayIsoDate } from '../../utils';
import { DEFAULT_VALID_DAYS, MAX_OPTIONS, defaultOptionLabel } from './quotationDraft';
import type { OptionDraft } from './quotationDraft';

const MS_PER_DAY = 1000 * 60 * 60 * 24;
const EMPTY_ROOMS: RoomResponse[] = [];
const EMPTY_GUESTS: GuestResponseDTO[] = [];

/**
 * Form state of the quotation create/edit page: recipient, stay window, the
 * per-option room picks and their totals. Reference data (rooms, bookings,
 * resolved prices, guest lookups) and the quotation being edited come from
 * react-query; edit mode copies the loaded quotation into the draft once.
 */
export function useQuotationDraft() {
  const { t } = useTranslation(['quotations', 'common']);
  const { id } = useParams<{ id: string }>();
  const isEditMode = !!id;

  const [recipientMode, setRecipientMode] = useState<'guest' | 'prospect'>('guest');
  // undefined = nothing chosen yet (fall back to the guest of the loaded quotation); null = cleared on purpose
  const [guestOverride, setGuestOverride] = useState<GuestResponseDTO | null | undefined>(undefined);
  const [guestQuery, setGuestQuery] = useState('');
  const [prospectFirstName, setProspectFirstName] = useState('');
  const [prospectLastName, setProspectLastName] = useState('');
  const [prospectEmail, setProspectEmail] = useState('');

  const [checkInDate, setCheckInDate] = useState('');
  const [checkOutDate, setCheckOutDate] = useState('');
  const [expectedGuests, setExpectedGuests] = useState<number | string>(1);
  const [options, setOptions] = useState<OptionDraft[]>([{ label: defaultOptionLabel(0), selectedRoomIds: [] }]);
  const [activeOptionIndex, setActiveOptionIndex] = useState(0);
  const [validUntil, setValidUntil] = useState(() => addDaysIso(todayIsoDate(), DEFAULT_VALID_DAYS));

  const roomsQuery = useRoomsList(false);
  const reservationsQuery = useReservationsSnapshot();
  const quotationQuery = useQuotation(id);
  const quotation = quotationQuery.data;
  const loadedGuestQuery = useGuestById(quotation?.guestId);
  const rooms = roomsQuery.data ?? EMPTY_ROOMS;
  const allReservations = reservationsQuery.data ?? [];
  const resolvedPrices = useAvailableRoomPrices(checkInDate, checkOutDate);

  // Copy the quotation being edited into the draft once, when it first arrives
  // (state adjusted during render, not in an effect).
  const [hydratedId, setHydratedId] = useState<string | null>(null);
  if (quotation && hydratedId !== quotation.id) {
    setHydratedId(quotation.id);
    setCheckInDate(quotation.checkInDate);
    setCheckOutDate(quotation.checkOutDate);
    setExpectedGuests(quotation.expectedGuests ?? 1);
    setValidUntil(quotation.validUntil);
    setOptions(quotation.options.map((opt) => ({
      label: opt.label,
      selectedRoomIds: opt.lineItems.map((li) => li.roomId),
    })));
    if (quotation.guestId) {
      setRecipientMode('guest');
    } else {
      setRecipientMode('prospect');
      const [first, ...rest] = quotation.guestFullName.split(' ');
      setProspectFirstName(first ?? '');
      setProspectLastName(rest.join(' '));
      setProspectEmail(quotation.prospectEmail ?? '');
    }
  }

  const selectedGuest = guestOverride !== undefined ? guestOverride : (loadedGuestQuery.data ?? null);

  const debouncedGuestQuery = useDebounce(guestQuery);
  const suggestionsQuery = useGuestSuggestions(debouncedGuestQuery);
  const guestSuggestions = guestQuery.trim() ? (suggestionsQuery.data ?? EMPTY_GUESTS) : EMPTY_GUESTS;

  const fetching = roomsQuery.isLoading || reservationsQuery.isLoading
    || (isEditMode && (quotationQuery.isLoading || loadedGuestQuery.isLoading));
  const loadFailure = roomsQuery.error ?? reservationsQuery.error ?? quotationQuery.error ?? loadedGuestQuery.error;
  const loadError = loadFailure ? getErrorMessage(loadFailure, t('common:failed_load_data')) : null;

  const nights = useMemo(() => {
    if (!checkInDate || !checkOutDate) return 0;
    const diff = new Date(checkOutDate).getTime() - new Date(checkInDate).getTime();
    return diff > 0 ? Math.round(diff / MS_PER_DAY) : 0;
  }, [checkInDate, checkOutDate]);

  const optionTotal = useCallback(
    (option: OptionDraft) => option.selectedRoomIds.reduce((sum, roomId) => {
      const resolved = resolvedPrices.get(roomId);
      if (resolved !== undefined) return sum + resolved;
      const room = rooms.find((r) => r.id === roomId);
      return sum + (room ? room.roomType.basePrice * nights : 0);
    }, 0),
    [resolvedPrices, rooms, nights],
  );

  const activeOption = options[activeOptionIndex] ?? options[0];

  const toggleRoomSelection = useCallback((roomId: string) => {
    setOptions((prev) => prev.map((option, index) => (
      index === activeOptionIndex
        ? {
          ...option,
          selectedRoomIds: option.selectedRoomIds.includes(roomId)
            ? option.selectedRoomIds.filter((rid) => rid !== roomId)
            : [...option.selectedRoomIds, roomId],
        }
        : option
    )));
  }, [activeOptionIndex]);

  const handleSelectOptionTab = useCallback((index: number) => setActiveOptionIndex(index), []);

  const handleAddOption = useCallback(() => {
    setOptions((prev) => {
      if (prev.length >= MAX_OPTIONS) return prev;
      const next = [...prev, { label: defaultOptionLabel(prev.length), selectedRoomIds: [] }];
      setActiveOptionIndex(next.length - 1);
      return next;
    });
  }, []);

  const handleRemoveOption = useCallback((index: number) => {
    setOptions((prev) => {
      if (prev.length <= 1) return prev;
      const next = prev.filter((_, i) => i !== index);
      setActiveOptionIndex((current) => Math.min(current, next.length - 1));
      return next;
    });
  }, []);

  const handleActiveOptionLabelChange = useCallback((e: React.ChangeEvent<HTMLInputElement>) => {
    const value = e.target.value;
    setOptions((prev) => prev.map((option, index) => (index === activeOptionIndex ? { ...option, label: value } : option)));
  }, [activeOptionIndex]);

  const handleGuestModeClick = useCallback(() => setRecipientMode('guest'), []);
  const handleProspectModeClick = useCallback(() => setRecipientMode('prospect'), []);
  const handleGuestQueryChange = useCallback((e: React.ChangeEvent<HTMLInputElement>) => setGuestQuery(e.target.value), []);
  const handleSelectGuest = useCallback((guest: GuestResponseDTO) => {
    setGuestOverride(guest);
    setGuestQuery('');
  }, []);
  const handleClearGuest = useCallback(() => setGuestOverride(null), []);
  const handleProspectFirstNameChange = useCallback((e: React.ChangeEvent<HTMLInputElement>) => setProspectFirstName(e.target.value), []);
  const handleProspectLastNameChange = useCallback((e: React.ChangeEvent<HTMLInputElement>) => setProspectLastName(e.target.value), []);
  const handleProspectEmailChange = useCallback((e: React.ChangeEvent<HTMLInputElement>) => setProspectEmail(e.target.value), []);
  const handleValidUntilChange = useCallback((e: React.ChangeEvent<HTMLInputElement>) => setValidUntil(e.target.value), []);

  return {
    id, isEditMode, fetching, loadError,
    rooms, allReservations, resolvedPrices,
    recipientMode, selectedGuest, guestQuery, guestSuggestions,
    prospectFirstName, prospectLastName, prospectEmail,
    checkInDate, checkOutDate, expectedGuests, validUntil, options, activeOptionIndex, activeOption,
    setCheckInDate, setCheckOutDate, setExpectedGuests,
    optionTotal, toggleRoomSelection,
    handleSelectOptionTab, handleAddOption, handleRemoveOption, handleActiveOptionLabelChange,
    handleGuestModeClick, handleProspectModeClick, handleGuestQueryChange, handleSelectGuest, handleClearGuest,
    handleProspectFirstNameChange, handleProspectLastNameChange, handleProspectEmailChange, handleValidUntilChange,
  };
}
