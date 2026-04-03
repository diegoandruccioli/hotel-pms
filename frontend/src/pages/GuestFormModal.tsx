import { useState, useCallback, useMemo, memo } from 'react';
import { useTranslation } from 'react-i18next';
import { guestService } from '../services/guestService';
import type { GuestResponseDTO, GuestRequestDTO } from '../types/guest.types';
import { MaterialIcon } from '../components/MaterialIcon';
import { M3Button } from '../components/m3/M3Button';
import { useToastStore } from '../store/toastStore';
import * as FocusTrapModule from 'focus-trap-react';

const FocusTrap = FocusTrapModule.default ?? FocusTrapModule;

const COUNTRY_CODES = [
  { code: '+39', label: '🇮🇹 +39' },
  { code: '+44', label: '🇬🇧 +44' },
  { code: '+1',  label: '🇺🇸 +1' },
  { code: '+49', label: '🇩🇪 +49' },
  { code: '+33', label: '🇫🇷 +33' },
  { code: '+34', label: '🇪🇸 +34' },
  { code: '+41', label: '🇨🇭 +41' },
];

interface Props {
  guest?: GuestResponseDTO;
  onClose: () => void;
  onSaved: () => void;
}

export const GuestFormModal = memo(({ guest, onClose, onSaved }: Props) => {
  const { t } = useTranslation('common');
  const addToast = useToastStore((s) => s.addToast);
  const [loading, setLoading] = useState(false);
  const [showDeleteConfirm, setShowDeleteConfirm] = useState(false);
  
  const { initPrefix, initNumber } = useMemo(() => {
    const initialPhone = guest?.phone || '';
    let prefix = '+39';
    let number = initialPhone;

    if (initialPhone) {
      const match = COUNTRY_CODES.find(c => initialPhone.startsWith(c.code));
      if (match) {
        prefix = match.code;
        number = initialPhone.slice(match.code.length).trim();
      }
    }
    return { initPrefix: prefix, initNumber: number };
  }, [guest?.phone]);

  const [phonePrefix, setPhonePrefix] = useState(initPrefix);
  const [phoneNumber, setPhoneNumber] = useState(initNumber);

  const [formData, setFormData] = useState<GuestRequestDTO>({
    firstName: guest?.firstName || '',
    lastName: guest?.lastName || '',
    email: guest?.email || '',
    phone: '', // Will be merged on submit
    city: guest?.city || '',
    country: guest?.country || '',
  });

  const handleChange = useCallback((e: React.ChangeEvent<HTMLInputElement>) => {
    const { name, value } = e.target;
    setFormData(prev => ({ ...prev, [name]: value }));
  }, []);

  const handlePhonePrefixChange = useCallback((e: React.ChangeEvent<HTMLSelectElement>) => {
    setPhonePrefix(e.target.value);
  }, []);

  const handlePhoneNumberChange = useCallback((e: React.ChangeEvent<HTMLInputElement>) => {
    setPhoneNumber(e.target.value);
  }, []);

  const handleSubmit = useCallback(async (e: React.FormEvent) => {
    e.preventDefault();
    setLoading(true);

    if (!formData.email.trim() && !phoneNumber.trim()) {
      addToast(t('email_or_phone_required', 'È necessario inserire Email oppure Telefono'), 'error');
      setLoading(false);
      return;
    }

    const finalPhone = phoneNumber.trim() ? `${phonePrefix} ${phoneNumber.trim()}` : '';
    const submitData = { ...formData, phone: finalPhone };

    try {
      if (guest) {
        await guestService.updateGuest(guest.id, submitData);
        addToast(t('item_updated', 'Ospite aggiornato con successo'), 'success');
      } else {
        await guestService.createGuest(submitData);
        addToast(t('saving', 'Salvato con successo'), 'success');
      }
      onSaved();
    } catch (err: unknown) {
      const errorObj = err as {response?: {data?: {detail?: string}}, message?: string};
      const errorMsg = errorObj.response?.data?.detail || errorObj.message || t('error', 'Errore');
      addToast(errorMsg, 'error');
    } finally {
      setLoading(false);
    }
  }, [formData, phoneNumber, phonePrefix, guest, onSaved, addToast, t]);

  const handleDelete = useCallback(async () => {
    if (!guest) return;
    setLoading(true);
    try {
      await guestService.deleteGuest(guest.id);
      addToast(t('item_deleted', 'Eliminato con successo'), 'success');
      onSaved();
    } catch (err: unknown) {
      const errorObj = err as {response?: {data?: {detail?: string}}, message?: string};
      const errorMsg = errorObj.response?.data?.detail || errorObj.message || t('error', 'Errore');
      addToast(errorMsg, 'error');
    } finally {
      setLoading(false);
      setShowDeleteConfirm(false);
    }
  }, [guest, onSaved, addToast, t]);

  const openDeleteConfirm = useCallback(() => setShowDeleteConfirm(true), []);
  const closeDeleteConfirm = useCallback(() => setShowDeleteConfirm(false), []);

  const inputClass = "block w-full rounded-shape-xs border border-outline px-3 py-2 text-sm font-body bg-transparent text-on-surface focus:border-primary focus:ring-1 focus:ring-primary focus:outline-none";

  return (
    <FocusTrap>
      <div className="fixed inset-0 z-50 flex items-center justify-center p-4 sm:p-0" role="dialog" aria-modal="true">
        <div className="fixed inset-0 bg-scrim/40 transition-opacity" onClick={onClose} aria-hidden="true" />
        <div className="relative bg-surface rounded-shape-lg shadow-elevation-3 w-full max-w-md max-h-[90vh] flex flex-col animate-scale-in">
          
          <div className="flex items-center justify-between px-6 py-4 border-b border-outline-variant/30">
            <h3 className="text-xl font-display font-medium text-on-surface">
              {guest ? t('edit_guest', 'Modifica Ospite') : t('add_guest', 'Aggiungi Ospite')}
            </h3>
            <button
              onClick={onClose}
              type="button"
              className="w-8 h-8 flex items-center justify-center rounded-shape-full text-on-surface-variant hover:bg-surface-container-highest transition-colors"
            >
              <MaterialIcon name="close" size={20} />
            </button>
          </div>

          <div className="p-6 overflow-y-auto">
            <form id="guest-form" onSubmit={handleSubmit} className="space-y-4">
              <div className="grid grid-cols-2 gap-4">
                <div>
                  <label className="block text-sm font-medium font-body text-on-surface-variant mb-1">
                    {t('first_name', 'Nome')} *
                  </label>
                  <input required type="text" name="firstName" value={formData.firstName} onChange={handleChange} className={inputClass} />
                </div>
                <div>
                  <label className="block text-sm font-medium font-body text-on-surface-variant mb-1">
                    {t('last_name', 'Cognome')} *
                  </label>
                  <input required type="text" name="lastName" value={formData.lastName} onChange={handleChange} className={inputClass} />
                </div>
              </div>

              <div>
                <label className="block text-sm font-medium font-body text-on-surface-variant mb-1">
                  {t('email', 'Email')} * (se senza telefono)
                </label>
                <input type="email" name="email" value={formData.email} onChange={handleChange} className={inputClass} />
              </div>

              <div>
                <label className="block text-sm font-medium font-body text-on-surface-variant mb-1">
                  {t('phone', 'Telefono')} * (se senza email)
                </label>
                <div className="flex gap-2">
                  <select 
                    value={phonePrefix} 
                    onChange={handlePhonePrefixChange}
                    className={`${inputClass} w-[100px] px-2`}
                  >
                    {COUNTRY_CODES.map(c => <option key={c.code} value={c.code}>{c.label}</option>)}
                  </select>
                  <input 
                    type="text" 
                    value={phoneNumber} 
                    onChange={handlePhoneNumberChange} 
                    className={`${inputClass} flex-1`} 
                    placeholder="Es. 333 1234567"
                  />
                </div>
              </div>

              <div className="grid grid-cols-2 gap-4">
                <div>
                  <label className="block text-sm font-medium font-body text-on-surface-variant mb-1">
                    {t('city', 'Città')}
                  </label>
                  <input type="text" name="city" value={formData.city} onChange={handleChange} className={inputClass} />
                </div>
                <div>
                  <label className="block text-sm font-medium font-body text-on-surface-variant mb-1">
                    {t('country', 'Nazione')}
                  </label>
                  <input type="text" name="country" value={formData.country} onChange={handleChange} className={inputClass} />
                </div>
              </div>
            </form>
          </div>

          {showDeleteConfirm ? (
            <div className="flex flex-col sm:flex-row justify-between items-center gap-3 px-6 py-4 border-t border-error/20 bg-error-container/20 rounded-b-shape-lg">
              <span className="text-sm font-medium font-body text-error">
                {t('confirm_delete', 'Sei sicuro di voler eliminare questo ospite?')}
              </span>
              <div className="flex gap-2">
                <M3Button variant="text" onClick={closeDeleteConfirm} disabled={loading}>{t('cancel')}</M3Button>
                <M3Button onClick={handleDelete} loading={loading} disabled={loading} className="bg-error text-on-error hover:bg-error/90 border-transparent">{t('confirm', 'Conferma')}</M3Button>
              </div>
            </div>
          ) : (
            <div className="flex justify-between items-center px-6 py-4 border-t border-outline-variant/30 bg-surface-container-lowest rounded-b-shape-lg">
              <div>
                {guest && (
                  <M3Button variant="text" onClick={openDeleteConfirm} disabled={loading} className="text-error hover:bg-error-container/20">
                    {t('delete', 'Elimina')}
                  </M3Button>
                )}
              </div>
              <div className="flex gap-2">
                <M3Button variant="text" onClick={onClose} disabled={loading}>{t('cancel', 'Annulla')}</M3Button>
                <M3Button form="guest-form" type="submit" loading={loading} disabled={loading}>{t('save', 'Salva')}</M3Button>
              </div>
            </div>
          )}
        </div>
      </div>
    </FocusTrap>
  );
});

GuestFormModal.displayName = 'GuestFormModal';
