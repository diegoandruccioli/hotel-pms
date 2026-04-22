import { MaterialIcon } from '../MaterialIcon';

type ButtonVariant = 'filled' | 'tonal' | 'outlined' | 'text';

interface M3ButtonProps extends React.ButtonHTMLAttributes<HTMLButtonElement> {
  variant?: ButtonVariant;
  icon?: string;
  loading?: boolean;
}

const variantClasses: Record<ButtonVariant, string> = {
  filled:
    'bg-primary text-on-primary hover:shadow-elevation-1 active:shadow-elevation-0 focus-visible:ring-2 focus-visible:ring-primary focus-visible:ring-offset-2',
  tonal:
    'bg-secondary-container text-on-secondary-container hover:shadow-elevation-1 active:shadow-elevation-0 focus-visible:ring-2 focus-visible:ring-secondary focus-visible:ring-offset-2',
  outlined:
    'border border-outline text-primary bg-transparent hover:bg-primary/[0.08] focus-visible:ring-2 focus-visible:ring-primary focus-visible:ring-offset-2',
  text: 'text-primary bg-transparent hover:bg-primary/[0.08] focus-visible:ring-2 focus-visible:ring-primary focus-visible:ring-offset-2',
};

export const M3Button = ({
  variant = 'filled',
  icon,
  loading = false,
  disabled = false,
  children,
  className = '',
  ...rest
}: M3ButtonProps) => {
  const isDisabled = disabled || loading;

  return (
    <button
      disabled={isDisabled}
      className={`inline-flex items-center justify-center gap-2 px-6 h-10 rounded-shape-full text-sm font-medium font-body transition-all
        ${variantClasses[variant]}
        ${isDisabled ? 'opacity-38 cursor-not-allowed shadow-none' : ''}
        ${className}`}
      {...rest}
    >
      {loading ? (
        <MaterialIcon name="progress_activity" size={18} className="animate-spin" />
      ) : icon ? (
        <MaterialIcon name={icon} size={18} />
      ) : null}
      {children}
    </button>
  );
};
