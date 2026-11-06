import { memo, useCallback } from 'react';
import { useTranslation } from 'react-i18next';
import { MaterialIcon } from '../../components/MaterialIcon';
import { M3Button, M3Card, M3TextField } from '../../components/m3';
import type { GuestResponseDTO } from '../../types';

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

interface QuotationRecipientSectionProps {
  recipientMode: 'guest' | 'prospect';
  selectedGuest: GuestResponseDTO | null;
  guestQuery: string;
  guestSuggestions: GuestResponseDTO[];
  prospectFirstName: string;
  prospectLastName: string;
  prospectEmail: string;
  onGuestModeClick: () => void;
  onProspectModeClick: () => void;
  onGuestQueryChange: (e: React.ChangeEvent<HTMLInputElement>) => void;
  onSelectGuest: (guest: GuestResponseDTO) => void;
  onClearGuest: () => void;
  onProspectFirstNameChange: (e: React.ChangeEvent<HTMLInputElement>) => void;
  onProspectLastNameChange: (e: React.ChangeEvent<HTMLInputElement>) => void;
  onProspectEmailChange: (e: React.ChangeEvent<HTMLInputElement>) => void;
}

export const QuotationRecipientSection = memo(({
  recipientMode, selectedGuest, guestQuery, guestSuggestions,
  prospectFirstName, prospectLastName, prospectEmail,
  onGuestModeClick, onProspectModeClick, onGuestQueryChange, onSelectGuest, onClearGuest,
  onProspectFirstNameChange, onProspectLastNameChange, onProspectEmailChange,
}: QuotationRecipientSectionProps) => {
  const { t } = useTranslation(['quotations', 'guests', 'common']);

  return (
    <M3Card variant="solid" className="p-6 space-y-4">
      <div className="flex items-center gap-2 mb-2">
        <MaterialIcon name="person" className="text-primary" />
        <h2 className="text-lg font-medium text-on-surface">{t('step_recipient')}</h2>
      </div>

      <div className="flex gap-2" role="group" aria-label={t('heading_recipient')}>
        <M3Button
          type="button"
          variant={recipientMode === 'guest' ? 'filled' : 'outlined'}
          onClick={onGuestModeClick}
        >
          {t('toggle_existing_guest')}
        </M3Button>
        <M3Button
          type="button"
          variant={recipientMode === 'prospect' ? 'filled' : 'outlined'}
          onClick={onProspectModeClick}
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
            <M3Button variant="text" icon="edit" onClick={onClearGuest}>{t('guests:btn_change')}</M3Button>
          </div>
        ) : (
          <div className="space-y-2">
            <M3TextField
              label={t('guests:search_guest_placeholder')}
              leadingIcon="search"
              value={guestQuery}
              onChange={onGuestQueryChange}
            />
            {guestQuery && (
              <div className="border border-outline-variant rounded-shape-sm max-h-48 overflow-y-auto">
                {guestSuggestions.length > 0 ? (
                  <ul className="divide-y divide-outline-variant">
                    {guestSuggestions.map((guest) => (
                      <GuestSuggestionRow key={guest.id} guest={guest} onSelect={onSelectGuest} />
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
          <M3TextField label={t('label_prospect_first_name')} value={prospectFirstName} onChange={onProspectFirstNameChange} required />
          <M3TextField label={t('label_prospect_last_name')} value={prospectLastName} onChange={onProspectLastNameChange} required />
          <M3TextField label={t('label_prospect_email')} type="email" value={prospectEmail} onChange={onProspectEmailChange} required />
        </div>
      )}
    </M3Card>
  );
});
QuotationRecipientSection.displayName = 'QuotationRecipientSection';
