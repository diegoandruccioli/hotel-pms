import { useState, useCallback, useMemo, memo } from 'react';
import { useTranslation } from 'react-i18next';
import { z } from 'zod';
import { guestService } from '../services';
import type { GuestResponseDTO, GuestRequestDTO } from '../types';
import { MaterialIcon } from '../components/MaterialIcon';
import { M3Button } from '../components/m3';
import { M3Dialog } from '../components/m3';
import { M3TextField } from '../components/m3';
import { M3Select } from '../components/m3';
import { StructuredAddressFields } from '../components/StructuredAddressFields';
import { useToastStore } from '../store';

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
  const { t } = useTranslation(['guests', 'common']);
  const addToast = useToastStore((s) => s.addToast);
  const [loading, setLoading] = useState(false);
  const [showDeleteConfirm, setShowDeleteConfirm] = useState(false);
  const [fieldErrors, setFieldErrors] = useState<Record<string, string>>({});
  const [showFiscalSection, setShowFiscalSection] = useState(
    !!(guest?.fiscalCode || guest?.vatNumber || guest?.companyName || guest?.sdiCode || guest?.pecEmail)
  );

  const guestSchema = useMemo(() => z.object({
    firstName: z.string().trim().min(1, t('common:err_required')),
    lastName: z.string().trim().min(1, t('common:err_required')),
    email: z.union([z.string().trim().email(t('common:err_invalid_email')), z.literal('')]),
    phoneNumber: z.string().trim(),
    city: z.string(),
    country: z.string(),
  }).refine((data) => !!data.email || !!data.phoneNumber, {
    message: t('err_email_or_phone'),
    path: ['email'],
  }), [t]);

  const { initPrefix, initNumber } = useMemo(() => {
    const initialPhone = guest?.phone || '';
    let prefix = '+39';
    let number = initialPhone;

    if (initialPhone) {
      const match = COUNTRY_CODES.find(c => initialPhone.startsWith(c.code));
      if (match) {
        prefix = match.code;
        const raw = initialPhone.slice(match.code.length).trim().replace(/\D/g, '');
        if (raw.length <= 3) number = raw;
        else if (raw.length <= 6) number = `${raw.slice(0, 3)} ${raw.slice(3)}`;
        else number = `${raw.slice(0, 3)} ${raw.slice(3, 6)} ${raw.slice(6)}`;
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
    fiscalCode: guest?.fiscalCode || '',
    vatNumber: guest?.vatNumber || '',
    companyName: guest?.companyName || '',
    sdiCode: guest?.sdiCode || '',
    pecEmail: guest?.pecEmail || '',
    cap: guest?.cap || '',
    comune: guest?.comune || '',
    provincia: guest?.provincia || '',
  });

  const handleChange = useCallback((e: React.ChangeEvent<HTMLInputElement>) => {
    const { name, value } = e.target;
    setFormData(prev => ({ ...prev, [name]: value }));
  }, []);

  const handleCapChange = useCallback(
    (value: string) => setFormData((prev) => ({ ...prev, cap: value })),
    [],
  );
  const handleComuneChange = useCallback(
    (value: string) => setFormData((prev) => ({ ...prev, comune: value })),
    [],
  );
  const handleProvinciaChange = useCallback(
    (value: string) => setFormData((prev) => ({ ...prev, provincia: value })),
    [],
  );

  const handlePhonePrefixChange = useCallback((e: React.ChangeEvent<HTMLSelectElement>) => {
    setPhonePrefix(e.target.value);
  }, []);

  const formatPhoneDisplay = useCallback((raw: string): string => {
    const d = raw.replace(/\D/g, '');
    if (d.length <= 3) return d;
    if (d.length <= 6) return `${d.slice(0, 3)} ${d.slice(3)}`;
    return `${d.slice(0, 3)} ${d.slice(3, 6)} ${d.slice(6)}`;
  }, []);

  const handlePhoneNumberChange = useCallback((e: React.ChangeEvent<HTMLInputElement>) => {
    setPhoneNumber(formatPhoneDisplay(e.target.value));
  }, [formatPhoneDisplay]);

  const handleSubmit = useCallback(async (e: React.FormEvent) => {
    e.preventDefault();
    setFieldErrors({});

    const result = guestSchema.safeParse({
      firstName: formData.firstName,
      lastName: formData.lastName,
      email: formData.email ?? '',
      phoneNumber: phoneNumber,
      city: formData.city ?? '',
      country: formData.country ?? '',
    });

    if (!result.success) {
      const errors: Record<string, string> = {};
      for (const issue of result.error.issues) {
        const field = issue.path[0];
        if (typeof field === 'string' && !errors[field]) {
          errors[field] = issue.message;
        }
      }
      setFieldErrors(errors);
      return;
    }

    setLoading(true);

    const finalPhone = result.data.phoneNumber ? `${phonePrefix} ${result.data.phoneNumber}` : undefined;
    const finalEmail = result.data.email || undefined;
    const submitData = { ...formData, email: finalEmail, phone: finalPhone };

    try {
      if (guest) {
        await guestService.updateGuest(guest.id, submitData);
        addToast(t('toast_guest_updated'), 'success');
      } else {
        await guestService.createGuest(submitData);
        addToast(t('toast_guest_saved'), 'success');
      }
      onSaved();
    } catch (err: unknown) {
      const errorObj = err as {response?: {data?: {detail?: string}}, message?: string};
      const errorMsg = errorObj.response?.data?.detail || errorObj.message || t('toast_error');
      addToast(errorMsg, 'error');
    } finally {
      setLoading(false);
    }
  }, [formData, phoneNumber, phonePrefix, guest, guestSchema, onSaved, addToast, t]);

  const handleDelete = useCallback(async () => {
    if (!guest) return;
    setLoading(true);
    try {
      await guestService.deleteGuest(guest.id);
      addToast(t('toast_guest_deleted'), 'success');
      onSaved();
    } catch (err: unknown) {
      const errorObj = err as {response?: {data?: {detail?: string}}, message?: string};
      const errorMsg = errorObj.response?.data?.detail || errorObj.message || t('toast_error');
      addToast(errorMsg, 'error');
    } finally {
      setLoading(false);
      setShowDeleteConfirm(false);
    }
  }, [guest, onSaved, addToast, t]);

  const openDeleteConfirm = useCallback(() => setShowDeleteConfirm(true), []);
  const closeDeleteConfirm = useCallback(() => setShowDeleteConfirm(false), []);
  const toggleFiscalSection = useCallback(() => setShowFiscalSection(prev => !prev), []);

  const countryCodeOptions = useMemo(
    () => COUNTRY_CODES.map((c) => ({ value: c.code, label: c.label })),
    [],
  );

  const footer = showDeleteConfirm ? (
    <div className="flex flex-col sm:flex-row justify-between items-center gap-3">
      <span className="text-sm font-medium font-body text-error">
        {t('confirm_delete_guest')}
      </span>
      <div className="flex gap-2">
        <M3Button variant="text" onClick={closeDeleteConfirm} disabled={loading}>{t('cancel')}</M3Button>
        <M3Button onClick={handleDelete} loading={loading} disabled={loading} className="bg-error text-on-error hover:bg-error/90 border-transparent">{t('btn_confirm')}</M3Button>
      </div>
    </div>
  ) : (
    <div className="flex justify-between items-center">
      <div>
        {guest && (
          <M3Button variant="text" onClick={openDeleteConfirm} disabled={loading} className="text-error hover:bg-error-container/20">
            {t('delete')}
          </M3Button>
        )}
      </div>
      <div className="flex gap-2">
        <M3Button variant="text" onClick={onClose} disabled={loading}>{t('cancel')}</M3Button>
        <M3Button form="guest-form" type="submit" loading={loading} disabled={loading}>{t('save')}</M3Button>
      </div>
    </div>
  );

  return (
    <M3Dialog
      open
      title={guest ? t('edit_guest') : t('add_guest')}
      titleId="guest-modal-title"
      onClose={onClose}
      footer={footer}
    >
            <form id="guest-form" onSubmit={handleSubmit} noValidate className="space-y-4">
              <div className="grid grid-cols-2 gap-4">
                <M3TextField
                  label={t('label_first_name')}
                  required
                  name="firstName"
                  value={formData.firstName}
                  onChange={handleChange}
                  errorText={fieldErrors.firstName}
                />
                <M3TextField
                  label={t('label_last_name')}
                  required
                  name="lastName"
                  value={formData.lastName}
                  onChange={handleChange}
                  errorText={fieldErrors.lastName}
                />
              </div>

              <M3TextField
                label={t('label_email_hint')}
                required
                type="email"
                name="email"
                value={formData.email}
                onChange={handleChange}
                errorText={fieldErrors.email}
              />

              <div>
                <p className="block text-sm font-medium font-body text-on-surface-variant mb-1">
                  {t('label_phone_hint')} *
                </p>
                <div className="flex gap-2">
                  <M3Select
                    label={t('label_phone_hint')}
                    hideLabel
                    className="shrink-0 w-24"
                    options={countryCodeOptions}
                    value={phonePrefix}
                    onChange={handlePhonePrefixChange}
                  />
                  <M3TextField
                    label={t('label_phone_number')}
                    hideLabel
                    className="flex-1"
                    value={phoneNumber}
                    onChange={handlePhoneNumberChange}
                    placeholder="Es. 333 1234567"
                  />
                </div>
              </div>

              <div className="grid grid-cols-2 gap-4">
                <M3TextField label={t('city')} name="city" value={formData.city} onChange={handleChange} />
                <M3TextField label={t('label_country')} name="country" value={formData.country} onChange={handleChange} />
              </div>

              <div className="border border-outline-variant/40 rounded-shape-xs">
                <button
                  type="button"
                  onClick={toggleFiscalSection}
                  aria-expanded={showFiscalSection}
                  aria-controls="fiscal-section"
                  className="w-full flex items-center justify-between px-3 py-2 text-sm font-medium font-body text-on-surface-variant hover:bg-surface-container-highest/50 rounded-shape-xs transition-colors focus-visible:ring-2 focus-visible:ring-primary focus-visible:ring-offset-1 focus-visible:outline-hidden"
                >
                  <span>{t('section_fiscal_data')}</span>
                  <MaterialIcon name={showFiscalSection ? 'expand_less' : 'expand_more'} size={18} />
                </button>
                {showFiscalSection && (
                  <div id="fiscal-section" className="px-3 pb-3 pt-1 space-y-3">
                    <div className="grid grid-cols-2 gap-3">
                      <M3TextField label={t('label_fiscal_code')} name="fiscalCode" value={formData.fiscalCode ?? ''} onChange={handleChange} />
                      <M3TextField label={t('label_vat_number')} name="vatNumber" value={formData.vatNumber ?? ''} onChange={handleChange} />
                    </div>
                    <M3TextField label={t('label_company_name')} name="companyName" value={formData.companyName ?? ''} onChange={handleChange} />
                    <div className="grid grid-cols-2 gap-3">
                      <M3TextField label={t('label_sdi_code')} name="sdiCode" value={formData.sdiCode ?? ''} onChange={handleChange} />
                      <M3TextField label={t('label_pec_email')} type="email" name="pecEmail" value={formData.pecEmail ?? ''} onChange={handleChange} />
                    </div>
                    <StructuredAddressFields
                      idPrefix="guest"
                      cap={formData.cap ?? ''}
                      comune={formData.comune ?? ''}
                      provincia={formData.provincia ?? ''}
                      onCapChange={handleCapChange}
                      onComuneChange={handleComuneChange}
                      onProvinciaChange={handleProvinciaChange}
                    />
                  </div>
                )}
              </div>
            </form>
    </M3Dialog>
  );
});

GuestFormModal.displayName = 'GuestFormModal';
