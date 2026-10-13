import { useFormatters } from '../../hooks';
import { useState, useCallback, useMemo } from 'react';
import { useNavigate } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import { z } from 'zod';
import { MaterialIcon } from '../../components/MaterialIcon';
import { Alert } from '../../components/Alert';
import { M3LoadingState } from '../../components/m3';
import { PageHeader } from '../../components/PageHeader';
import { M3Button } from '../../components/m3';
import { M3Card } from '../../components/m3';
import { M3TextField } from '../../components/m3';
import { quotationService } from '../../services';
import type { QuotationOptionRequest, QuotationRequest } from '../../types';
import { RoomSelection } from '../Reservations/RoomSelection';
import { getErrorMessage } from '../../utils';
import { useToastStore } from '../../store';
import { MAX_OPTIONS, defaultOptionLabel } from './quotationDraft';
import { QuotationOptionTab } from './QuotationOptionTab';
import { QuotationRecipientSection } from './QuotationRecipientSection';
import { useQuotationDraft } from './useQuotationDraft';

export const QuotationForm = () => {
  const { formatCurrency } = useFormatters();
  const { t } = useTranslation(['quotations', 'guests', 'common']);
  const navigate = useNavigate();
  const addToast = useToastStore((s) => s.addToast);
  const draft = useQuotationDraft();
  const {
    id, isEditMode, recipientMode, selectedGuest, prospectFirstName, prospectLastName, prospectEmail,
    checkInDate, checkOutDate, expectedGuests, validUntil, options, activeOptionIndex, activeOption,
  } = draft;

  const [loading, setLoading] = useState(false);
  const [submitError, setSubmitError] = useState<string | null>(null);
  const error = submitError ?? draft.loadError;

  const quotationSchema = useMemo(() => z.object({
    checkInDate: z.string().min(1, t('common:msg_valid_dates')),
    checkOutDate: z.string().min(1, t('common:msg_valid_dates')),
    validUntil: z.string().min(1, t('common:msg_valid_dates')),
    expectedGuests: z.coerce.number().int().positive(t('common:err_must_be_positive')),
  }).refine(
    (data) => new Date(data.checkOutDate).getTime() > new Date(data.checkInDate).getTime(),
    { message: t('common:msg_valid_dates'), path: ['checkOutDate'] },
  ), [t]);

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
      setSubmitError(t('err_select_recipient'));
      return;
    }
    if (options.some((option) => option.selectedRoomIds.length === 0)) {
      setSubmitError(t('err_select_room'));
      return;
    }

    const parsed = quotationSchema.safeParse({ checkInDate, checkOutDate, validUntil, expectedGuests });
    if (!parsed.success) {
      setSubmitError(parsed.error.issues[0]?.message ?? t('common:msg_valid_dates'));
      return;
    }

    try {
      setLoading(true);
      setSubmitError(null);

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
      setSubmitError(getErrorMessage(err, isEditMode ? t('toast_updated') : t('toast_created')));
    } finally {
      setLoading(false);
    }
  }, [recipientMode, selectedGuest, prospectFirstName, prospectLastName, prospectEmail,
      options, checkInDate, checkOutDate, validUntil, expectedGuests,
      quotationSchema, addToast, t, navigate, isEditMode, id]);

  if (draft.fetching) {
    return (
      <M3LoadingState label={t('common:loading')} plain />
    );
  }

  return (
    <form onSubmit={handleSubmit} noValidate className="space-y-6 max-w-4xl mx-auto pb-10">
      <PageHeader title={isEditMode ? t('edit_quotation') : t('new_quotation')} onBack={handleBack} bordered />

      {error && (
        <Alert tone="error">{error}</Alert>
      )}

      <QuotationRecipientSection
        recipientMode={recipientMode}
        selectedGuest={selectedGuest}
        guestQuery={draft.guestQuery}
        guestSuggestions={draft.guestSuggestions}
        prospectFirstName={prospectFirstName}
        prospectLastName={prospectLastName}
        prospectEmail={prospectEmail}
        onGuestModeClick={draft.handleGuestModeClick}
        onProspectModeClick={draft.handleProspectModeClick}
        onGuestQueryChange={draft.handleGuestQueryChange}
        onSelectGuest={draft.handleSelectGuest}
        onClearGuest={draft.handleClearGuest}
        onProspectFirstNameChange={draft.handleProspectFirstNameChange}
        onProspectLastNameChange={draft.handleProspectLastNameChange}
        onProspectEmailChange={draft.handleProspectEmailChange}
      />

      <M3Card variant="solid" className="p-6 space-y-4">
        <div className="flex items-center justify-between gap-2 mb-2">
          <div className="flex items-center gap-2">
            <MaterialIcon name="event_seat" className="text-primary" />
            <h2 className="text-lg font-medium text-on-surface">{t('step_stay_details')}</h2>
          </div>
        </div>

        <div className="flex flex-wrap items-center gap-2" role="group" aria-label={t('label_options')}>
          {options.map((option, index) => (
            <QuotationOptionTab
              key={index}
              option={option}
              index={index}
              isActive={index === activeOptionIndex}
              canRemove={options.length > 1}
              total={draft.optionTotal(option)}
              onSelect={draft.handleSelectOptionTab}
              onRemove={draft.handleRemoveOption}
            />
          ))}
          {options.length < MAX_OPTIONS && (
            <M3Button type="button" variant="text" icon="add" onClick={draft.handleAddOption}>
              {t('action_add_option')}
            </M3Button>
          )}
        </div>

        <M3TextField
          label={t('label_option_name')}
          value={activeOption.label}
          onChange={draft.handleActiveOptionLabelChange}
          className="max-w-xs"
        />

        <RoomSelection
          checkInDate={checkInDate}
          checkOutDate={checkOutDate}
          expectedGuests={expectedGuests}
          availableRooms={draft.rooms}
          selectedRoomIds={activeOption.selectedRoomIds}
          allReservations={draft.allReservations}
          resolvedPrices={draft.resolvedPrices}
          onCheckInChange={draft.setCheckInDate}
          onCheckOutChange={draft.setCheckOutDate}
          onExpectedGuestsChange={draft.setExpectedGuests}
          onToggleRoom={draft.toggleRoomSelection}
        />
        <M3TextField
          label={t('label_valid_until')}
          type="date"
          value={validUntil}
          onChange={draft.handleValidUntilChange}
          required
          className="max-w-xs"
        />
        {activeOption.selectedRoomIds.length > 0 && (
          <p className="text-sm font-medium text-on-surface">
            {t('quotation_total', { amount: formatCurrency(draft.optionTotal(activeOption)) })}
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
