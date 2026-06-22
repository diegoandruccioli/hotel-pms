import { useTranslation } from 'react-i18next';
import { MaterialIcon } from '../MaterialIcon';

interface PasswordVisibilityToggleProps {
  visible: boolean;
  onToggle: () => void;
  className?: string;
}

export const PasswordVisibilityToggle = ({ visible, onToggle, className = '' }: PasswordVisibilityToggleProps) => {
  const { t } = useTranslation('common');

  return (
    <button
      type="button"
      onClick={onToggle}
      aria-label={visible ? t('hide_password') : t('show_password')}
      className={`flex items-center justify-center w-10 h-10 rounded-shape-full text-on-surface-variant
        hover:bg-surface-container-highest focus-visible:outline-none focus-visible:ring-2
        focus-visible:ring-primary focus-visible:ring-offset-2 transition-colors ${className}`}
    >
      <MaterialIcon name={visible ? 'visibility_off' : 'visibility'} size={20} />
    </button>
  );
};
