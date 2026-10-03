import type { HTMLAttributes } from 'react';
import { cn } from '../../utils';

type M3AvatarSize = 'md' | 'lg';

export interface M3AvatarProps extends Omit<HTMLAttributes<HTMLSpanElement>, 'children'> {
  name?: string | null;
  size: M3AvatarSize;
}

const sizeClasses: Record<M3AvatarSize, string> = {
  md: 'w-10 h-10 text-sm',
  lg: 'w-16 h-16 text-2xl',
};

/**
 * M3 avatar: a filled circle with the uppercase initial of a name ("?" when missing).
 */
export const M3Avatar = ({ name, size, className, ...rest }: M3AvatarProps) => (
  <span
    {...rest}
    className={cn(
      'flex items-center justify-center rounded-shape-full bg-primary text-on-primary font-display font-bold',
      sizeClasses[size],
      className
    )}
  >
    {name?.charAt(0).toUpperCase() || '?'}
  </span>
);
