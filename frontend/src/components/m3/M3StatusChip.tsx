import { cn, toneChipClasses } from '../../utils';
import type { StatusTone } from '../../utils';
import { MaterialIcon } from '../MaterialIcon';

interface M3StatusChipProps {
  label: string;
  tone?: StatusTone;
  icon?: string;
  className?: string;
}

export const M3StatusChip = ({
  label,
  tone = 'neutral',
  icon,
  className = '',
}: M3StatusChipProps) => (
  <span
    className={cn(
      'inline-flex items-center gap-1 px-2.5 py-1 rounded-shape-sm text-xs font-medium font-body',
      toneChipClasses[tone],
      className
    )}
  >
    {icon && <MaterialIcon name={icon} size={14} />}
    {label}
  </span>
);
