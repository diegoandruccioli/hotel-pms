import { useState, useEffect, useCallback, useMemo, memo } from 'react';
import { useNavigate, useParams } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import { z } from 'zod';
import { MaterialIcon } from '../../components/MaterialIcon';
import { M3Button } from '../../components/m3/M3Button';
import { M3Card } from '../../components/m3/M3Card';
import { M3TextField } from '../../components/m3/M3TextField';
import { inventoryService } from '../../services/inventoryService';
import { reservationService } from '../../services/reservationService';
import { guestService } from '../../services/guestService';
import { quotationService } from '../../services/quotationService';
import type { GuestResponseDTO } from '../../types';
import type { RoomResponse } from '../../types';
import type { ReservationResponse } from '../../types';
import type { QuotationOptionRequest, QuotationRequest } from '../../types';
import { RoomSelection } from '../Reservations/RoomSelection';
import { getErrorMessage } from '../../utils/errorMessage';
import { useToastStore } from '../../store/toastStore';

const DEFAULT_VALID_DAYS = 7;
const MAX_OPTIONS = 5;
const MS_PER_DAY = 1000 * 60 * 60 * 24;

interface OptionDraft {
  label: string;
  selectedRoomIds: string[];
}

const defaultOptionLabel = (index: number) => `Opzione ${index + 1}`;

const GuestSuggestionRow = memo(({ guest, onSelect }: { guest: GuestResponseDTO; onSelect: (guest: GuestResponseDTO) => void }) => {
  const handleClick = useCallback(() => onSelect(guest), [onSelect, guest]);
  return (
    <li>
      <button
        type="button"
        className="w-full text-left px-4 py-3 hover:bg-surface-variant transition-colors"
        onClick={handleClick}
      >
        <p className="font-medium text-on-surface">{guest.firstName} {guest.lastName}</p>
        <p className="text-sm text-on-surface-variant">{guest.email}</p>
      </button>
    </li>
  );
});
GuestSuggestionRow.displayName = 'GuestSuggestionRow';

const OptionTab = memo(({ option, index, isActive, canRemove, total, onSelect, onRemove }: {
  option: OptionDraft;
  index: number;
  isActive: boolean;
  canRemove: boolean;
  total: number;
  onSelect: (index: number) => void;
  onRemove: (index: number) => void;
}) => {
  const handleSelect = useCallback(() => onSelect(index), [onSelect, index]);
  const handleRemove = useCallback(() => onRemove(index), [onRemove, index]);
  return (
    <div className={`flex items-center rounded-shape-full border ${isActive ? 'border-primary bg-primary-container' : 'border-outline-variant'}`}>
      <button
        type="button"
        onClick={handleSelect}
        aria-pressed={isActive}
        className={`px-4 py-2 text-sm font-medium font-body rounded-shape-full focus-visible:outline-hidden focus-visible:ring-2 focus-visible:ring-primary ${isActive ? 'text-on-primary-container' : 'text-on-surface-variant'}`}
      >
        {option.label || defaultOptionLabel(index)} · € {total.toFixed(2)}
      </button>
      {canRemove && (
        <button
          type="button"
          onClick={handleRemove}
          aria-label={`Rimuovi ${option.label || defaultOptionLabel(index)}`}
          className="pr-3 pl-1 text-on-surface-variant hover:text-error focus-visible:outline-hidden focus-visible:ring-2 focus-visible:ring-primary rounded-shape-full"
        >
          <MaterialIcon name="close" size={16} />
        </button>
      )}
    </div>
  );
});
OptionTab.displayName = 'OptionTab';

const todayPlusDays = (days: number): string => {
  const d = new Date();
  d.setDate(d.getDate() + days);
  return d.toISOString().slice(0, 10);
};

export const QuotationForm = () => {
  const { t } = useTranslation(['quotations', 'guests', 'common']);
  const navigate = useNavigate();
  const { id } = useParams<{ id: string }>();
  const isEditMode = !!id;
  const addToast = useToastStore((s) => s.addToast);

  const [fetching, setFetching] = useState(false);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const [rooms, setRooms] = useState<RoomResponse[]>([]);
  const [allReservations, setAllReservations] = useState<ReservationResponse[]>([]);
  const [resolvedPrices, setResolvedPrices] = useState<Map<string, number>>(new Map());

  const [recipientMode, setRecipientMode] = useState<'guest' | 'prospect'>('guest');
  const [selectedGuest, setSelectedGuest] = useState<GuestResponseDTO | null>(null);
  const [guestQuery, setGuestQuery] = useState('');
  const [guestSuggestions, setGuestSuggestions] = useState<GuestResponseDTO[]>([]);
  const [prospectFirstName, setProspectFirstName] = useState('');
  const [prospectLastName, setProspectLastName] = useState('');
  const [prospectEmail, setProspectEmail] = useState('');

  const [checkInDate, setCheckInDate] = useState('');
  const [checkOutDate, setCheckOutDate] = useState('');
  const [expectedGuests, setExpectedGuests] = useState<number | string>(1);
  const [options, setOptions] = useState<OptionDraft[]>([{ label: defaultOptionLabel(0), selectedRoomIds: [] }]);
  const [activeOptionIndex, setActiveOptionIndex] = useState(0);
  const [validUntil, setValidUntil] = useState(() => todayPlusDays(DEFAULT_VALID_DAYS));

  const quotationSchema = useMemo(() => z.object({
    checkInDate: z.string().min(1, t('common:msg_valid_dates')),
    checkOutDate: z.string().min(1, t('common:msg_valid_dates')),
    validUntil: z.string().min(1, t('common:msg_valid_dates')),
    expectedGuests: z.coerce.number().int().positive(t('common:err_must_be_positive')),
  }).refine(
    (data) => new Date(data.checkOutDate).getTime() > new Date(data.checkInDate).getTime(),
    { message: t('common:msg_valid_dates'), path: ['checkOutDate'] },
  ), [t]);

  const loadInitialData = useCallback(async () => {
    try {
      setFetching(true);
      setError(null);
      const [roomsData, allRes, quotation] = await Promise.all([
        inventoryService.getAllRooms(),
        reservationService.getAllReservations(),
        isEditMode && id ? quotationService.getQuotationById(id) : Promise.resolve(null),
      ]);
      setRooms(roomsData.content);
      setAllReservations(allRes);

      if (quotation) {
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
          const guest = await guestService.getGuestById(quotation.guestId);
          setSelectedGuest(guest);
        } else {
          setRecipientMode('prospect');
          const [first, ...rest] = quotation.guestFullName.split(' ');
          setProspectFirstName(first ?? '');
          setProspectLastName(rest.join(' '));
          setProspectEmail(quotation.prospectEmail ?? '');
        }
      }
    } catch (err: unknown) {
      setError(getErrorMessage(err, t('common:failed_load_data')));
    } finally {
      setFetching(false);
    }
  }, [t, isEditMode, id]);

  useEffect(() => {
    loadInitialData();
  }, [loadInitialData]);

  useEffect(() => {
    if (!checkInDate || !checkOutDate || new Date(checkOutDate) <= new Date(checkInDate)) {
      setResolvedPrices(new Map());
      return;
    }
    let cancelled = false;
    inventoryService.getAvailableRooms(checkInDate, checkOutDate)
      .then((availableRooms) => {
        if (cancelled) return;
        const priceMap = new Map<string, number>();
        availableRooms.forEach((room) => {
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

  useEffect(() => {
    const delay = setTimeout(async () => {
      if (guestQuery.trim().length > 0) {
        try {
          const results = await guestService.searchGuests(guestQuery);
          setGuestSuggestions(results);
        } catch {
          setGuestSuggestions([]);
        }
      } else {
        setGuestSuggestions([]);
      }
    }, 300);
    return () => clearTimeout(delay);
  }, [guestQuery]);

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
            ? option.selectedRoomIds.filter((id) => id !== roomId)
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
    setSelectedGuest(guest);
    setGuestQuery('');
    setGuestSuggestions([]);
  }, []);
  const handleClearGuest = useCallback(() => setSelectedGuest(null), []);
  const handleProspectFirstNameChange = useCallback((e: React.ChangeEvent<HTMLInputElement>) => setProspectFirstName(e.target.value), []);
  const handleProspectLastNameChange = useCallback((e: React.ChangeEvent<HTMLInputElement>) => setProspectLastName(e.target.value), []);
  const handleProspectEmailChange = useCallback((e: React.ChangeEvent<HTMLInputElement>) => setProspectEmail(e.target.value), []);
  const handleValidUntilChange = useCallback((e: React.ChangeEvent<HTMLInputElement>) => setValidUntil(e.target.value), []);
  const handleBack = useCallback(
    () => navigate(isEditMode && id ? `/quotations/${id}` : '/quotations'),
    [navigate, isEditMode, id],
  );

  const handleSubmit = useCallback(async (e: React.FormEvent) => {
    e.preventDefault();

    const hasRecipient = recipientMode === 'guest'
      ? !!selectedGuest
      : prospectFirstName.trim() && prospectLastName.trim() && prospectEmail.trim();
    if (!hasRecipient) {
      setError(t('err_select_recipient'));
      return;
    }
    if (options.some((option) => option.selectedRoomIds.length === 0)) {
      setError(t('err_select_room'));
      return;
    }

    const parsed = quotationSchema.safeParse({ checkInDate, checkOutDate, validUntil, expectedGuests });
    if (!parsed.success) {
      setError(parsed.error.issues[0]?.message ?? t('common:msg_valid_dates'));
      return;
    }

    try {
      setLoading(true);
      setError(null);

      const optionRequests: QuotationOptionRequest[] = options.map((option, index) => ({
        label: option.label.trim() || defaultOptionLabel(index),
        roomIds: option.selectedRoomIds,
      }));

      const request: QuotationRequest = {
        guestId: recipientMode === 'guest' ? selectedGuest!.id : null,
        prospectFirstName: recipientMode === 'prospect' ? prospectFirstName.trim() : null,
        prospectLastName: recipientMode === 'prospect' ? prospectLastName.trim() : null,
        prospectEmail: recipientMode === 'prospect' ? prospectEmail.trim() : null,
        checkInDate: parsed.data.checkInDate,
        checkOutDate: parsed.data.checkOutDate,
        expectedGuests: parsed.data.expectedGuests,
        options: optionRequests,
        validUntil: parsed.data.validUntil,
      };

      if (isEditMode && id) {
        await quotationService.updateQuotation(id, request);
        addToast(t('toast_updated'), 'success');
        navigate(`/quotations/${id}`);
      } else {
        await quotationService.createQuotation(request);
        addToast(t('toast_created'), 'success');
        navigate('/quotations');
      }
    } catch (err: unknown) {
      setError(getErrorMessage(err, isEditMode ? t('toast_updated') : t('toast_created')));
    } finally {
      setLoading(false);
    }
  }, [recipientMode, selectedGuest, prospectFirstName, prospectLastName, prospectEmail,
      options, checkInDate, checkOutDate, validUntil, expectedGuests,
      quotationSchema, addToast, t, navigate, isEditMode, id]);

  if (fetching) {
    return (
      <div className="flex justify-center items-center h-64">
        <MaterialIcon name="progress_activity" size={32} className="text-primary animate-spin" />
      </div>
    );
  }

  return (
    <form onSubmit={handleSubmit} noValidate className="space-y-6 max-w-4xl mx-auto pb-10">
      <div className="flex items-center gap-4 border-b border-outline-variant pb-4">
        <button
          type="button"
          onClick={handleBack}
          className="p-2 rounded-full hover:bg-surface-variant transition-colors text-on-surface-variant"
          aria-label={t('common:back')}
        >
          <MaterialIcon name="arrow_back" />
        </button>
        <div>
          <h1 className="text-2xl font-display font-bold text-on-surface">
            {isEditMode ? t('edit_quotation') : t('new_quotation')}
          </h1>
        </div>
      </div>

      {error && (
        <div className="p-4 bg-error-container text-on-error-container rounded-shape-sm flex items-start gap-3">
          <MaterialIcon name="error" />
          <p className="text-sm font-body mt-0.5">{error}</p>
        </div>
      )}

      <M3Card className="p-6 space-y-4">
        <div className="flex items-center gap-2 mb-2">
          <MaterialIcon name="person" className="text-primary" />
          <h2 className="text-lg font-medium text-on-surface">{t('step_recipient')}</h2>
        </div>

        <div className="flex gap-2" role="group" aria-label={t('heading_recipient')}>
          <M3Button
            type="button"
            variant={recipientMode === 'guest' ? 'filled' : 'outlined'}
            onClick={handleGuestModeClick}
          >
            {t('toggle_existing_guest')}
          </M3Button>
          <M3Button
            type="button"
            variant={recipientMode === 'prospect' ? 'filled' : 'outlined'}
            onClick={handleProspectModeClick}
          >
            {t('toggle_new_prospect')}
          </M3Button>
        </div>

        {recipientMode === 'guest' ? (
          selectedGuest ? (
            <div className="p-4 border border-primary rounded-shape-md bg-primary/5 flex justify-between items-center">
              <div>
                <p className="font-medium text-on-surface">{selectedGuest.firstName} {selectedGuest.lastName}</p>
                <p className="text-sm text-on-surface-variant">{selectedGuest.email}</p>
              </div>
              <M3Button variant="text" icon="edit" onClick={handleClearGuest}>{t('guests:btn_change')}</M3Button>
            </div>
          ) : (
            <div className="space-y-2">
              <M3TextField
                label={t('guests:search_guest_placeholder')}
                leadingIcon="search"
                value={guestQuery}
                onChange={handleGuestQueryChange}
              />
              {guestQuery && (
                <div className="border border-outline-variant rounded-shape-sm max-h-48 overflow-y-auto">
                  {guestSuggestions.length > 0 ? (
                    <ul className="divide-y divide-outline-variant">
                      {guestSuggestions.map((guest) => (
                        <GuestSuggestionRow key={guest.id} guest={guest} onSelect={handleSelectGuest} />
                      ))}
                    </ul>
                  ) : (
                    <p className="p-4 text-center text-sm text-on-surface-variant">{t('guests:no_guests_search')}</p>
                  )}
                </div>
              )}
            </div>
          )
        ) : (
          <div className="grid grid-cols-1 md:grid-cols-3 gap-4">
            <M3TextField label={t('label_prospect_first_name')} value={prospectFirstName} onChange={handleProspectFirstNameChange} required />
            <M3TextField label={t('label_prospect_last_name')} value={prospectLastName} onChange={handleProspectLastNameChange} required />
            <M3TextField label={t('label_prospect_email')} type="email" value={prospectEmail} onChange={handleProspectEmailChange} required />
          </div>
        )}
      </M3Card>

      <M3Card className="p-6 space-y-4">
        <div className="flex items-center justify-between gap-2 mb-2">
          <div className="flex items-center gap-2">
            <MaterialIcon name="event_seat" className="text-primary" />
            <h2 className="text-lg font-medium text-on-surface">{t('step_stay_details')}</h2>
          </div>
        </div>

        <div className="flex flex-wrap items-center gap-2" role="group" aria-label={t('label_options')}>
          {options.map((option, index) => (
            <OptionTab
              key={index}
              option={option}
              index={index}
              isActive={index === activeOptionIndex}
              canRemove={options.length > 1}
              total={optionTotal(option)}
              onSelect={handleSelectOptionTab}
              onRemove={handleRemoveOption}
            />
          ))}
          {options.length < MAX_OPTIONS && (
            <M3Button type="button" variant="text" icon="add" onClick={handleAddOption}>
              {t('action_add_option')}
            </M3Button>
          )}
        </div>

        <M3TextField
          label={t('label_option_name')}
          value={activeOption.label}
          onChange={handleActiveOptionLabelChange}
          className="max-w-xs"
        />

        <RoomSelection
          checkInDate={checkInDate}
          checkOutDate={checkOutDate}
          expectedGuests={expectedGuests}
          availableRooms={rooms}
          selectedRoomIds={activeOption.selectedRoomIds}
          allReservations={allReservations}
          resolvedPrices={resolvedPrices}
          onCheckInChange={setCheckInDate}
          onCheckOutChange={setCheckOutDate}
          onExpectedGuestsChange={setExpectedGuests}
          onToggleRoom={toggleRoomSelection}
        />
        <M3TextField
          label={t('label_valid_until')}
          type="date"
          value={validUntil}
          onChange={handleValidUntilChange}
          required
          className="max-w-xs"
        />
        {activeOption.selectedRoomIds.length > 0 && (
          <p className="text-sm font-medium text-on-surface">
            {t('quotation_total', { amount: `€ ${optionTotal(activeOption).toFixed(2)}` })}
          </p>
        )}
      </M3Card>

      <div className="flex justify-end pt-4 gap-3">
        <M3Button type="button" variant="text" onClick={handleBack}>{t('common:cancel')}</M3Button>
        <M3Button type="submit" loading={loading}>{t('common:save')}</M3Button>
      </div>
    </form>
  );
};
