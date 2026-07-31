import { test, expect } from '@playwright/test';
import { csrfHeader, createCleanRoom, createGuest, createWalkInStay } from './fixtures/api';

// Checkout + FatturaPA against the real backend (Fase 7 / item 20). Exercises
// the full billing lifecycle a mocked spec cannot: checkout is gated on the
// invoice actually being PAID (BillingNotPaidException, backend-enforced —
// no mock can accidentally get this gate wrong), and both PDF and FatturaPA
// XML generation run the real rendering pipeline (pdf-template-engine /
// FatturaPAServiceImpl) against real invoice/charge/guest data, not a canned
// byte blob.

test.describe('Checkout + invoice PDF + FatturaPA XML against the real backend', () => {
    let stayId: string;
    let invoiceId: string;

    test.beforeAll(async ({ request }) => {
        const headers = await csrfHeader(request);
        const room = await createCleanRoom(request, headers);
        // fiscalDetails: true — FatturaPAServiceImpl.generateXml requires the
        // guest's structured address (cap/comune/provincia) to be present
        // (GUEST_STRUCTURED_ADDRESS_INCOMPLETE otherwise).
        const guest = await createGuest(request, headers, { fiscalDetails: true });
        const stay = await createWalkInStay(request, headers, { roomId: room.id, guestId: guest.id });
        stayId = stay.id;
        invoiceId = stay.invoiceId;
    });

    test('checkout is blocked while unpaid, succeeds once paid, and both PDF and FatturaPA XML render for real', async ({ request }) => {
        const headers = await csrfHeader(request);

        // 1. Checkout must be rejected — BillingNotPaidException, not a
        // client-side assumption. A mocked equivalent of this test would
        // have no way to get this wrong, since the mock would just return
        // whatever status the test author hardcoded.
        const blockedCheckout = await request.put(`/api/v1/stays/${stayId}/check-out`, { headers });
        expect(blockedCheckout.status(), await blockedCheckout.text()).toBe(409);

        // 2. Pay the invoice in full — read the real totalAmount rather than
        // assuming it (charge amount = real RoomType.basePrice, not hardcoded).
        const invoiceBefore = await request.get(`/api/v1/invoices/${invoiceId}`);
        expect(invoiceBefore.status()).toBe(200);
        const { totalAmount } = (await invoiceBefore.json()) as { totalAmount: number };
        expect(totalAmount).toBeGreaterThan(0);

        const paymentResponse = await request.post(`/api/v1/invoices/${invoiceId}/payments`, {
            headers,
            data: { amount: totalAmount, paymentMethod: 'CASH' },
        });
        expect(paymentResponse.status(), await paymentResponse.text()).toBe(201);

        const invoiceAfterPayment = await request.get(`/api/v1/invoices/${invoiceId}`);
        const { status: statusAfterPayment } = (await invoiceAfterPayment.json()) as { status: string };
        expect(statusAfterPayment).toBe('PAID');

        // 3. Now checkout succeeds against the real backend.
        const checkout = await request.put(`/api/v1/stays/${stayId}/check-out`, { headers });
        expect(checkout.status(), await checkout.text()).toBe(200);

        // 4. Invoice PDF — real rendering pipeline, not a stub byte array.
        const pdfResponse = await request.get(`/api/v1/invoices/${invoiceId}/pdf`);
        expect(pdfResponse.status()).toBe(200);
        expect(pdfResponse.headers()['content-type']).toContain('application/pdf');
        const pdfBytes = await pdfResponse.body();
        expect(pdfBytes.length).toBeGreaterThan(1000);
        expect(pdfBytes.subarray(0, 4).toString('latin1')).toBe('%PDF');

        // 5. FatturaPA requires documentType=FATTURA (default is RICEVUTA —
        // generating it before this PATCH would 409 SDI_ONLY_VALID_FOR_FATTURA).
        const docTypeResponse = await request.patch(`/api/v1/invoices/${invoiceId}/document-type`, {
            headers,
            data: { documentType: 'FATTURA' },
        });
        expect(docTypeResponse.status(), await docTypeResponse.text()).toBe(200);

        const fatturaResponse = await request.get(`/api/v1/invoices/${invoiceId}/fatturaPA`);
        expect(fatturaResponse.status(), await fatturaResponse.text()).toBe(200);
        expect(fatturaResponse.headers()['content-type']).toContain('xml');
        const xml = (await fatturaResponse.body()).toString('utf-8');
        expect(xml).toContain('<?xml');
        // The guest's real fiscal code from the fixture, proving the XML was
        // built from real invoice/guest data, not a fixed template.
        expect(xml).toContain('RSSMRA90A01H501U');
    });
});
