import { describe, it, expect, vi } from 'vitest';
import { render, screen, fireEvent } from '@testing-library/react';
import { axe } from 'vitest-axe';
import { ThemeToggle } from './ThemeToggle';

vi.mock('react-i18next', () => ({
  useTranslation: () => ({ t: (key: string) => key, i18n: { language: 'en' } }),
  initReactI18next: { type: '3rdParty', init: vi.fn() },
}));

vi.mock('../store/themeStore', () => ({
  useThemeStore: () => ({
    theme: 'light',
    setTheme: vi.fn(),
  }),
}));

describe('ThemeToggle', () => {
  it('should render the toggle button', () => {
    render(<ThemeToggle />);
    const button = screen.getByRole('button', { name: 'theme_toggle' });
    expect(button).toBeInTheDocument();
  });

  it('should display current theme icon', () => {
    render(<ThemeToggle />);
    expect(screen.getByText('light_mode')).toBeInTheDocument();
  });

  it('should be clickable', () => {
    render(<ThemeToggle />);
    const button = screen.getByRole('button');
    expect(() => fireEvent.click(button)).not.toThrow();
  });

  it('should have no accessibility violations', async () => {
    const { container } = render(<ThemeToggle />);
    const results = await axe(container);
    expect(results).toHaveNoViolations();
  });
});
