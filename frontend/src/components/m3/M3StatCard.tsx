import { memo } from 'react';
import { Link } from 'react-router-dom';
import { cn, toneChipClasses } from '../../utils';
import type { StatusTone } from '../../utils';
import { MaterialIcon } from '../MaterialIcon';
import { M3Card } from './M3Card';

interface M3StatCardDelta {
  /** Signed change; zero counts as non-negative. */
  percent: number;
  /** Already translated and worded with its direction ("+12% vs previous period", "Down 5% …"): the icon is decorative, so the text alone must carry the sign. */
  label: string;
}

interface M3StatCardProps {
  label: string;
  /** Already formatted for the locale. */
  value: string;
  /** Material Symbols name for the tone tile. */
  icon?: string;
  tone?: StatusTone;
  delta?: M3StatCardDelta;
  /** Oldest to newest. Non-finite values are dropped; the sparkline is drawn only with two or more points left. */
  trend?: number[];
  /** Text alternative for the sparkline, which is decorative (`aria-hidden`). */
  trendLabel?: string;
  /** Makes the whole card one link. Nothing focusable may live inside the card. */
  to?: string;
  state?: Record<string, unknown>;
  className?: string;
}

const SPARK_WIDTH = 100;
const SPARK_HEIGHT = 24;
const SPARK_PAD = 2;

const round = (n: number) => Number(n.toFixed(2));

const sparklinePoints = (trend: number[]): string => {
  const min = Math.min(...trend);
  const range = Math.max(...trend) - min;
  const last = trend.length - 1;
  return trend
    .map((v, i) => {
      const y = range === 0
        ? SPARK_HEIGHT / 2
        : SPARK_PAD + (1 - (v - min) / range) * (SPARK_HEIGHT - 2 * SPARK_PAD);
      return `${round((i / last) * SPARK_WIDTH)},${round(y)}`;
    })
    .join(' ');
};

const cardBody = ({
  label, value, icon, tone = 'info', delta, trend, trendLabel,
}: M3StatCardProps) => {
  const points = trend?.filter(Number.isFinite) ?? [];
  return (
  <>
    <div className="flex items-center gap-3">
      {icon && (
        <div className={cn('flex h-12 w-12 shrink-0 items-center justify-center rounded-shape-lg', toneChipClasses[tone])}>
          <MaterialIcon name={icon} size={24} />
        </div>
      )}
      <p className="min-w-0 truncate text-sm font-body font-medium text-on-surface-variant">{label}</p>
    </div>
    <div className="mt-3 flex items-end justify-between gap-3">
      <p className="text-2xl font-display font-bold tabular-nums text-on-surface">{value}</p>
      {points.length >= 2 && (
        <>
          <svg
            aria-hidden="true"
            viewBox={`0 0 ${SPARK_WIDTH} ${SPARK_HEIGHT}`}
            preserveAspectRatio="none"
            className="h-8 w-24 shrink-0 text-primary"
          >
            <polyline
              points={sparklinePoints(points)}
              fill="none"
              stroke="currentColor"
              strokeWidth={2}
              strokeLinecap="round"
              strokeLinejoin="round"
              vectorEffect="non-scaling-stroke"
            />
          </svg>
          {trendLabel && <span className="sr-only">{trendLabel}</span>}
        </>
      )}
    </div>
    {delta && (
      <p className={cn('mt-1 flex items-center gap-0.5 text-xs font-body font-medium', delta.percent >= 0 ? 'text-tertiary' : 'text-error')}>
        <MaterialIcon name={delta.percent >= 0 ? 'trending_up' : 'trending_down'} size={14} />
        {delta.label}
      </p>
    )}
  </>
  );
};

/**
 * KPI tile: label, large figure, optional delta and inline sparkline. Pure and
 * presentational: callers translate, format and compute the delta. With `to` the
 * whole card is one link named by its content, so a screen reader hears
 * "label value", not a separate "view all".
 */
export const M3StatCard = memo((props: M3StatCardProps) => {
  const { to, state, className } = props;
  if (!to) {
    return <M3Card variant="solid" className={cn('p-5', className)}>{cardBody(props)}</M3Card>;
  }
  return (
    <Link
      to={to}
      state={state}
      className={cn(
        'block min-h-11 rounded-shape-lg focus-visible:outline-hidden focus-visible:ring-2 focus-visible:ring-primary focus-visible:ring-offset-2',
        className,
      )}
    >
      <M3Card variant="solid" className="h-full p-5 transition-colors hover:bg-surface-container-low">
        {cardBody(props)}
      </M3Card>
    </Link>
  );
});
M3StatCard.displayName = 'M3StatCard';
