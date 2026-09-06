package com.hotelpms.commonweb.csv;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class CsvWriterTest {

    private static final int BYTE_MASK = 0xFF;
    private static final int UTF8_BOM_BYTE_0 = 0xEF;
    private static final int UTF8_BOM_BYTE_1 = 0xBB;
    private static final int UTF8_BOM_BYTE_2 = 0xBF;

    @Test
    void writesUtf8BomThenSemicolonDelimitedHeaderAndRows() throws IOException {
        final ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (CsvWriter csv = CsvWriter.open(out, List.of("Name", "Amount"))) {
            csv.printRow(List.of("Mario Rossi", "100.00"));
            csv.printRow(List.of("Anna Bianchi", "50.50"));
        }

        final byte[] bytes = out.toByteArray();
        assertThat(bytes[0] & BYTE_MASK).isEqualTo(UTF8_BOM_BYTE_0);
        assertThat(bytes[1] & BYTE_MASK).isEqualTo(UTF8_BOM_BYTE_1);
        assertThat(bytes[2] & BYTE_MASK).isEqualTo(UTF8_BOM_BYTE_2);

        final String content = new String(bytes, StandardCharsets.UTF_8);
        assertThat(content).contains("Name;Amount\r\n");
        assertThat(content).contains("Mario Rossi;100.00\r\n");
        assertThat(content).contains("Anna Bianchi;50.50\r\n");
    }

    @Test
    void quotesValuesContainingTheDelimiterOrEmbeddedQuotes() throws IOException {
        final ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (CsvWriter csv = CsvWriter.open(out, List.of("Value"))) {
            csv.printRow(List.of("contains;semicolon"));
            csv.printRow(List.of("contains\"quote"));
            csv.printRow(List.of("contains\nnewline"));
        }

        final String content = new String(out.toByteArray(), StandardCharsets.UTF_8);
        assertThat(content).contains("\"contains;semicolon\"");
        assertThat(content).contains("\"contains\"\"quote\"");
        assertThat(content).contains("\"contains\nnewline\"");
    }

    @Test
    void handlesAnEmptyBodyByWritingOnlyThePreambleAndHeader() throws IOException {
        final ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (CsvWriter csv = CsvWriter.open(out, List.of("Col"))) {
            // no rows
            assertThat(csv).isNotNull();
        }

        final String content = new String(out.toByteArray(), StandardCharsets.UTF_8);
        assertThat(content).isEqualTo("﻿Col\r\n");
    }

    @Test
    void prefixesValuesStartingWithAFormulaTriggerCharacterToPreventCsvFormulaInjection() throws IOException {
        final ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (CsvWriter csv = CsvWriter.open(out, List.of("Name"))) {
            csv.printRow(List.of("=cmd|'/c calc'!A1"));
            csv.printRow(List.of("+1+1"));
            csv.printRow(List.of("-1+1"));
            csv.printRow(List.of("@SUM(A1:A2)"));
            csv.printRow(List.of("Mario Rossi"));
        }

        final String content = new String(out.toByteArray(), StandardCharsets.UTF_8);
        assertThat(content).contains("'=cmd|'/c calc'!A1");
        assertThat(content).contains("'+1+1");
        assertThat(content).contains("'-1+1");
        assertThat(content).contains("'@SUM(A1:A2)");
        assertThat(content).contains("Mario Rossi\r\n");
        assertThat(content).doesNotContain("'Mario Rossi");
    }

    @Test
    void leavesNullAndEmptyValuesUnprefixed() throws IOException {
        final ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (CsvWriter csv = CsvWriter.open(out, List.of("Col"))) {
            csv.printRow(java.util.Collections.singletonList(null));
            csv.printRow(List.of(""));
        }

        final String content = new String(out.toByteArray(), StandardCharsets.UTF_8);
        assertThat(content).isEqualTo("﻿Col\r\n\"\"\r\n\"\"\r\n");
    }
}
