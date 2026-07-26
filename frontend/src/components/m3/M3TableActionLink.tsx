type ActionTone = 'primary' | 'error';

interface M3TableActionLinkProps extends React.ButtonHTMLAttributes<HTMLButtonElement> {
  tone?: ActionTone;
}

const toneClasses: Record<ActionTone, string> = {
  primary: 'text-primary hover:text-primary/80 focus-visible:ring-primary',
  error: 'text-error hover:text-error/80 focus-visible:ring-error',
};

/**
 * Compact text action used for table row actions (edit/view/delete/check-in...).
 * Centralizes the focus-visible ring so every row action gets the same
 * highly-visible indicator (BUG-7, docs/LIVE_E2E_AUDIT_2026-07.md) instead of
 * each page hand-rolling its own className, several of which omitted it.
 */
export const M3TableActionLink = ({
  tone = 'primary',
  disabled = false,
  className = '',
  children,
  ...rest
}: M3TableActionLinkProps) => {
  return (
    <button
      type="button"
      disabled={disabled}
      className={`font-medium text-sm rounded transition-colors
        focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-offset-2
        disabled:opacity-50 disabled:cursor-not-allowed
        ${toneClasses[tone]}
        ${className}`}
      {...rest}
    >
      {children}
    </button>
  );
};
