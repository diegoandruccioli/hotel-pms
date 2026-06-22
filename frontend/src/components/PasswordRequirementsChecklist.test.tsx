import { describe, it, expect, vi } from 'vitest';
import { render, screen } from '@testing-library/react';
import { axe } from 'vitest-axe';
import { PasswordRequirementsChecklist } from './PasswordRequirementsChecklist';

vi.mock('react-i18next', () => ({
  useTranslation: () => ({ t: (key: string) => key, i18n: { language: 'en' } }),
  initReactI18next: { type: '3rdParty', init: vi.fn() },
}));

describe('PasswordRequirementsChecklist', () => {
  it('renders all four requirements', () => {
    render(<PasswordRequirementsChecklist password="" />);
    expect(screen.getByText('password_req_length')).toBeInTheDocument();
    expect(screen.getByText('password_req_uppercase')).toBeInTheDocument();
    expect(screen.getByText('password_req_digits')).toBeInTheDocument();
    expect(screen.getByText('password_req_special')).toBeInTheDocument();
  });

  it('marks no requirement as met for an empty password', () => {
    render(<PasswordRequirementsChecklist password="" />);
    const items = screen.getAllByText(/password_req_/);
    items.forEach((item) => expect(item.closest('li')).toHaveClass('text-on-surface-variant'));
  });

  it('marks only the length requirement as met for a long but otherwise weak password', () => {
    render(<PasswordRequirementsChecklist password="aaaaaaaaaaaaaaaa" />);
    expect(screen.getByText('password_req_length').closest('li')).toHaveClass('text-tertiary');
    expect(screen.getByText('password_req_uppercase').closest('li')).toHaveClass('text-on-surface-variant');
    expect(screen.getByText('password_req_digits').closest('li')).toHaveClass('text-on-surface-variant');
    expect(screen.getByText('password_req_special').closest('li')).toHaveClass('text-on-surface-variant');
  });

  it('marks all requirements as met for a password satisfying the full policy', () => {
    render(<PasswordRequirementsChecklist password="HotelPms@@2026xx" />);
    expect(screen.getByText('password_req_length').closest('li')).toHaveClass('text-tertiary');
    expect(screen.getByText('password_req_uppercase').closest('li')).toHaveClass('text-tertiary');
    expect(screen.getByText('password_req_digits').closest('li')).toHaveClass('text-tertiary');
    expect(screen.getByText('password_req_special').closest('li')).toHaveClass('text-tertiary');
  });

  it('should have no accessibility violations', async () => {
    const { container } = render(<PasswordRequirementsChecklist password="partial1" />);
    const results = await axe(container);
    expect(results).toHaveNoViolations();
  });
});
