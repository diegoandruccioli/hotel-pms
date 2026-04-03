import { useState, useCallback, memo } from 'react';
import type { FormEvent } from 'react';
import { useLocation, useNavigate, useParams } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import { MaterialIcon } from '../../components/MaterialIcon';
import { M3Button } from '../../components/m3/M3Button';
import { M3Card } from '../../components/m3/M3Card';
import { M3TextField } from '../../components/m3/M3TextField';
import { stayService } from '../../services/stayService';
import type { StayRequest, StayGuestRequest } from '../../types/stay.types';

const ICON_SIZE_20 = { fontSize: 20 };

interface CheckInState {
  guestId: string;
  roomId: string;
  expectedGuests: number;
}

interface IdentifiableGuest extends StayGuestRequest {
  _id: string; // Internal ID for React keys
}

const emptyGuest = (isPrimary: boolean): IdentifiableGuest => ({
  _id: Math.random().toString(36).substr(2, 9),
  firstName: '',
  lastName: '',
  gender: '',
  dateOfBirth: '',
  placeOfBirth: '',
  citizenship: '',
  documentType: '',
  documentNumber: '',
  documentPlaceOfIssue: '',
  isPrimaryGuest: isPrimary,
  travellerType: isPrimary ? 'OSPITE_SINGOLO' : '',
  travelPurpose: '',
});

const GuestFieldSection = memo(({ 
  guest, 
  index, 
  canRemove, 
  onRemove, 
  onChange,
}: {
  guest: IdentifiableGuest;
  index: number;
  canRemove: boolean;
  onRemove: (idx: number) => void;
  onChange: (idx: number, field: keyof StayGuestRequest, value: string | boolean) => void;
}) => {
  const handleFieldChange = useCallback((e: React.ChangeEvent<HTMLInputElement | HTMLSelectElement>) => {
    const { name, value, type } = e.target;
    const checked = (e.target as HTMLInputElement).checked;
    onChange(index, name as keyof StayGuestRequest, type === 'checkbox' ? checked : value);
  }, [index, onChange]);

  const handleRemove = useCallback(() => onRemove(index), [index, onRemove]);

  return (
    <M3Card className="p-6">
      <div className="flex justify-between items-center mb-6">
        <h2 className="text-xl font-display font-medium text-on-surface flex items-center">
          <MaterialIcon name="person" className="mr-2 text-primary" />
          Guest {index + 1} {guest.isPrimaryGuest && <span className="ml-2 text-xs bg-primary text-on-primary px-2 py-0.5 rounded-full">Primary</span>}
        </h2>
        {canRemove && (
          <M3Button variant="text" icon="close" onClick={handleRemove} type="button">
            Remove
          </M3Button>
        )}
      </div>

      <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
        <M3TextField
          label="First Name"
          name="firstName"
          value={guest.firstName}
          onChange={handleFieldChange}
          required
        />
        <M3TextField
          label="Last Name"
          name="lastName"
          value={guest.lastName}
          onChange={handleFieldChange}
          required
        />
        <M3TextField
          label="Gender (M/F)"
          name="gender"
          value={guest.gender}
          onChange={handleFieldChange}
          required
        />
        <M3TextField
          label="Date of Birth"
          name="dateOfBirth"
          type="date"
          value={guest.dateOfBirth}
          onChange={handleFieldChange}
          required
        />
        <M3TextField
          label="Place of Birth"
          name="placeOfBirth"
          value={guest.placeOfBirth}
          onChange={handleFieldChange}
          required
        />
        <M3TextField
          label="Citizenship (e.g., IT)"
          name="citizenship"
          value={guest.citizenship}
          onChange={handleFieldChange}
          required
        />
        <M3TextField
          label="Document Type (e.g., PASSPORT)"
          name="documentType"
          value={guest.documentType}
          onChange={handleFieldChange}
          required
        />
        <M3TextField
          label="Document Number"
          name="documentNumber"
          value={guest.documentNumber}
          onChange={handleFieldChange}
          required
        />
        <M3TextField
          label="Document Place of Issue"
          name="documentPlaceOfIssue"
          value={guest.documentPlaceOfIssue}
          onChange={handleFieldChange}
          required
        />
        <div className="relative">
          <div className="relative flex items-center rounded-shape-xs border transition-colors border-outline hover:border-on-surface">
            <select
              id={`traveller-type-${index}`}
              name="travellerType"
              value={guest.travellerType}
              onChange={handleFieldChange}
              className="peer w-full bg-transparent px-4 pt-5 pb-1.5 text-sm font-body text-on-surface focus:outline-none appearance-none"
              required
            >
              <option value="" disabled hidden></option>
              <option value="OSPITE_SINGOLO">Ospite Singolo</option>
              <option value="CAPOFAMIGLIA">Capofamiglia</option>
              <option value="CAPOGRUPPO">Capogruppo</option>
            </select>
            <label 
              htmlFor={`traveller-type-${index}`}
              className="absolute transition-all duration-150 pointer-events-none font-body left-4 top-1 text-xs text-on-surface-variant"
            >
              Tipo Alloggiato
            </label>
            <span 
              className="material-symbols-outlined absolute right-3 pointer-events-none text-on-surface-variant z-10" 
              style={ICON_SIZE_20}
            >
              arrow_drop_down
            </span>
          </div>
        </div>
        <M3TextField
          label="Motivo Soggiorno (es. Turismo)"
          name="travelPurpose"
          value={guest.travelPurpose}
          onChange={handleFieldChange}
        />
        <div className="md:col-span-2 flex items-center gap-6 mt-2">
          <label className="flex items-center gap-2 cursor-pointer">
            <input
              type="checkbox"
              name="isPrimaryGuest"
              checked={guest.isPrimaryGuest}
              onChange={handleFieldChange}
              className="w-5 h-5 text-primary rounded focus:ring-primary"
            />
            <span className="text-sm font-body text-on-surface">Primary Guest</span>
          </label>
        </div>
      </div>
    </M3Card>
  );
});

GuestFieldSection.displayName = 'GuestFieldSection';

export const CheckInForm = memo(() => {
  const { t } = useTranslation('common');
  const navigate = useNavigate();
  const { reservationId } = useParams<{ reservationId: string }>();
  const location = useLocation();
  const state = location.state as CheckInState | null;

  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  // Initialize guests based on expectedGuests, at least 1 primary guest
  const initialGuestsCount = state?.expectedGuests && state.expectedGuests > 0 ? state.expectedGuests : 1;
  const [guests, setGuests] = useState<IdentifiableGuest[]>(
    Array.from({ length: initialGuestsCount }, (_, i) => emptyGuest(i === 0))
  );

  const handleGuestChange = useCallback((index: number, field: keyof StayGuestRequest, value: string | boolean) => {
    setGuests(prev => {
      const updated = [...prev];
      updated[index] = { ...updated[index], [field]: value };
      
      // Ensure only one primary guest if we are setting this one to primary
      if (field === 'isPrimaryGuest' && value === true) {
        return updated.map((g, i) => i === index ? g : { ...g, isPrimaryGuest: false });
      }
      return updated;
    });
  }, []);

  const addGuest = useCallback(() => {
    setGuests(prev => [...prev, emptyGuest(false)]);
  }, []);

  const removeGuest = useCallback((index: number) => {
    setGuests(prev => prev.filter((_, i) => i !== index));
  }, []);

  const handleBack = useCallback(() => navigate(-1), [navigate]);

  const handleSubmit = useCallback(async (e: FormEvent) => {
    e.preventDefault();
    setError(null);

    if (!reservationId || !state?.roomId || !state?.guestId) {
      setError('Missing reservation context data (reservationId, roomId, guestId)');
      return;
    }

    // Validate at least one primary guest
    if (!guests.some(g => g.isPrimaryGuest)) {
      setError(t('error_primary_guest_required') || 'At least one primary guest is required');
      return;
    }

    try {
      setLoading(true);
      // Remove _id before sending to API
      const apiGuests: StayGuestRequest[] = guests.map((g) => ({
        firstName: g.firstName,
        lastName: g.lastName,
        gender: g.gender,
        dateOfBirth: g.dateOfBirth,
        placeOfBirth: g.placeOfBirth,
        citizenship: g.citizenship,
        documentType: g.documentType,
        documentNumber: g.documentNumber,
        documentPlaceOfIssue: g.documentPlaceOfIssue,
        isPrimaryGuest: g.isPrimaryGuest,
        travellerType: g.travellerType || undefined,
        travelPurpose: g.travelPurpose || undefined,
      }));
      
      const request: StayRequest = {
        reservationId,
        guestId: state.guestId,
        roomId: state.roomId,
        status: 'CHECKED_IN',
        guests: apiGuests,
      };

      await stayService.createStay(request);
      navigate('/stays', { replace: true });
    } catch (err: unknown) {
      const e = err as {response?: {data?: {detail?: string}}, message?: string};
      setError(e.response?.data?.detail || e.message || 'Failed to complete check-in');
    } finally {
      setLoading(false);
    }
  }, [reservationId, state, guests, navigate, t]);

  return (
    <div className="max-w-4xl mx-auto space-y-6">
      <div className="flex items-center gap-4">
        <M3Button variant="text" icon="arrow_back" onClick={handleBack}>
          {t('back')}
        </M3Button>
        <h1 className="text-2xl font-display font-bold text-on-surface">Check-in Alloggiati</h1>
      </div>

      {error && (
        <div className="bg-error-container text-on-error-container p-4 rounded-shape-sm flex items-start gap-3">
          <MaterialIcon name="error" className="mt-0.5 flex-shrink-0" />
          <p className="font-body text-sm">{error}</p>
        </div>
      )}

      <form onSubmit={handleSubmit} className="space-y-6">
        {guests.map((guest, index) => (
          <GuestFieldSection
            key={guest._id}
            guest={guest}
            index={index}
            canRemove={guests.length > 1}
            onRemove={removeGuest}
            onChange={handleGuestChange}
          />
        ))}

        <div className="flex gap-4 items-center justify-between border-t border-outline-variant pt-6">
          <M3Button variant="outlined" icon="person_add" onClick={addGuest} type="button">
            Add Guest
          </M3Button>
          <M3Button 
            variant="filled" 
            icon="how_to_reg" 
            type="submit" 
            disabled={loading}
          >
            {loading ? 'Processing...' : 'Complete Check-in'}
          </M3Button>
        </div>
      </form>
    </div>
  );
});

CheckInForm.displayName = 'CheckInForm';

CheckInForm.displayName = 'CheckInForm';
