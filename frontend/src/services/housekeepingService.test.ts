import { describe, it, expect, vi, beforeEach } from 'vitest';
import api from './api';
import { housekeepingService } from './housekeepingService';

vi.mock('./api');

describe('housekeepingService', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('should fetch the resolved business date', async () => {
    const mock = { businessDate: '2026-10-05', timezone: 'Europe/Rome', cutoffHour: 4 };
    vi.mocked(api.get).mockResolvedValueOnce({ data: mock });

    const result = await housekeepingService.getBusinessDate();

    expect(api.get).toHaveBeenCalledWith('/api/v1/frontdesk/housekeeping/business-date');
    expect(result).toEqual(mock);
  });

  it('should fetch the worksheet for a given date', async () => {
    const mock = { date: '2026-10-05', generatedAt: '2026-10-05T04:00:00', provisional: true,
      hotelName: 'Test Hotel', rows: [], summary: {} };
    vi.mocked(api.get).mockResolvedValueOnce({ data: mock });

    const result = await housekeepingService.getWorksheet('2026-10-05');

    expect(api.get).toHaveBeenCalledWith('/api/v1/frontdesk/housekeeping/worksheet', {
      params: { date: '2026-10-05' },
    });
    expect(result).toEqual(mock);
  });

  it('should trigger the worksheet PDF download via a hidden iframe', () => {
    vi.useFakeTimers();
    const iframe = { style: {} as CSSStyleDeclaration, src: '' } as HTMLIFrameElement;
    const createElementSpy = vi.spyOn(document, 'createElement').mockReturnValue(iframe);
    const appendChildSpy = vi.spyOn(document.body, 'appendChild').mockImplementation((n) => n);
    const removeChildSpy = vi.spyOn(document.body, 'removeChild').mockImplementation((n) => n);

    housekeepingService.downloadWorksheetPdf('2026-10-05');

    expect(iframe.src).toBe('/api/v1/frontdesk/housekeeping/worksheet.pdf?date=2026-10-05');
    expect(iframe.style.display).toBe('none');
    expect(appendChildSpy).toHaveBeenCalledWith(iframe);
    expect(removeChildSpy).not.toHaveBeenCalled();

    vi.runAllTimers();
    expect(removeChildSpy).toHaveBeenCalledWith(iframe);

    createElementSpy.mockRestore();
    appendChildSpy.mockRestore();
    removeChildSpy.mockRestore();
    vi.useRealTimers();
  });
});
