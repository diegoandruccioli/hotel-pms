import { useState, useCallback, memo } from 'react';
import { useTranslation } from 'react-i18next';
import { stayService } from '../../services/stayService';
import { useToastStore } from '../../store/toastStore';
import { M3Card } from '../../components/m3/M3Card';
import { M3Button } from '../../components/m3/M3Button';
import { MaterialIcon } from '../../components/MaterialIcon';

const getTodayString = () => {
  const now = new Date();
  return `${now.getFullYear()}-${String(now.getMonth() + 1).padStart(2, '0')}-${String(now.getDate()).padStart(2, '0')}`;
};

interface AlloggiatiReportSectionProps {
  isAdminOrOwner: boolean;
}

export const AlloggiatiReportSection = memo(({ isAdminOrOwner }: AlloggiatiReportSectionProps) => {
  const { t } = useTranslation('common');
  const addToast = useToastStore((s) => s.addToast);
  const [alloggiatiDate, setAlloggiatiDate] = useState(getTodayString());
  const [downloadingReport, setDownloadingReport] = useState(false);
  const [downloadingJson, setDownloadingJson] = useState(false);
  const [submitting, setSubmitting] = useState(false);

  const handleAlloggiatiDateChange = useCallback((e: React.ChangeEvent<HTMLInputElement>) => {
    setAlloggiatiDate(e.target.value);
  }, []);

  const handleAlloggiatiDownload = useCallback(async () => {
    setDownloadingReport(true);
    try {
      await stayService.downloadAlloggiatiReport(alloggiatiDate);
      addToast(t('alloggiati_report_downloaded', { date: alloggiatiDate }), 'success');
    } catch (err: unknown) {
      const message = err instanceof Error ? err.message : t('failed_generate_report');
      addToast(message, 'error');
    } finally {
      setDownloadingReport(false);
    }
  }, [alloggiatiDate, addToast, t]);

  const handleAlloggiatiJsonDownload = useCallback(async () => {
    setDownloadingJson(true);
    try {
      await stayService.downloadAlloggiatiJson(alloggiatiDate);
      addToast(t('alloggiati_json_downloaded', { date: alloggiatiDate }), 'success');
    } catch (err: unknown) {
      const message = err instanceof Error ? err.message : t('failed_generate_report');
      addToast(message, 'error');
    } finally {
      setDownloadingJson(false);
    }
  }, [alloggiatiDate, addToast, t]);

  const handleAlloggiatiSubmit = useCallback(async () => {
    const confirmed = window.confirm(t('alloggiati_submit_confirm', { date: alloggiatiDate }));
    if (!confirmed) return;
    setSubmitting(true);
    try {
      await stayService.submitAlloggiatiReport(alloggiatiDate);
      addToast(t('alloggiati_submit_success'), 'success');
    } catch (err: unknown) {
      const message = err instanceof Error ? err.message : '';
      addToast(message ? t('alloggiati_submit_error', { message }) : t('alloggiati_submit_failed'), 'error');
    } finally {
      setSubmitting(false);
    }
  }, [alloggiatiDate, addToast, t]);

  return (
    <M3Card variant="outlined" className="p-5">
      <div className="flex items-center gap-2 mb-3">
        <MaterialIcon name="verified_user" size={20} className="text-primary" />
        <h2 className="text-sm font-display font-semibold text-on-surface">{t('police_report_title')}</h2>
      </div>
      <p className="text-xs font-body text-on-surface-variant mb-4">{t('police_report_desc')}</p>
      <div className="flex flex-col sm:flex-row items-end gap-3">
        <div className="flex-1">
          <label htmlFor="alloggiati-date" className="block text-sm font-medium font-body text-on-surface-variant mb-1">
            {t('check_in_date')}
          </label>
          <input
            id="alloggiati-date"
            type="date"
            value={alloggiatiDate}
            onChange={handleAlloggiatiDateChange}
            className="block w-full rounded-shape-xs border border-outline px-3 py-2 text-sm font-body bg-transparent text-on-surface focus:border-primary focus:ring-1 focus:ring-primary focus:outline-none"
          />
        </div>
        <M3Button
          id="generate-alloggiati-btn"
          icon={downloadingReport ? 'progress_activity' : 'download'}
          loading={downloadingReport}
          disabled={downloadingReport}
          onClick={handleAlloggiatiDownload}
        >
          {t('generate_and_download')}
        </M3Button>
        {isAdminOrOwner && (
          <M3Button
            id="download-alloggiati-json-btn"
            variant="outlined"
            icon={downloadingJson ? 'progress_activity' : 'data_object'}
            loading={downloadingJson}
            disabled={downloadingJson}
            onClick={handleAlloggiatiJsonDownload}
          >
            {t('download_json_export')}
          </M3Button>
        )}
        {isAdminOrOwner && (
          <M3Button
            id="submit-alloggiati-btn"
            variant="tonal"
            icon={submitting ? 'progress_activity' : 'send'}
            loading={submitting}
            disabled={submitting}
            onClick={handleAlloggiatiSubmit}
          >
            {t('alloggiati_submit')}
          </M3Button>
        )}
      </div>
    </M3Card>
  );
});

AlloggiatiReportSection.displayName = 'AlloggiatiReportSection';
