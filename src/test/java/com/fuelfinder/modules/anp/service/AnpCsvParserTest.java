package com.fuelfinder.modules.anp.service;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

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
    void collectsRowsWithInvalidColumnCountAndIgnoresBlankLines() {
        String csv = header(';') + "\n\n" + row(';') + "\ninvalid;row\n";

        ParsedAnpCsv result = parser.parse(csv.getBytes(StandardCharsets.UTF_8));

        assertEquals(2, result.totalRecordsRead());
        assertEquals(1, result.records().size());
        assertEquals(1, result.errors().size());
        assertEquals("Registro 4: quantidade de colunas diferente do cabeçalho.",
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

    private static final class CharsetForTests {
        private static final java.nio.charset.Charset WINDOWS_1252 =
                java.nio.charset.Charset.forName("windows-1252");
    }
}
