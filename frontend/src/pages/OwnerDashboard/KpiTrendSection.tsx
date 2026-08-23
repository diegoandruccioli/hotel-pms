import { useCallback, useMemo, useState } from 'react';
import { useTranslation } from 'react-i18next';
import {
  ResponsiveContainer, LineChart, Line, BarChart, Bar,
  XAxis, YAxis, CartesianGrid, Tooltip, Legend,
} from 'recharts';
import { M3Card } from '../../components/m3/M3Card';
import { M3Select } from '../../components/m3/M3Select';
import { M3Table, M3TableRow, M3TableCell } from '../../components/m3/M3Table';
import { M3LoadingState } from '../../components/m3/M3LoadingState';
import { M3ErrorState } from '../../components/m3/M3ErrorState';
import { useKpiReport } from '../../hooks/queries/useKpiReport';
import { getErrorMessage } from '../../utils/errorMessage';
import type { KpiPeriodDto, ReportGranularity } from '../../types/ownerReport.types';

interface KpiTrendSectionProps {
  startDate: string;
  endDate: string;
  formatCurrency: (amount: number) => string;
  formatDate: (dateStr?: string) => string;
}

const GRANULARITY_VALUES: ReportGranularity[] = ['DAY', 'WEEK', 'MONTH'];
const PERCENT_MULTIPLIER = 100;
const CHART_HEIGHT = 240;

const OCCUPANCY_AXIS_DOMAIN: [number, number] = [0, PERCENT_MULTIPLIER];

const ADR_COLOR = 'var(--md-primary)';
const REVPAR_COLOR = 'var(--md-secondary)';
const OCCUPANCY_COLOR = 'var(--md-error)';

interface ChartPoint {
  periodStart: string;
  adr: number;
  revpar: number;
  occupancyPercent: number;
}

export const KpiTrendSection = ({ startDate, endDate, formatCurrency, formatDate }: KpiTrendSectionProps) => {
  const { t } = useTranslation('common');
  const [granularity, setGranularity] = useState<ReportGranularity>('WEEK');
  const { data, isLoading, isError, error, refetch } = useKpiReport(startDate, endDate, granularity, true);

  const granularityOptions = useMemo(
    () => GRANULARITY_VALUES.map((value) => ({
      value,
      label: t(`label_granularity_${value.toLowerCase()}`),
    })),
    [t],
  );

  const handleGranularityChange = useCallback((e: React.ChangeEvent<HTMLSelectElement>) => {
    setGranularity(e.target.value as ReportGranularity);
  }, []);

  const chartData = useMemo<ChartPoint[]>(() => (data?.periods ?? []).map((period) => ({
    periodStart: formatDate(period.periodStart),
    adr: period.adr,
    revpar: period.revpar,
    occupancyPercent: Math.round(period.occupancyRate * PERCENT_MULTIPLIER),
  })), [data, formatDate]);

  const tableRows = useMemo(() => data?.periods ?? [], [data]);

  return (
    <M3Card variant="outlined" className="p-4 space-y-4">
      <div className="flex flex-col sm:flex-row sm:items-center sm:justify-between gap-3">
        <h2 className="text-sm font-display font-semibold text-on-surface">
          {t('stat_revenue_trend_title')}
        </h2>
        <M3Select
          label={t('label_granularity')}
          hideLabel
          options={granularityOptions}
          value={granularity}
          onChange={handleGranularityChange}
          className="w-40"
        />
      </div>

      {isLoading ? (
        <M3LoadingState label={t('loading')} />
      ) : isError ? (
        <M3ErrorState
          title={t('err_kpi_load_failed')}
          message={getErrorMessage(error, t('err_kpi_load_failed'))}
          retryLabel={t('try_again')}
          onRetry={refetch}
        />
      ) : tableRows.length === 0 ? (
        <p className="text-sm font-body text-on-surface-variant py-4 text-center">
          {t('no_kpi_data_period')}
        </p>
      ) : (
        <KpiTrendContent
          chartData={chartData}
          tableRows={tableRows}
          formatCurrency={formatCurrency}
          formatDate={formatDate}
          occupancyTitle={t('stat_occupancy_trend_title')}
          periodLabel={t('label_period')}
          adrLabel={t('adr')}
          revparLabel={t('revpar')}
          occupancyLabel={t('occupancy')}
        />
      )}
    </M3Card>
  );
};

interface KpiTrendContentProps {
  chartData: ChartPoint[];
  tableRows: KpiPeriodDto[];
  formatCurrency: (amount: number) => string;
  formatDate: (dateStr?: string) => string;
  occupancyTitle: string;
  periodLabel: string;
  adrLabel: string;
  revparLabel: string;
  occupancyLabel: string;
}

/** Split into its own component so the charts + table only re-render when
 * the fetched data actually changes, not on every granularity-select
 * re-render of the parent while loading. */
const KpiTrendContent = ({
  chartData, tableRows, formatCurrency, formatDate,
  occupancyTitle, periodLabel, adrLabel, revparLabel, occupancyLabel,
}: KpiTrendContentProps) => {
  const tableHeaders = useMemo(
    () => [periodLabel, adrLabel, revparLabel, occupancyLabel],
    [periodLabel, adrLabel, revparLabel, occupancyLabel],
  );
  const formatTooltipValue = useCallback(
    (value: number | string | readonly (number | string)[] | undefined) => formatCurrency(Number(value)),
    [formatCurrency],
  );

  return (
    <>
      <ResponsiveContainer width="100%" height={CHART_HEIGHT}>
        <LineChart data={chartData}>
          <CartesianGrid strokeDasharray="3 3" />
          <XAxis dataKey="periodStart" />
          <YAxis />
          <Tooltip formatter={formatTooltipValue} />
          <Legend />
          <Line type="monotone" dataKey="adr" name={adrLabel} stroke={ADR_COLOR} />
          <Line type="monotone" dataKey="revpar" name={revparLabel} stroke={REVPAR_COLOR} />
        </LineChart>
      </ResponsiveContainer>

      <h3 className="text-sm font-display font-semibold text-on-surface">{occupancyTitle}</h3>
      <ResponsiveContainer width="100%" height={CHART_HEIGHT}>
        <BarChart data={chartData}>
          <CartesianGrid strokeDasharray="3 3" />
          <XAxis dataKey="periodStart" />
          <YAxis unit="%" domain={OCCUPANCY_AXIS_DOMAIN} />
          <Tooltip />
          <Bar dataKey="occupancyPercent" name={occupancyLabel} fill={OCCUPANCY_COLOR} />
        </BarChart>
      </ResponsiveContainer>

      {/* Textual/tabular equivalent of the two charts above, for screen
          readers — an <svg> chart with no fallback fails a11y. */}
      <M3Table headers={tableHeaders}>
        {tableRows.map((period) => (
          <M3TableRow key={period.periodStart}>
            <M3TableCell>{formatDate(period.periodStart)}</M3TableCell>
            <M3TableCell>{formatCurrency(period.adr)}</M3TableCell>
            <M3TableCell>{formatCurrency(period.revpar)}</M3TableCell>
            <M3TableCell>{Math.round(period.occupancyRate * PERCENT_MULTIPLIER)}%</M3TableCell>
          </M3TableRow>
        ))}
      </M3Table>
    </>
  );
};
