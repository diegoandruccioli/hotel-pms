import { useEffect, useMemo, useRef, type ReactNode } from 'react';
import { useTranslation } from 'react-i18next';
import { Alert } from '../../components/Alert';
import { M3LoadingState, M3Button, M3Card, M3Dialog, M3Stepper } from '../../components/m3';
import { PageHeader } from '../../components/PageHeader';
import { GuestSearchAndCreate } from './GuestSearchAndCreate';
import { StayDatesFields } from './StayDatesFields';
import { RoomGrid } from './RoomGrid';
import { ReservationSummary } from './ReservationSummary';
import {
  useReservationForm,
  STEP_DATES,
  STEP_ROOMS,
  STEP_GUEST,
  STEP_SUMMARY,
} from './useReservationForm';

export const ReservationForm = () => {
  const { t } = useTranslation('common');
  const f = useReservationForm();
  const { step, id, isEdit, isView } = f;
  const stepHeadingRef = useRef<HTMLHeadingElement>(null);
  const shownStep = useRef(step);

  // Moving between steps hands focus to the new step's title, so keyboard and screen-reader
  // users land on the new content instead of on the button they just pressed.
  useEffect(() => {
    if (shownStep.current === step) return;
    shownStep.current = step;
    stepHeadingRef.current?.focus();
  }, [step]);

  const steps = useMemo(() => [
    { id: 'dates', label: t('reservation_step_dates') },
    { id: 'rooms', label: t('reservation_step_rooms') },
    { id: 'guest', label: t('reservation_step_guest') },
    { id: 'summary', label: t('reservation_step_summary') },
  ], [t]);

  const titles = useMemo(() => {
    if (isEdit) return { title: t('edit_reservation'), subtitle: t('edit_reservation_subtitle') };
    if (isView) return { title: t('reservation_details'), subtitle: t('view_reservation_subtitle') };
    return { title: t('new_reservation'), subtitle: t('new_reservation_subtitle') };
  }, [isEdit, isView, t]);

  if (f.fetching) {
    return (
      <M3LoadingState label={t('loading')} plain />
    );
  }

  // Dates and rooms are frozen once the guest is in house: the stay is a separate record.
  const locked = isView || !!f.checkedInStayId;
  const isLastStep = step === STEP_SUMMARY;
  const saveLabel = id ? t('update_reservation') : t('confirm_reservation');

  const header = (
    <>
      <PageHeader title={titles.title} subtitle={titles.subtitle} onBack={f.goToList} bordered />

      {f.error && (
        <Alert tone="error">{f.error}</Alert>
      )}

      {f.checkedInStayId && (
        <Alert tone="info">
          <p>{t('reservation_already_checked_in_banner')}</p>
          <M3Button
            type="button"
            variant="text"
            className="mt-1 px-0"
            onClick={f.goToStay}
          >
            {t('reservation_go_to_stay')}
          </M3Button>
        </Alert>
      )}
    </>
  );

  const renderSummary = (variant: 'panel' | 'full', actions?: ReactNode) => (
    <ReservationSummary
      variant={variant}
      headingRef={variant === 'full' ? stepHeadingRef : undefined}
      checkInDate={f.checkInDate}
      checkOutDate={f.checkOutDate}
      expectedGuests={f.expectedGuests}
      rooms={f.rooms}
      selectedRoomIds={f.selectedRoomIds}
      resolvedPrices={f.resolvedPrices}
      guest={f.selectedGuest}
    >
      {actions}
    </ReservationSummary>
  );

  if (isView) {
    return (
      <div className="space-y-6 max-w-4xl mx-auto pb-10">
        {header}
        {renderSummary('full')}
        <div className="flex justify-end pt-4 gap-3">
          <M3Button type="button" variant="text" onClick={f.goToList}>{t('back')}</M3Button>
          <M3Button type="button" icon="edit" onClick={f.goToEdit}>{t('edit')}</M3Button>
        </div>
      </div>
    );
  }

  return (
    <form onSubmit={f.handleFormSubmit} noValidate className="space-y-6 max-w-6xl mx-auto pb-10">
      {header}

      <M3Stepper
        steps={steps}
        current={step}
        isSelectable={f.isStepSelectable}
        onSelect={f.goToStep}
        ariaLabel={t('stepper_aria_label')}
        progressLabel={t('stepper_progress', { current: step + 1, total: steps.length, label: steps[step].label })}
      />

      <div className="grid grid-cols-1 lg:grid-cols-[1fr_20rem] gap-6 items-start">
        {isLastStep ? renderSummary('full') : (
          <M3Card variant="solid" className="p-6 space-y-4">
            <h2
              ref={stepHeadingRef}
              tabIndex={-1}
              className="text-lg font-medium text-on-surface focus:outline-none"
            >
              {steps[step].label}
            </h2>
            {step === STEP_DATES && (
              <StayDatesFields
                checkInDate={f.checkInDate}
                checkOutDate={f.checkOutDate}
                expectedGuests={f.expectedGuests}
                onCheckInChange={f.setCheckInDate}
                onCheckOutChange={f.setCheckOutDate}
                onExpectedGuestsChange={f.setExpectedGuests}
                readOnly={locked}
              />
            )}
            {step === STEP_ROOMS && (
              <RoomGrid
                checkInDate={f.checkInDate}
                checkOutDate={f.checkOutDate}
                availableRooms={f.rooms}
                selectedRoomIds={f.selectedRoomIds}
                allReservations={f.allReservations}
                currentReservationId={id}
                resolvedPrices={f.resolvedPrices}
                onToggleRoom={f.toggleRoom}
                readOnly={locked}
              />
            )}
            {step === STEP_GUEST && (
              <GuestSearchAndCreate
                selectedGuest={f.selectedGuest}
                onSelectGuest={f.setSelectedGuest}
                onClearGuest={f.clearGuest}
                required
              />
            )}
          </M3Card>
        )}

        {!isLastStep && renderSummary('panel', isEdit && (
          <M3Button type="button" className="w-full" loading={f.loading} onClick={f.save}>
            {saveLabel}
          </M3Button>
        ))}
      </div>

      <div className="flex justify-between pt-4 gap-3">
        <M3Button type="button" variant="text" onClick={f.goToList}>{t('cancel')}</M3Button>
        <div className="flex gap-3">
          {step > STEP_DATES && (
            <M3Button type="button" variant="outlined" icon="arrow_back" onClick={f.goPrevious}>
              {t('btn_previous')}
            </M3Button>
          )}
          {isLastStep ? (
            <M3Button key="confirm" type="submit" loading={f.loading}>{saveLabel}</M3Button>
          ) : (
            <M3Button key="next" type="submit">{t('btn_next')}</M3Button>
          )}
        </div>
      </div>

      {f.staleConflict && (
        <M3Dialog
          open
          title={t('reservation_stale_version_title')}
          titleId="reservation-stale-version-dialog"
          onClose={f.handleCancelAfterConflict}
        >
          <p className="text-sm font-body text-on-surface">{t('reservation_stale_version_body')}</p>
          <div className="flex justify-end gap-3 pt-4">
            <M3Button type="button" variant="outlined" onClick={f.handleCancelAfterConflict}>
              {t('cancel')}
            </M3Button>
            <M3Button type="button" onClick={f.handleReloadAfterConflict}>
              {t('refresh')}
            </M3Button>
          </div>
        </M3Dialog>
      )}
    </form>
  );
};
