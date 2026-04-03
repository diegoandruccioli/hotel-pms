import { MaterialIcon } from '../MaterialIcon';

type ChipTone = 'success' | 'warning' | 'error' | 'info' | 'neutral';

interface M3StatusChipProps {
  label: string;
  tone?: ChipTone;
  icon?: string;
  className?: string;
}

const toneClasses: Record<ChipTone, string> = {
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
    className={`inline-flex items-center gap-1 px-2.5 py-1 rounded-shape-sm text-xs font-medium font-body ${toneClasses[tone]} ${className}`}
  >
    {icon && <MaterialIcon name={icon} size={14} />}
    {label}
  </span>
);
