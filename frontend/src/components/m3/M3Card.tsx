import { cn } from '../../utils';

type CardVariant = 'elevated' | 'filled' | 'outlined' | 'solid';

interface M3CardProps extends React.HTMLAttributes<HTMLDivElement> {
  variant?: CardVariant;
}

const variantClasses: Record<CardVariant, string> = {
  elevated: 'bg-surface shadow-elevation-1 rounded-shape-md',
  filled: 'bg-surface-container-highest rounded-shape-md',
  outlined: 'bg-surface border border-outline-variant rounded-shape-md',
  solid:
    'bg-surface-container-lowest border border-outline-variant shadow-elevation-1 rounded-shape-lg',
};

export const M3Card = ({
  variant = 'elevated',
  className = '',
  children,
  ...rest
}: M3CardProps) => (
  <div className={cn(variantClasses[variant], className)} {...rest}>
    {children}
  </div>
);
