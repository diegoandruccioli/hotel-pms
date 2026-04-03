import { useState, useEffect, useCallback, memo } from 'react';
import { MaterialIcon } from '../../components/MaterialIcon';
import { M3Button } from '../../components/m3/M3Button';
import { M3TextField } from '../../components/m3/M3TextField';
import { guestService } from '../../services/guestService';
import type { GuestResponseDTO, GuestRequestDTO } from '../../types/guest.types';

interface GuestSuggestionItemProps {
  guest: GuestResponseDTO;
  onSelect: (guest: GuestResponseDTO) => void;
}

const GuestSuggestionItem = memo(({ guest, onSelect }: GuestSuggestionItemProps) => {
  const handleClick = useCallback(() => {
    onSelect(guest);
  }, [onSelect, guest]);

  return (
    <li>
      <button 
        type="button"
        className="w-full text-left px-4 py-3 hover:bg-surface-variant transition-colors flex justify-between items-center"
        onClick={handleClick}
      >
        <div>
          <p className="font-medium text-on-surface">{guest.firstName} {guest.lastName}</p>
          <p className="text-sm text-on-surface-variant">{guest.email}</p>
        </div>
        <MaterialIcon name="chevron_right" className="text-on-surface-variant" />
      </button>
    </li>
  );
});

GuestSuggestionItem.displayName = 'GuestSuggestionItem';

interface GuestSearchAndCreateProps {
  selectedGuest: GuestResponseDTO | null;
  onSelectGuest: (guest: GuestResponseDTO) => void;
  onClearGuest: () => void;
  readOnly?: boolean;
}

export const GuestSearchAndCreate = memo(({
  selectedGuest,
  onSelectGuest,
  onClearGuest,
  readOnly = false
}: GuestSearchAndCreateProps) => {
  const [searchQuery, setSearchQuery] = useState('');
  const [suggestions, setSuggestions] = useState<GuestResponseDTO[]>([]);
  const [isCreatingGuest, setIsCreatingGuest] = useState(false);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const [newGuest, setNewGuest] = useState<GuestRequestDTO>({
    firstName: '',
    lastName: '',
    email: '',
    phone: '',
    city: '',
    country: ''
  });

  // Debounce search
  useEffect(() => {
    const delayDebounceFn = setTimeout(async () => {
      if (searchQuery.trim().length > 0) {
        try {
          const results = await guestService.searchGuests(searchQuery);
          setSuggestions(results);
        } catch (err) {
          console.error("Error searching guests", err);
        }
      } else {
        setSuggestions([]);
      }
    }, 300);

    return () => clearTimeout(delayDebounceFn);
  }, [searchQuery]);

  const handleCreateGuest = useCallback(async () => {
    try {
      setLoading(true);
      setError(null);
      const created = await guestService.createGuest(newGuest);
      onSelectGuest(created);
      setIsCreatingGuest(false);
      setSearchQuery('');
    } catch (err: unknown) {
      const e = err as {response?: {data?: {detail?: string}}, message?: string};
      setError(e.response?.data?.detail || e.message || 'Failed to create guest');
    } finally {
      setLoading(false);
    }
  }, [newGuest, onSelectGuest]);

  const handleInputChange = useCallback((e: React.ChangeEvent<HTMLInputElement>) => {
    const { name, value } = e.target;
    setNewGuest(prev => ({ ...prev, [name]: value }));
  }, []);

  const handleSearchChange = useCallback((e: React.ChangeEvent<HTMLInputElement>) => {
    setSearchQuery(e.target.value);
  }, []);

  const handleStartCreation = useCallback(() => setIsCreatingGuest(true), []);
  const handleCancelCreation = useCallback(() => setIsCreatingGuest(false), []);

  const handleSelectSuggestion = useCallback((guest: GuestResponseDTO) => {
    onSelectGuest(guest);
    setSearchQuery('');
  }, [onSelectGuest]);

  if (selectedGuest) {
    return (
      <div className="p-4 border border-primary rounded-shape-md bg-primary/5 flex justify-between items-center">
        <div>
          <p className="font-medium text-on-surface">{selectedGuest.firstName} {selectedGuest.lastName}</p>
          <p className="text-sm text-on-surface-variant">
            {selectedGuest.email} {selectedGuest.phone ? `• ${selectedGuest.phone}` : ''}
          </p>
        </div>
        {!readOnly && <M3Button variant="text" icon="edit" onClick={onClearGuest}>Change</M3Button>}
      </div>
    );
  }

  if (isCreatingGuest) {
    return (
      <div className="space-y-4 border border-outline-variant p-4 rounded-shape-md">
        <h3 className="text-sm font-medium text-on-surface-variant uppercase tracking-wider mb-2">Create New Primary Guest</h3>
        {error && <p className="text-error text-sm">{error}</p>}
        <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
          <M3TextField label="First Name" name="firstName" value={newGuest.firstName} onChange={handleInputChange} required />
          <M3TextField label="Last Name" name="lastName" value={newGuest.lastName} onChange={handleInputChange} required />
          <M3TextField label="Email" name="email" type="email" value={newGuest.email} onChange={handleInputChange} required />
          <M3TextField label="Phone" name="phone" value={newGuest.phone || ''} onChange={handleInputChange} />
          <M3TextField label="City" name="city" value={newGuest.city || ''} onChange={handleInputChange} />
          <M3TextField label="Country" name="country" value={newGuest.country || ''} onChange={handleInputChange} />
        </div>
        <div className="flex gap-2 justify-end pt-2">
          <M3Button variant="text" onClick={handleCancelCreation}>Cancel</M3Button>
          <M3Button onClick={handleCreateGuest} loading={loading}>Save Guest</M3Button>
        </div>
      </div>
    );
  }

  return (
    <div className="space-y-4">
      <div className="flex gap-2 items-center">
        <M3TextField 
          label="Search guest by name or email" 
          leadingIcon="search"
          value={searchQuery}
          onChange={handleSearchChange}
          className="flex-1"
          readOnly={readOnly}
        />
        {!readOnly && <M3Button variant="tonal" icon="person_add" onClick={handleStartCreation}>New Guest</M3Button>}
      </div>
      
      {searchQuery && (
        <div className="border border-outline-variant rounded-shape-sm max-h-48 overflow-y-auto">
          {suggestions.length > 0 ? (
            <ul className="divide-y divide-outline-variant">
              {suggestions.map(guest => (
                <GuestSuggestionItem 
                  key={guest.id}
                  guest={guest}
                  onSelect={handleSelectSuggestion}
                />
              ))}
            </ul>
          ) : (
            <p className="p-4 text-center text-sm text-on-surface-variant">No guests found. Try creating a new one.</p>
          )}
        </div>
      )}
    </div>
  );
});

GuestSearchAndCreate.displayName = 'GuestSearchAndCreate';
