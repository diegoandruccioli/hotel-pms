import { useTranslation } from 'react-i18next';
import { MaterialIcon } from './MaterialIcon';
import { PASSWORD_REQUIREMENTS } from '../utils/passwordPolicy';

interface PasswordRequirementsChecklistProps {
  password: string;
}

export const PasswordRequirementsChecklist = ({ password }: PasswordRequirementsChecklistProps) => {
  const { t } = useTranslation('common');

  return (
    <ul className="space-y-1 pl-1" aria-label={t('password_requirements')}>
      {PASSWORD_REQUIREMENTS.map((requirement) => {
        const met = requirement.test(password);
        return (
          <li
            key={requirement.key}
            className={`flex items-center gap-2 text-xs font-body transition-colors ${
              met ? 'text-tertiary' : 'text-on-surface-variant'
            }`}
          >
            <MaterialIcon name={met ? 'check_circle' : 'radio_button_unchecked'} size={16} />
            <span>{t(requirement.key)}</span>
          </li>
        );
      })}
    </ul>
  );
};
