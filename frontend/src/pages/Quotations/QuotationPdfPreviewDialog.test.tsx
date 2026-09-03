import { render, screen, waitFor, fireEvent } from '@testing-library/react';
import { describe, it, expect, vi, beforeEach } from 'vitest';
import { axe } from 'vitest-axe';
import { QuotationPdfPreviewDialog } from './QuotationPdfPreviewDialog';
import { quotationService } from '../../services';

vi.mock('../../services/quotationService');

vi.mock('react-i18next', () => ({
  useTranslation: () => ({ t: (key: string) => key, i18n: { language: 'en' } }),
  initReactI18next: { type: '3rdParty', init: vi.fn() },
}));

vi.mock('focus-trap-react', () => ({
  default: ({ children }: { children: React.ReactNode }) => <>{children}</>,
}));

describe('QuotationPdfPreviewDialog', () => {
  const onClose = vi.fn();
  const createObjectURL = vi.fn(() => 'blob:http://test/123');
  const revokeObjectURL = vi.fn();

  beforeEach(() => {
    vi.clearAllMocks();
    vi.stubGlobal('URL', { createObjectURL, revokeObjectURL });
  });

  it('shows the PDF in an iframe once the blob loads', async () => {
    vi.mocked(quotationService.getPdfBlob).mockResolvedValue(new Blob(['%PDF-1.6']));
    render(<QuotationPdfPreviewDialog quotationId="q1" onClose={onClose} />);

    await waitFor(() => expect(screen.getByTitle('pdf_preview_title')).toBeInTheDocument());
    expect(createObjectURL).toHaveBeenCalled();
  });

  it('revokes the object URL on unmount', async () => {
    vi.mocked(quotationService.getPdfBlob).mockResolvedValue(new Blob(['%PDF-1.6']));
    const { unmount } = render(<QuotationPdfPreviewDialog quotationId="q1" onClose={onClose} />);

    await waitFor(() => expect(screen.getByTitle('pdf_preview_title')).toBeInTheDocument());
    unmount();
    expect(revokeObjectURL).toHaveBeenCalledWith('blob:http://test/123');
  });

  it('falls back to a download button on load failure', async () => {
    vi.mocked(quotationService.getPdfBlob).mockRejectedValue({ response: {} });
    render(<QuotationPdfPreviewDialog quotationId="q1" onClose={onClose} />);

    await waitFor(() => expect(screen.getByText('action_download_pdf')).toBeInTheDocument());
    fireEvent.click(screen.getByText('action_download_pdf'));
    expect(quotationService.downloadPdf).toHaveBeenCalledWith('q1');
  });

  it('passes axe accessibility check', async () => {
    // Uses the error/fallback state, not the loaded iframe: axe-core's frame-crawling
    // logic doesn't handle jsdom's blob: "frames", which isn't a real accessibility
    // concern (jsdom never renders the iframe's document either way).
    vi.mocked(quotationService.getPdfBlob).mockRejectedValue({ response: {} });
    const { container } = render(<QuotationPdfPreviewDialog quotationId="q1" onClose={onClose} />);
    await waitFor(() => screen.getByText('action_download_pdf'));
    expect(await axe(container)).toHaveNoViolations();
  });
});
