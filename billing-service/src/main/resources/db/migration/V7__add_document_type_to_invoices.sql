-- Verticale 4: document type toggle (fattura / ricevuta non fiscale)
-- Default FATTURA preserves existing rows' fiscal status.
ALTER TABLE invoices
    ADD COLUMN document_type VARCHAR(30) NOT NULL DEFAULT 'FATTURA';
