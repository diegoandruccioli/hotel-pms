import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { render, screen, waitFor, fireEvent } from '@testing-library/react';
import { axe } from 'vitest-axe';
import { AlloggiatiReportSection } from './AlloggiatiReportSection';
import { stayService } from '../../services/stayService';
import { mockAxiosErrorWithDetail } from '../../test-utils/mockAxiosError';

vi.mock('react-i18next', () => ({
  useTranslation: () => ({ t: (key: string) => key, i18n: { language: 'en' } }),
  initReactI18next: { type: '3rdParty', init: vi.fn() },
}));

vi.mock('../../services/stayService', () => ({
  stayService: {
    downloadAlloggiatiReport: vi.fn(),
    downloadAlloggiatiJson: vi.fn(),
    submitAlloggiatiReport: vi.fn(),
  },
}));

const mockAddToast = vi.fn();
vi.mock('../../store/toastStore', () => ({
  useToastStore: (selector: unknown) =>
    (selector as (s: { addToast: typeof mockAddToast }) => unknown)({ addToast: mockAddToast }),
}));

describe('AlloggiatiReportSection', () => {
  beforeEach(() => vi.clearAllMocks());

  it('updates the date field on change', () => {
    render(<AlloggiatiReportSection isAdminOrOwner={false} />);
    const dateInput = screen.getByLabelText('check_in_date') as HTMLInputElement;
    fireEvent.change(dateInput, { target: { value: '2026-01-15' } });
    expect(dateInput.value).toBe('2026-01-15');
  });

  it('hides admin-only buttons for non admin/owner roles', () => {
    render(<AlloggiatiReportSection isAdminOrOwner={false} />);
    expect(screen.queryByText('download_json_export')).not.toBeInTheDocument();
    expect(screen.queryByText('alloggiati_submit')).not.toBeInTheDocument();
  });

  it('downloads the TXT report and shows a success toast', async () => {
    vi.mocked(stayService.downloadAlloggiatiReport).mockResolvedValueOnce(undefined);
    render(<AlloggiatiReportSection isAdminOrOwner={false} />);
    fireEvent.click(screen.getByText('generate_and_download'));
    await waitFor(() => expect(mockAddToast).toHaveBeenCalledWith('alloggiati_report_downloaded', 'success'));
  });

  it('shows an error toast when the TXT download fails', async () => {
    vi.mocked(stayService.downloadAlloggiatiReport)
      .mockRejectedValueOnce(mockAxiosErrorWithDetail('Nessuna presenza da riportare per la data selezionata'));
    render(<AlloggiatiReportSection isAdminOrOwner={false} />);
    fireEvent.click(screen.getByText('generate_and_download'));
    await waitFor(() =>
      expect(mockAddToast).toHaveBeenCalledWith('Nessuna presenza da riportare per la data selezionata', 'error'));
  });

  it('downloads the JSON export for admin/owner and shows a success toast', async () => {
    vi.mocked(stayService.downloadAlloggiatiJson).mockResolvedValueOnce(undefined);
    render(<AlloggiatiReportSection isAdminOrOwner />);
    fireEvent.click(screen.getByText('download_json_export'));
    await waitFor(() => expect(mockAddToast).toHaveBeenCalledWith('alloggiati_json_downloaded', 'success'));
  });

  describe('submit to PS portal', () => {
    const confirmSpy = vi.spyOn(window, 'confirm');

    afterEach(() => confirmSpy.mockReset());

    it('does nothing when the confirm dialog is dismissed', () => {
      confirmSpy.mockReturnValue(false);
      render(<AlloggiatiReportSection isAdminOrOwner />);
      fireEvent.click(screen.getByText('alloggiati_submit'));
      expect(stayService.submitAlloggiatiReport).not.toHaveBeenCalled();
    });

    it('submits the report and shows a success toast when confirmed', async () => {
      confirmSpy.mockReturnValue(true);
      vi.mocked(stayService.submitAlloggiatiReport).mockResolvedValueOnce(undefined);
      render(<AlloggiatiReportSection isAdminOrOwner />);
      fireEvent.click(screen.getByText('alloggiati_submit'));
      await waitFor(() => expect(mockAddToast).toHaveBeenCalledWith('alloggiati_submit_success', 'success'));
    });

    it('shows an error toast with the backend reason when the submit fails', async () => {
      confirmSpy.mockReturnValue(true);
      vi.mocked(stayService.submitAlloggiatiReport)
        .mockRejectedValueOnce(mockAxiosErrorWithDetail('Credenziali Alloggiati Web non valide'));
      render(<AlloggiatiReportSection isAdminOrOwner />);
      fireEvent.click(screen.getByText('alloggiati_submit'));
      await waitFor(() => expect(mockAddToast).toHaveBeenCalledWith('alloggiati_submit_error', 'error'));
    });

    it('falls back to a generic error toast when the submit fails without a backend detail', async () => {
      confirmSpy.mockReturnValue(true);
      vi.mocked(stayService.submitAlloggiatiReport).mockRejectedValueOnce(new Error('network down'));
      render(<AlloggiatiReportSection isAdminOrOwner />);
      fireEvent.click(screen.getByText('alloggiati_submit'));
      await waitFor(() => expect(mockAddToast).toHaveBeenCalledWith('alloggiati_submit_failed', 'error'));
    });
  });

  it('passes axe accessibility check', async () => {
    const { container } = render(<AlloggiatiReportSection isAdminOrOwner />);
    expect(await axe(container)).toHaveNoViolations();
  });
});
