import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import { axe } from 'vitest-axe';
import { ExportCsvButton } from './ExportCsvButton';

const useTranslationMock = vi.hoisted(() => vi.fn());

vi.mock('react-i18next', () => ({
  useTranslation: useTranslationMock,
  initReactI18next: { type: '3rdParty', init: vi.fn() },
}));

beforeEach(() => {
  useTranslationMock.mockReset();
  useTranslationMock.mockImplementation(() => ({ t: (key: string) => key, i18n: { language: 'en' } }));
});

describe('ExportCsvButton', () => {
  it('shows the translated label and calls onExport on click', async () => {
    const onExport = vi.fn().mockResolvedValue(undefined);
    render(<ExportCsvButton onExport={onExport} />);

    fireEvent.click(screen.getByRole('button', { name: 'export_csv' }));

    expect(onExport).toHaveBeenCalledTimes(1);
    await waitFor(() => expect(screen.getByRole('button')).toBeEnabled());
  });

  it('is disabled while the export runs, so a second click does nothing', async () => {
    let finish: () => void = () => undefined;
    const onExport = vi.fn(
      () =>
        new Promise<void>((resolve) => {
          finish = resolve;
        })
    );
    render(<ExportCsvButton onExport={onExport} />);
    const button = screen.getByRole('button');

    fireEvent.click(button);
    await waitFor(() => expect(button).toBeDisabled());
    fireEvent.click(button);
    expect(onExport).toHaveBeenCalledTimes(1);

    finish();
    await waitFor(() => expect(button).toBeEnabled());
  });

  it('becomes enabled again when onExport rejects', async () => {
    const onExport = vi.fn().mockRejectedValue(new Error('boom'));
    const unhandled = vi.fn();
    process.on('unhandledRejection', unhandled);
    render(<ExportCsvButton onExport={onExport} />);
    const button = screen.getByRole('button');

    fireEvent.click(button);

    await waitFor(() => expect(button).toBeEnabled());
    process.off('unhandledRejection', unhandled);
  });

  it('has no accessibility violations', async () => {
    const { container } = render(<ExportCsvButton onExport={vi.fn().mockResolvedValue(undefined)} />);
    expect(await axe(container)).toHaveNoViolations();
  });
});
