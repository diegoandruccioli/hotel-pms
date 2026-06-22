import { describe, it, expect, vi } from 'vitest';
import { render, screen, fireEvent } from '@testing-library/react';
import { axe } from 'vitest-axe';
import { SettingsPageHeader } from './SettingsPageHeader';

vi.mock('react-i18next', () => ({
  useTranslation: () => ({ t: (key: string) => key, i18n: { language: 'en' } }),
  initReactI18next: { type: '3rdParty', init: vi.fn() },
}));

describe('SettingsPageHeader', () => {
  it('renders the title and icon', () => {
    render(<SettingsPageHeader icon="lock" title="Cambia Password" onBack={vi.fn()} />);
    expect(screen.getByRole('heading', { level: 1, name: 'Cambia Password' })).toBeInTheDocument();
  });

  it('renders the subtitle when provided', () => {
    render(<SettingsPageHeader icon="person" title="Profilo" subtitle="Sottotitolo" onBack={vi.fn()} />);
    expect(screen.getByText('Sottotitolo')).toBeInTheDocument();
  });

  it('does not render a subtitle paragraph when omitted', () => {
    render(<SettingsPageHeader icon="person" title="Profilo" onBack={vi.fn()} />);
    expect(screen.queryByText('Sottotitolo')).not.toBeInTheDocument();
  });

  it('calls onBack when the back button is clicked', () => {
    const onBack = vi.fn();
    render(<SettingsPageHeader icon="person" title="Profilo" onBack={onBack} />);
    fireEvent.click(screen.getByRole('button', { name: 'back' }));
    expect(onBack).toHaveBeenCalledTimes(1);
  });

  it('should have no accessibility violations', async () => {
    const { container } = render(<SettingsPageHeader icon="person" title="Profilo" subtitle="Sub" onBack={vi.fn()} />);
    const results = await axe(container);
    expect(results).toHaveNoViolations();
  });
});
