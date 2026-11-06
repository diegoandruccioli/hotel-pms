import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { useTranslation } from 'react-i18next';
import { MaterialIcon } from '../../components/MaterialIcon';
import { M3Button, M3Card, M3ConfirmDialog, M3Stepper, M3TextField } from '../../components/m3';
import { useFormatters } from '../../hooks';
import { useRunNightAudit } from '../../hooks/queries';
import { useToastStore } from '../../store';
import { addDaysIso, getErrorMessage, todayIsoDate } from '../../utils';
import { NightAuditPreCheck } from './NightAuditPreCheck';
import { NoShowCandidatesList } from './NoShowCandidatesList';
import { useNoShowCandidates } from './useNoShowCandidates';

const STEP_NO_SHOWS = 0;
const STEP_CONFIRM = 1;

/**
 * "Close a business day" card as a two-step procedure: resolve the arrivals that never
 * showed up, then review and run the closing. Step 2 is only reachable via "Continue", and
 * changing the date goes back to step 1 since the candidates depend on it.
 */
export const NightAuditRunWizard = () => {
  const { t } = useTranslation('common');
  const addToast = useToastStore((s) => s.addToast);
  const { formatDate } = useFormatters();

  const [step, setStep] = useState(STEP_NO_SHOWS);
  const [runDate, setRunDate] = useState(() => addDaysIso(todayIsoDate(), -1));
  const [confirmingRun, setConfirmingRun] = useState(false);

  const runMutation = useRunNightAudit();
  const { total: noShowCount, isLoading: countLoading, error: countError } = useNoShowCandidates(runDate);
  const stepHeadingRef = useRef<HTMLHeadingElement>(null);
  const previousStep = useRef(step);
  const countKnown = !countLoading && !countError;

  const steps = useMemo(
    () => [
      { id: 'no-shows', label: t('night_audit_step_noshows') },
      { id: 'confirm', label: t('night_audit_step_confirm') },
    ],
    [t],
  );

  // Continue/Back unmount the focused button; hand focus to the new step's heading.
  useEffect(() => {
    if (previousStep.current !== step) stepHeadingRef.current?.focus();
    previousStep.current = step;
  }, [step]);

  const handleRunDateChange = useCallback((e: React.ChangeEvent<HTMLInputElement>) => {
    setRunDate(e.target.value);
    setStep(STEP_NO_SHOWS);
  }, []);
  const isStepSelectable = useCallback((index: number) => index === STEP_NO_SHOWS && step === STEP_CONFIRM, [step]);
  const handleSelectStep = useCallback((index: number) => setStep(index), []);
  const handleContinue = useCallback(() => setStep(STEP_CONFIRM), []);
  const handleBack = useCallback(() => setStep(STEP_NO_SHOWS), []);
  const handleRunRequest = useCallback(() => setConfirmingRun(true), []);
  const handleRunDialogClose = useCallback(() => setConfirmingRun(false), []);

  const handleRunConfirm = useCallback(async () => {
    try {
      const result = await runMutation.mutateAsync(runDate);
      if (result.status === 'COMPLETED') {
        addToast(t('night_audit_run_success', { date: formatDate(result.businessDate) }), 'success');
        setStep(STEP_NO_SHOWS);
      } else {
        addToast(t('night_audit_run_failed_detail', { reason: result.failureReason ?? '' }), 'error');
      }
    } catch (err: unknown) {
      addToast(getErrorMessage(err, t('night_audit_run_failed')), 'error');
    } finally {
      setConfirmingRun(false);
    }
  }, [runDate, runMutation, addToast, t, formatDate]);

  return (
    <M3Card variant="solid" role="region" aria-labelledby="night-audit-run-card-title" className="max-w-5xl space-y-4 p-5">
      <div className="flex items-center gap-2">
        <MaterialIcon name="play_circle" size={20} className="text-primary" />
        <h2 id="night-audit-run-card-title" className="text-sm font-display font-semibold text-on-surface">{t('night_audit_run_card_title')}</h2>
      </div>
      <p className="text-xs font-body text-on-surface-variant">{t('night_audit_run_card_desc')}</p>

      <M3Stepper
        steps={steps}
        current={step}
        isSelectable={isStepSelectable}
        onSelect={handleSelectStep}
        ariaLabel={t('night_audit_steps_aria_label')}
        progressLabel={t('stepper_progress', { current: step + 1, total: steps.length, label: steps[step].label })}
      />

      <h3 ref={stepHeadingRef} tabIndex={-1} className="text-sm font-display font-semibold text-on-surface outline-none">
        {steps[step].label}
      </h3>

      {step === STEP_NO_SHOWS ? (
        <div className="space-y-4">
          <M3TextField
            className="w-full sm:max-w-xs"
            label={t('night_audit_business_date')}
            type="date"
            value={runDate}
            onChange={handleRunDateChange}
            max={todayIsoDate()}
          />
          <p className="text-sm font-body text-on-surface-variant">{t('night_audit_noshows_desc')}</p>
          <NoShowCandidatesList businessDate={runDate} />
          <div className="flex justify-end">
            <M3Button icon="arrow_forward" onClick={handleContinue} disabled={runDate === ''}>{t('night_audit_continue')}</M3Button>
          </div>
        </div>
      ) : (
        <div className="space-y-4">
          <p className="text-sm font-body text-on-surface">
            {countKnown
              ? t('night_audit_confirm_summary', { count: noShowCount, date: formatDate(runDate) })
              : t('night_audit_confirm_summary_unknown', { date: formatDate(runDate) })}
          </p>
          <NightAuditPreCheck businessDate={runDate} />
          <div className="flex flex-wrap justify-between gap-3">
            <M3Button variant="outlined" icon="arrow_back" onClick={handleBack}>{t('back')}</M3Button>
            <M3Button icon="play_arrow" onClick={handleRunRequest} loading={runMutation.isPending} disabled={runDate === ''}>
              {t('night_audit_run_action')}
            </M3Button>
          </div>
        </div>
      )}

      {confirmingRun && (
        <M3ConfirmDialog
          title={t('night_audit_run_action')}
          titleId="confirm-night-audit-run-dialog"
          message={t('night_audit_run_confirm', { date: formatDate(runDate) })}
          onConfirm={handleRunConfirm}
          onCancel={handleRunDialogClose}
          loading={runMutation.isPending}
        />
      )}
    </M3Card>
  );
};
