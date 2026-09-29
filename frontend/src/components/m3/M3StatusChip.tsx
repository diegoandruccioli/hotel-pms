import { cn } from '../../utils';
import type { StatusTone } from '../../utils';
import { MaterialIcon } from '../MaterialIcon';

interface M3StatusChipProps {
  label: string;
  tone?: StatusTone;
  icon?: string;
  className?: string;
}

const toneClasses: Record<StatusTone, string> = {
  success: 'bg-tertiary-container text-on-tertiary-container',
  warning: 'bg-secondary-container text-on-secondary-container',
  error: 'bg-error-container text-on-error-container',
  info: 'bg-primary-container text-on-primary-container',
  neutral: 'bg-surface-container-highest text-on-surface-variant',
};

export const M3StatusChip = ({
  label,
  tone = 'neutral',
  icon,
  className = '',
}: M3StatusChipProps) => (
  <span
    className={cn(
      'inline-flex items-center gap-1 px-2.5 py-1 rounded-shape-sm text-xs font-medium font-body',
      toneClasses[tone],
      className
    )}
  >
    {icon && <MaterialIcon name={icon} size={14} />}
    {label}
  </span>
);
