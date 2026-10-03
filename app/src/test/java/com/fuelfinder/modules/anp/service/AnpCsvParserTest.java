package com.fuelfinder.modules.anp.service;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.ByteBuffer;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AnpCsvParserTest {

    private final AnpCsvParser parser = new AnpCsvParser();

    @Test
    void parsesUtf8SemicolonCsvWithBomQuotedValuesAndEmbeddedNewline() {
        String csv = "\uFEFFRegiao - Sigla;Estado - Sigla;Municipio;Revenda;CNPJ da Revenda;"
                + "Nome da Rua;Numero Rua;Bairro;Cep;Produto;Data da Coleta;"
                + "Valor de Venda;Unidade de Medida;Bandeira\r\n"
                + "SE;SP;São Paulo;\"Posto; Central\";12.345.678/0001-95;"
                + "Rua Um;10;Centro;01000-000;ETANOL;29/09/2026;4,199;R$/L;Marca\r\n"
                + "SE;SP;São Paulo;\"Posto\nNovo\";12345678000196;"
                + "Rua Dois;20;Centro;01000-000;GNV;2026-09-29;4,000;R$/m³;Marca";

        ParsedAnpCsv result = parser.parse(csv.getBytes(StandardCharsets.UTF_8));

        assertEquals(2, result.totalRecordsRead());
        assertEquals(2, result.records().size());
        assertEquals("Posto; Central", result.records().get(0).values().get("revenda"));
        assertEquals("Posto\nNovo", result.records().get(1).values().get("revenda"));
        assertEquals("29/09/2026", result.records().get(0).values().get("data da coleta"));
        assertEquals(List.of(), result.errors());
    }

    @Test
    void mapsColumnsFromTheAutomotiveAnpReferenceLayout() {
        String csv = "\uFEFFRegiao - Sigla;Estado - Sigla;Municipio;Revenda;CNPJ da Revenda;"
                + "Nome da Rua;Numero Rua;Complemento;Bairro;Cep;Produto;Data da Coleta;"
                + "Valor de Venda;Valor de Compra;Unidade de Medida;Bandeira\r\n"
                + "N;AC;CRUZEIRO DO SUL;POSTO CENTRAL;01.492.748/0003-83;"
                + "\"AVENIDA CENTRAL; KM 2\";440;;CENTRO;69980-000;GASOLINA;"
                + "02/01/2026;7,97;;\"R$ / litro\";IPIRANGA";

        AnpCsvRow row = parser.parse(csv.getBytes(StandardCharsets.UTF_8)).records().get(0);

        assertEquals("AC", row.values().get("estado sigla"));
        assertEquals("CRUZEIRO DO SUL", row.values().get("municipio"));
        assertEquals("POSTO CENTRAL", row.values().get("revenda"));
        assertEquals("AVENIDA CENTRAL; KM 2", row.values().get("nome da rua"));
        assertEquals("7,97", row.values().get("valor de venda"));
        assertEquals("R$ / litro", row.values().get("unidade de medida"));
    }

    @Test
    void parsesTabSeparatedAndCommaSeparatedFiles() {
        String tabSeparated = header('\t') + "\n" + row('\t');
        assertEquals(1, parser.parse(tabSeparated.getBytes(StandardCharsets.UTF_8))
                .totalRecordsRead());

        String commaSeparated = header(',') + "\n" + row(',');
        assertEquals(1, parser.parse(commaSeparated.getBytes(StandardCharsets.UTF_8))
                .records().size());
    }

    @Test
    void parsesEscapedQuotesInHeadersAndFields() {
        String csv = header(';').replace("Bandeira", "\"Bandeira \"\"especial\"\"\"")
                + "\n" + row(';').replace("Marca", "\"Marca \"\"especial\"\"\"");

        ParsedAnpCsv result = parser.parse(csv.getBytes(StandardCharsets.UTF_8));

        assertEquals("Marca \"especial\"",
                result.records().get(0).values().get("bandeira especial"));
    }

    @Test
    void fallsBackToWindows1252ForLegacyAnpExports() {
        String csv = header(';') + "\n" + row(';').replace("Sao Paulo", "São Paulo");

        ParsedAnpCsv result = parser.parse(csv.getBytes(CharsetForTests.WINDOWS_1252));

        assertEquals("São Paulo", result.records().get(0).values().get("municipio"));
    }

    @Test
    void parsesUtf16LittleEndianAndBigEndianFilesWithBom() {
        String csv = header(';') + "\r\n" + row(';');

        ParsedAnpCsv littleEndian = parser.parse(utf16WithBom(csv, StandardCharsets.UTF_16LE, true));
        ParsedAnpCsv bigEndian = parser.parse(utf16WithBom(csv, StandardCharsets.UTF_16BE, false));

        assertEquals(1, littleEndian.records().size());
        assertEquals(1, bigEndian.records().size());
        assertEquals("12345678000195",
                littleEndian.records().get(0).values().get("cnpj da revenda"));
        assertEquals("12345678000195",
                bigEndian.records().get(0).values().get("cnpj da revenda"));
    }

    @Test
    void parsesCsvFromZipWithoutExtractingArchivePaths() throws IOException {
        byte[] archive = zip(Map.of(
                "../notes.txt", "ignore me".getBytes(StandardCharsets.UTF_8),
                "Preços semestrais - AUTOMOTIVOS_2026.01.csv",
                ("\uFEFF" + header(';') + "\r\n" + row(';'))
                        .getBytes(StandardCharsets.UTF_8)));

        ParsedAnpCsv parsed = parser.parse(archive);

        assertEquals(1, parsed.totalRecordsRead());
        assertEquals("12345678000195",
                parsed.records().get(0).values().get("cnpj da revenda"));
    }

    @Test
    void parsesZipWithLegacyCp437EntryNames() throws IOException {
        String entryName = "Preços semestrais - AUTOMOTIVOS_2026.01.csv";
        byte[] archive = zip(
                Map.of(
                        entryName,
                        (header(';') + "\r\n" + row(';'))
                                .getBytes(StandardCharsets.UTF_8)),
                java.nio.charset.Charset.forName("CP437"));

        ParsedAnpCsv parsed = parser.parse(archive);

        assertEquals(1, parsed.totalRecordsRead());
        assertEquals("12345678000195",
                parsed.records().get(0).values().get("cnpj da revenda"));
    }

    @Test
    void reportsZipWithoutCsvAndRejectsMultipleValidCsvEntries() throws IOException {
        AnpCsvParseException missingCsv = assertThrows(
                AnpCsvParseException.class,
                () -> parser.parse(zip(Map.of(
                        "readme.txt", "not a csv".getBytes(StandardCharsets.UTF_8)))));
        assertTrue(missingCsv.getMessage().contains("não contém arquivos .csv ou .tsv"));

        AnpCsvParseException ambiguous = assertThrows(
                AnpCsvParseException.class,
                () -> parser.parse(zip(Map.of(
                        "gasolina.csv", (header(';') + "\n" + row(';'))
                                .getBytes(StandardCharsets.UTF_8),
                        "diesel.csv", (header(';') + "\n" + row(';'))
                                .getBytes(StandardCharsets.UTF_8)))));
        assertTrue(ambiguous.getMessage().contains("mais de um CSV/TSV"));
        assertTrue(ambiguous.getMessage().contains("gasolina.csv"));
        assertTrue(ambiguous.getMessage().contains("diesel.csv"));
    }

    @Test
    void collectsRowsWithInvalidColumnCountAndIgnoresBlankLines() {
        String multilineRow = row(';').replace("Posto", "\"Posto\nNovo\"");
        String csv = header(';') + "\n\n" + row(';') + "\n" + multilineRow + "\ninvalid;row\n";

        ParsedAnpCsv result = parser.parse(csv.getBytes(StandardCharsets.UTF_8));

        assertEquals(3, result.totalRecordsRead());
        assertEquals(2, result.records().size());
        assertEquals(1, result.errors().size());
        assertEquals("Linha 6: quantidade de colunas diferente do cabeçalho.",
                result.errors().get(0));
    }

    @Test
    void rejectsEmptyFilesMalformedQuotesAndFilesWithoutRecords() {
        assertThrows(AnpCsvParseException.class, () -> parser.parse(new byte[0]));
        assertThrows(AnpCsvParseException.class,
                () -> parser.parse("Header".getBytes(StandardCharsets.UTF_8)));
        assertThrows(AnpCsvParseException.class,
                () -> parser.parse((header(';') + "\n\"incomplete")
                        .getBytes(StandardCharsets.UTF_8)));
        assertThrows(AnpCsvParseException.class,
                () -> parser.parse((header(';') + ";\"incomplete")
                        .getBytes(StandardCharsets.UTF_8)));
        assertThrows(AnpCsvParseException.class,
                () -> parser.parse(header(';').getBytes(StandardCharsets.UTF_8)));
        assertThrows(AnpCsvParseException.class,
                () -> parser.parse("\uFEFF".getBytes(StandardCharsets.UTF_8)));
    }

    @Test
    void rejectsMissingDuplicateAndBlankHeaderNames() {
        assertThrows(AnpCsvParseException.class,
                () -> parser.parse("one;two\n1;2".getBytes(StandardCharsets.UTF_8)));
        AnpCsvParseException unknownLayout = assertThrows(
                AnpCsvParseException.class,
                () -> parser.parse("Region;State\nNorth;AC"
                        .getBytes(StandardCharsets.UTF_8)));
        assertTrue(unknownLayout.getMessage().contains("Cabeçalhos lidos: [Region, State]"));
        assertTrue(unknownLayout.getMessage().contains("delimitador: ';'"));

        String header = header(';');
        String duplicate = header.replace("Estado - Sigla", "CNPJ da Revenda");
        assertThrows(AnpCsvParseException.class,
                () -> parser.parse(duplicate.getBytes(StandardCharsets.UTF_8)));

        String blank = header.replace("Bandeira", "");
        assertThrows(AnpCsvParseException.class,
                () -> parser.parse(blank.getBytes(StandardCharsets.UTF_8)));
    }

    private String header(char delimiter) {
        return String.join(String.valueOf(delimiter), List.of(
                "Regiao - Sigla", "Estado - Sigla", "Municipio", "Revenda",
                "CNPJ da Revenda", "Nome da Rua", "Numero Rua", "Bairro",
                "Cep", "Produto", "Data da Coleta", "Valor de Venda",
                "Unidade de Medida", "Bandeira"));
    }

    private String row(char delimiter) {
        List<String> fields = List.of(
                "SE", "SP", "Sao Paulo", "Posto", "12345678000195",
                "Rua", "1", "Centro", "01000-000", "ETANOL",
                "29/09/2026", delimiter == ',' ? "\"4,199\"" : "4,199",
                "R$/L", "Marca");
        return String.join(String.valueOf(delimiter), fields);
    }

    private byte[] utf16WithBom(
            String value,
            java.nio.charset.Charset charset,
            boolean littleEndian) {
        byte[] encoded = value.getBytes(charset);
        return ByteBuffer.allocate(encoded.length + 2)
                .put((byte) (littleEndian ? 0xFF : 0xFE))
                .put((byte) (littleEndian ? 0xFE : 0xFF))
                .put(encoded)
                .array();
    }

    private byte[] zip(Map<String, byte[]> entries) throws IOException {
        return zip(entries, StandardCharsets.UTF_8);
    }

    private byte[] zip(
            Map<String, byte[]> entries,
            java.nio.charset.Charset archiveCharset) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        try (ZipOutputStream archive = new ZipOutputStream(output, archiveCharset)) {
            for (Map.Entry<String, byte[]> entry : entries.entrySet()) {
                archive.putNextEntry(new ZipEntry(entry.getKey()));
                archive.write(entry.getValue());
                archive.closeEntry();
            }
        }
        return output.toByteArray();
    }

    private static final class CharsetForTests {
        private static final java.nio.charset.Charset WINDOWS_1252 =
                java.nio.charset.Charset.forName("windows-1252");
    }
}
