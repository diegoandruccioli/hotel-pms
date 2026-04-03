import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen } from '@testing-library/react';
import { OwnerDashboard } from './OwnerDashboard';

vi.mock('react-i18next', () => ({
  useTranslation: () => ({ t: (key: string) => key, i18n: { language: 'en' } }),
  initReactI18next: { type: '3rdParty', init: vi.fn() },
}));

vi.mock('../services/billingReportService', () => ({
  billingReportService: { getOwnerFinancialReport: vi.fn() },
}));

vi.mock('../store/toastStore', () => ({
  useToastStore: (selector: unknown) =>
    (selector as (s: { addToast: () => void }) => unknown)({ addToast: vi.fn() }),
}));

const mockUseAuthStore = vi.fn();
vi.mock('../store/authStore', () => ({
  useAuthStore: () => mockUseAuthStore(),
}));

describe('OwnerDashboard', () => {
  beforeEach(() => vi.clearAllMocks());

  it('should show access restricted for non-owner', () => {
    mockUseAuthStore.mockReturnValue({ user: { role: 'RECEPTIONIST' } });
    render(<OwnerDashboard />);
    expect(screen.getByText('access_restricted')).toBeInTheDocument();
  });

  it('should show access restricted for unauthenticated user', () => {
    mockUseAuthStore.mockReturnValue({ user: null });
    render(<OwnerDashboard />);
    expect(screen.getByText('access_restricted')).toBeInTheDocument();
  });

  it('should render dashboard for OWNER role', () => {
    mockUseAuthStore.mockReturnValue({ user: { role: 'OWNER' } });
    render(<OwnerDashboard />);
    expect(screen.getByText('owner_dashboard')).toBeInTheDocument();
  });

  it('should render dashboard for ADMIN role', () => {
    mockUseAuthStore.mockReturnValue({ user: { role: 'ADMIN' } });
    render(<OwnerDashboard />);
    expect(screen.getByText('owner_dashboard')).toBeInTheDocument();
  });

  it('should show date filter fields for authorized users', () => {
    mockUseAuthStore.mockReturnValue({ user: { role: 'OWNER' } });
    render(<OwnerDashboard />);
    expect(screen.getByLabelText('start_date')).toBeInTheDocument();
    expect(screen.getByLabelText('end_date')).toBeInTheDocument();
  });

  it('should show generate report button', () => {
    mockUseAuthStore.mockReturnValue({ user: { role: 'OWNER' } });
    render(<OwnerDashboard />);
    expect(screen.getByText('generate_report')).toBeInTheDocument();
  });
});
