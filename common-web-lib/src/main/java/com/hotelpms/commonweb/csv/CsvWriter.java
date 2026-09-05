package com.hotelpms.commonweb.csv;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;
import org.apache.commons.csv.QuoteMode;

import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Set;

/**
 * Shared CSV writer for server-side exports (reservations/guests/invoices/
 * owner-report). Semicolon-delimited with a UTF-8 BOM — the Excel-IT
 * convention the owner-dashboard CSV already used client-side before this;
 * moving the format here is what lets every export share exactly one
 * definition of it instead of drifting. Also replaces
 * {@code FatturaPAServiceImpl}'s hand-rolled {@code csvEscape} with Commons
 * CSV's own RFC 4180 quoting.
 *
 * <p>Designed for streaming: a controller pages through a repository result
 * and calls {@link #printRow} per row inside a {@code StreamingResponseBody},
 * rather than building the whole file in memory first (the pre-existing
 * pattern in {@code FatturaPAServiceImpl.generateBatchZip}, kept there
 * unchanged — this class is for the new exports only).
 */
public final class CsvWriter implements AutoCloseable {

    /**
     * UTF-8 byte order mark (U+FEFF), written before the header so Excel-IT opens the
     * file as UTF-8 rather than guessing a legacy codepage. This source file is UTF-8
     * (confirmed against the built .class file, not just visual inspection — the whole
     * build already relies on UTF-8 source elsewhere, e.g. em-dashes in Javadoc).
     */
    private static final char UTF8_BOM = '﻿';

    private static final CSVFormat FORMAT = CSVFormat.DEFAULT.builder()
            .setDelimiter(';')
            .setRecordSeparator("\r\n")
            .setQuoteMode(QuoteMode.MINIMAL)
            .build();

    /**
     * Leading characters that Excel/LibreOffice/Google Sheets interpret as the start of a
     * formula (or, for tab/CR, as a way to smuggle one past naive quoting) when a CSV cell
     * is opened in a spreadsheet -- CSV formula injection (CWE-1236). A cell whose stringified
     * value starts with one of these is prefixed with a literal apostrophe, which every major
     * spreadsheet application renders as "force text" and strips from display.
     */
    private static final Set<Character> FORMULA_TRIGGER_CHARS = Set.of('=', '+', '-', '@', '\t', '\r');

    private final CSVPrinter printer;

    private CsvWriter(final CSVPrinter printer) {
        this.printer = printer;
    }

    /**
     * Opens a writer over {@code out}: writes the UTF-8 BOM, then the header
     * row. The returned instance owns {@code out} from this point on —
     * close it (try-with-resources) rather than closing {@code out} directly.
     *
     * @param out    the stream to write to (e.g. a controller's
     *               {@code StreamingResponseBody} output stream)
     * @param header the column headers, written as the first row
     * @return an open writer, ready for {@link #printRow}
     * @throws IOException if writing the preamble or header fails
     */
    public static CsvWriter open(final OutputStream out, final List<String> header) throws IOException {
        final Writer writer = new OutputStreamWriter(out, StandardCharsets.UTF_8);
        writer.write(UTF8_BOM);
        final CSVPrinter printer = new CSVPrinter(writer, FORMAT);
        printer.printRecord(header.stream().map(CsvWriter::neutralizeFormula).toList());
        return new CsvWriter(printer);
    }

    /**
     * Writes one data row. Values are stringified and quoted/escaped by
     * Commons CSV as needed (embedded delimiter, quote, or newline) —
     * never hand-escaped by the caller. Any value beginning with a
     * formula-trigger character is neutralized first (see {@link
     * #FORMULA_TRIGGER_CHARS}) since exported columns can carry
     * user-supplied text (e.g. a guest's name).
     *
     * @param values the row's cell values, in column order
     * @throws IOException if writing fails
     */
    public void printRow(final List<?> values) throws IOException {
        printer.printRecord(values.stream().map(CsvWriter::neutralizeFormula).toList());
    }

    private static String neutralizeFormula(final Object value) {
        final String text = value == null ? "" : String.valueOf(value);
        if (!text.isEmpty() && FORMULA_TRIGGER_CHARS.contains(text.charAt(0))) {
            return "'" + text;
        }
        return text;
    }

    /**
     * Flushes and closes the underlying writer and stream.
     *
     * @throws IOException if closing fails
     */
    @Override
    public void close() throws IOException {
        printer.close(true);
    }
}
