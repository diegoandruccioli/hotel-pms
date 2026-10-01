import type { ReactNode } from 'react';
import { useTranslation } from 'react-i18next';
import { cn } from '../utils';
import { MaterialIcon } from './MaterialIcon';

interface PageHeaderProps {
  icon?: string;
  title: string;
  subtitle?: string;
  actions?: ReactNode;
  /** Renders the round back button before the title. */
  onBack?: () => void;
  /** Rendered inside the `h1`, after the title (e.g. a status chip). */
  titleAdornment?: ReactNode;
  /** `id` of the `h1`, for `aria-labelledby`. */
  id?: string;
  titleTestId?: string;
  /** Adds the bottom divider used by form/detail pages. */
  bordered?: boolean;
  className?: string;
  actionsClassName?: string;
}

/** Single page-title block: `h1` + optional icon, subtitle, back button and
 * actions. Every page renders its title through this component. */
export const PageHeader = ({
  icon,
  title,
  subtitle,
  actions,
  onBack,
  titleAdornment,
  id,
  titleTestId,
  bordered = false,
  className,
  actionsClassName,
}: PageHeaderProps) => {
  const { t } = useTranslation('common');

  return (
    <div
      className={cn(
        'flex flex-col sm:flex-row justify-between items-start sm:items-center gap-4',
        bordered && 'border-b border-outline-variant pb-4',
        className,
      )}
    >
      <div className="flex items-center gap-4 min-w-0">
        {onBack && (
          <button
            type="button"
            onClick={onBack}
            className="p-2 rounded-full hover:bg-surface-variant transition-colors text-on-surface-variant"
            aria-label={t('back')}
          >
            <MaterialIcon name="arrow_back" />
          </button>
        )}
        <div>
          <h1
            id={id}
            data-testid={titleTestId}
            className="text-2xl font-display font-bold tracking-tight text-on-surface flex items-center gap-2"
          >
            {icon && <MaterialIcon name={icon} className="text-primary" />}
            {title}
            {titleAdornment}
          </h1>
          {subtitle && <p className="text-sm font-body text-on-surface-variant mt-1">{subtitle}</p>}
        </div>
      </div>
      {actions && <div className={cn('flex items-center gap-3', actionsClassName)}>{actions}</div>}
    </div>
  );
};
