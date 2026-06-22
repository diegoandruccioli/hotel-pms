import { describe, it, expect, vi } from 'vitest';
import { render, screen, fireEvent } from '@testing-library/react';
import { axe } from 'vitest-axe';
import { PasswordVisibilityToggle } from './PasswordVisibilityToggle';

vi.mock('react-i18next', () => ({
  useTranslation: () => ({ t: (key: string) => key, i18n: { language: 'en' } }),
  initReactI18next: { type: '3rdParty', init: vi.fn() },
}));

describe('PasswordVisibilityToggle', () => {
  it('shows the "show" label and icon when not visible', () => {
    render(<PasswordVisibilityToggle visible={false} onToggle={vi.fn()} />);
    expect(screen.getByLabelText('show_password')).toBeInTheDocument();
  });

  it('shows the "hide" label and icon when visible', () => {
    render(<PasswordVisibilityToggle visible onToggle={vi.fn()} />);
    expect(screen.getByLabelText('hide_password')).toBeInTheDocument();
  });

  it('calls onToggle when clicked', () => {
    const onToggle = vi.fn();
    render(<PasswordVisibilityToggle visible={false} onToggle={onToggle} />);
    fireEvent.click(screen.getByLabelText('show_password'));
    expect(onToggle).toHaveBeenCalledTimes(1);
  });

  it('should have no accessibility violations', async () => {
    const { container } = render(<PasswordVisibilityToggle visible={false} onToggle={vi.fn()} />);
    const results = await axe(container);
    expect(results).toHaveNoViolations();
  });
});
