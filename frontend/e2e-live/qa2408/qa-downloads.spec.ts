import { test, expect } from '@playwright/test';
import path from 'node:path';
import { mkdirSync, statSync, readFileSync } from 'node:fs';
import { csrfHeader, createCleanRoom, createGuest, createWalkInStay } from '../fixtures/api';
import { attachQaListeners, setContext, DOWNLOAD_DIR, logCustom } from './support/qaListeners';

// 2026-08-24 QA pass — Phase 5: real browser downloads (context.on('download'),
// NOT eyeballed) for every download-producing flow, saved under
// qa-artifacts/downloads/ and validated for filename, MIME/magic bytes, and
// (PDF/XML) structural sanity. Invoice PDF and FatturaPA XML are fired via a
// hidden <iframe> (billingService.ts, deliberate — see that file's comment on
// why fetch+Blob+synthetic-click was abandoned), so the download listener
// MUST be attached at the context level, not awaited via page.waitForEvent
// tied to a click that never itself triggers a navigation.

mkdirSync(DOWNLOAD_DIR, { recursive: true });

test.beforeEach(async ({ context }) => {
  attachQaListeners(context, { role: 'ADMIN', locale: 'it' });
});

test.describe('QA 2026-08-24 — download verification', () => {
  test('Invoice PDF and FatturaPA XML download with correct filename, MIME, and real content', async ({ page, request }) => {
    const headers = await csrfHeader(request);
    const room = await createCleanRoom(request, headers);
    const guest = await createGuest(request, headers, { fiscalDetails: true });
    const stay = await createWalkInStay(request, headers, { roomId: room.id, guestId: guest.id });
    const invoiceBefore = await (await request.get(`/api/v1/invoices/${stay.invoiceId}`)).json();
    await request.post(`/api/v1/invoices/${stay.invoiceId}/payments`, {
      headers, data: { amount: invoiceBefore.totalAmount, paymentMethod: 'CASH' },
    });
    await request.patch(`/api/v1/invoices/${stay.invoiceId}/document-type`, { headers, data: { documentType: 'FATTURA' } });
    const invoice = await (await request.get(`/api/v1/invoices/${stay.invoiceId}`)).json();
    const invoiceNumber: string = invoice.invoiceNumber;
    expect(invoiceNumber, 'fixture invoice must have a real progressive number before UI download').toBeTruthy();

    setContext('/billing', 'download-invoice-pdf-xml');
    await page.goto('/billing');
    await page.getByLabel(/cerca|search/i).fill(invoiceNumber);
    await page.waitForTimeout(500); // debounced search, not defect-masking
    await page.getByRole('button', { name: /visualizza|view/i }).first().click();

    // --- PDF (iframe-triggered) ---
    const pdfDownloadPromise = page.waitForEvent('download', { timeout: 15_000 });
    await page.getByRole('button', { name: /scarica pdf|download pdf/i }).click();
    const pdfDownload = await pdfDownloadPromise;
    const pdfPath = path.join(DOWNLOAD_DIR, `fattura-${stay.invoiceId}.pdf`);
    await pdfDownload.saveAs(pdfPath);
    const pdfSuggested = pdfDownload.suggestedFilename();
    expect(pdfSuggested, 'PDF filename must match fattura-{uuid}.pdf').toBe(`fattura-${stay.invoiceId}.pdf`);
    const pdfBytes = readFileSync(pdfPath);
    expect(pdfBytes.length, 'PDF file must not be empty').toBeGreaterThan(1000);
    expect(pdfBytes.subarray(0, 4).toString('latin1'), 'PDF must start with %PDF magic bytes').toBe('%PDF');
    logCustom('download_verified', { file: 'invoice_pdf', filename: pdfSuggested, sizeBytes: statSync(pdfPath).size });

    // --- FatturaPA XML (validate-then-iframe pattern) ---
    const xmlDownloadPromise = page.waitForEvent('download', { timeout: 15_000 });
    await page.getByRole('button', { name: /scarica fatturapa xml|download fatturapa xml/i }).click();
    const xmlDownload = await xmlDownloadPromise;
    const xmlPath = path.join(DOWNLOAD_DIR, `fatturaPA-${stay.invoiceId}.xml`);
    await xmlDownload.saveAs(xmlPath);
    expect(xmlDownload.suggestedFilename()).toBe(`fatturaPA-${stay.invoiceId}.xml`);
    const xmlContent = readFileSync(xmlPath, 'utf-8');
    expect(xmlContent.trimStart().startsWith('<?xml'), 'XML must start with an XML declaration').toBe(true);
    expect(xmlContent, 'progressive invoice number must appear in the XML').toContain(invoiceNumber.replace('/', ''));
    expect(xmlContent, "guest's real fiscal code must appear — not placeholder data").toContain('RSSMRA90A01H501U');
    for (const placeholder of ['00000000000', '>HOTELPMS<']) {
      expect(xmlContent).not.toContain(placeholder);
    }
    logCustom('download_verified', { file: 'fatturapa_xml', filename: xmlDownload.suggestedFilename(), sizeBytes: statSync(xmlPath).size });
  });

  test('Quotation PDF downloads with correct filename and valid PDF bytes', async ({ page, request }) => {
    const headers = await csrfHeader(request);
    const guest = await createGuest(request, headers);
    const room = await createCleanRoom(request, headers);
    const quotationResponse = await request.post('/api/v1/quotations', {
      headers,
      data: {
        guestId: guest.id,
        checkInDate: '2026-12-20',
        checkOutDate: '2026-12-22',
        validUntil: '2026-12-19',
        options: [{ label: 'QA download test option', roomIds: [room.id] }],
      },
    });
    expect(quotationResponse.status(), await quotationResponse.text()).toBe(201);
    const quotation = await quotationResponse.json();

    setContext(`/quotations/${quotation.id}`, 'download-quotation-pdf');
    await page.goto(`/quotations/${quotation.id}`);
    const downloadPromise = page.waitForEvent('download', { timeout: 15_000 });
    await page.getByRole('button', { name: /scarica pdf|download pdf/i }).click();
    const download = await downloadPromise;
    const pdfPath = path.join(DOWNLOAD_DIR, `preventivo-${quotation.id}.pdf`);
    await download.saveAs(pdfPath);
    expect(download.suggestedFilename()).toBe(`preventivo-${quotation.id}.pdf`);
    const bytes = readFileSync(pdfPath);
    expect(bytes.subarray(0, 4).toString('latin1')).toBe('%PDF');
    logCustom('download_verified', { file: 'quotation_pdf', filename: download.suggestedFilename(), sizeBytes: statSync(pdfPath).size });
  });

  test('Alloggiati .txt and .json exports download with correct filename and well-formed content', async ({ page, request }) => {
    // EXPECTED TO FAIL — real app bug, not a test artifact. stayService.ts's
    // downloadAlloggiatiReport/downloadAlloggiatiJson (lines ~64-97) still use
    // URL.createObjectURL(blob) + synthetic <a>.click() + immediate
    // URL.revokeObjectURL() — the exact pattern billingService.ts's own code
    // comment (same repo) documents as verified-broken in real Chrome
    // ("produced no visible file save... known failure mode for synthetic
    // clicks on blob: URLs, silently dropped, no JS-visible error") and
    // deliberately replaced with a hidden-iframe download for invoice PDF/
    // FatturaPA XML. That fix was never applied here. Reproduced live: the
    // network call succeeds (200, correct Content-Disposition/filename), the
    // UI shows a success toast, but no file reaches disk. See REPORT.md.
    test.setTimeout(45_000);
    // Fixture stay whose actual check-in date we can target the report at.
    const headers = await csrfHeader(request);
    const room = await createCleanRoom(request, headers);
    const guest = await createGuest(request, headers);
    await createWalkInStay(request, headers, { roomId: room.id, guestId: guest.id });
    const today = new Date().toISOString().split('T')[0];

    setContext('/stays', 'download-alloggiati');
    await page.goto('/stays');
    await page.getByLabel(/check-in date|data check-in/i).fill(today);

    const txtDownloadPromise = page.waitForEvent('download', { timeout: 15_000 });
    await page.locator('#generate-alloggiati-btn').click();
    const txtDownload = await txtDownloadPromise;
    const txtPath = path.join(DOWNLOAD_DIR, `alloggiati-${today}.txt`);
    await txtDownload.saveAs(txtPath);
    expect(txtDownload.suggestedFilename()).toBe(`alloggiati-${today}.txt`);
    const txtLines = readFileSync(txtPath, 'latin1').split('\r\n').filter((l) => l.length > 0);
    for (const line of txtLines) {
      expect(line.length, `Alloggiati fixed-width row must be exactly 168 chars, got ${line.length}`).toBe(168);
    }
    logCustom('download_verified', { file: 'alloggiati_txt', filename: txtDownload.suggestedFilename(), rowCount: txtLines.length, allRowsCorrectWidth: txtLines.every((l) => l.length === 168) });

    const jsonDownloadPromise = page.waitForEvent('download', { timeout: 15_000 });
    await page.locator('#download-alloggiati-json-btn').click();
    const jsonDownload = await jsonDownloadPromise;
    const jsonPath = path.join(DOWNLOAD_DIR, `alloggiati-${today}.json`);
    await jsonDownload.saveAs(jsonPath);
    expect(jsonDownload.suggestedFilename()).toBe(`alloggiati-${today}.json`);
    const parsed = JSON.parse(readFileSync(jsonPath, 'utf-8'));
    expect(Array.isArray(parsed), 'Alloggiati JSON export must be a JSON array').toBe(true);
    logCustom('download_verified', { file: 'alloggiati_json', filename: jsonDownload.suggestedFilename(), rowCount: parsed.length });
  });

  test('Owner Analytics CSV export downloads with UTF-8 BOM, semicolon separator, and localized headers/status', async ({ page }) => {
    test.setTimeout(45_000);
    setContext('/owner-dashboard', 'download-owner-csv');
    await page.goto('/owner-dashboard');
    // #export-csv-btn only renders once `report` state is populated, which
    // requires clicking "generate report" first — it is not auto-fetched on
    // mount (separate from the KPI trend chart, which auto-loads and 500s —
    // see REPORT.md; that failure does not block this button, this was a
    // test bug: the load step was simply missing).
    await page.locator('#load-report-btn').click();
    await page.locator('#export-csv-btn').waitFor({ state: 'visible', timeout: 15_000 });
    const downloadPromise = page.waitForEvent('download', { timeout: 15_000 });
    await page.locator('#export-csv-btn').click();
    const download = await downloadPromise;
    const csvPath = path.join(DOWNLOAD_DIR, download.suggestedFilename());
    await download.saveAs(csvPath);
    expect(download.suggestedFilename()).toMatch(/^owner-report-\d{4}-\d{2}-\d{2}-to-\d{4}-\d{2}-\d{2}\.csv$/);
    const raw = readFileSync(csvPath);
    const hasBom = raw[0] === 0xef && raw[1] === 0xbb && raw[2] === 0xbf;
    expect(hasBom, 'CSV must lead with a UTF-8 BOM for Excel-IT compatibility (R2 #7 fix)').toBe(true);
    const text = raw.toString('utf-8');
    const headerLine = text.split(/\r?\n/)[1] ?? text.split(/\r?\n/)[0]; // line 0 may be the BOM-prefixed header
    expect(headerLine, 'CSV must use ; as separator, not ,').toContain(';');
    logCustom('download_verified', { file: 'owner_csv', filename: download.suggestedFilename(), hasBom, headerLine });
  });
});
