package com.fuelfinder.modules.anp.service;

import org.springframework.stereotype.Component;

import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Component
public class AnpCsvParser {

    private static final Set<String> REQUIRED_HEADERS = Set.of(
            "cnpj da revenda",
            "revenda",
            "municipio",
            "estado sigla",
            "produto",
            "data da coleta",
            "valor de venda",
            "unidade de medida");
    private static final List<Character> SUPPORTED_DELIMITERS = List.of(';', '\t', ',');

    public ParsedAnpCsv parse(byte[] fileContent) {
        if (fileContent == null || fileContent.length == 0) {
            throw new AnpCsvParseException("O arquivo ANP está vazio.");
        }

        String content = decode(fileContent);
        if (!content.isEmpty() && content.charAt(0) == '\uFEFF') {
            content = content.substring(1);
        }
        if (content.isBlank()) {
            throw new AnpCsvParseException("O arquivo ANP não contém cabeçalho.");
        }
        List<CsvRecord> parsedRecords = parseRecords(content, detectDelimiter(content));

        List<String> headers = parsedRecords.get(0).fields().stream()
                .map(AnpCsvParser::normalizeHeader)
                .toList();
        validateHeaders(headers);

        List<AnpCsvRow> records = new ArrayList<>();
        List<String> errors = new ArrayList<>();
        int totalRecordsRead = 0;
        for (int index = 1; index < parsedRecords.size(); index++) {
            CsvRecord record = parsedRecords.get(index);
            if (record.fields().stream().allMatch(String::isBlank)) {
                continue;
            }
            totalRecordsRead++;
            if (record.fields().size() != headers.size()) {
                errors.add("Registro " + record.recordNumber()
                        + ": quantidade de colunas diferente do cabeçalho.");
                continue;
            }
            Map<String, String> values = new HashMap<>();
            for (int column = 0; column < headers.size(); column++) {
                values.put(headers.get(column), record.fields().get(column).trim());
            }
            records.add(new AnpCsvRow(record.recordNumber(), Map.copyOf(values)));
        }
        if (totalRecordsRead == 0) {
            throw new AnpCsvParseException("O arquivo ANP não contém registros de dados.");
        }
        return new ParsedAnpCsv(
                totalRecordsRead,
                List.copyOf(records),
                List.copyOf(errors));
    }

    private String decode(byte[] content) {
        try {
            return StandardCharsets.UTF_8.newDecoder().decode(ByteBuffer.wrap(content)).toString();
        } catch (CharacterCodingException exception) {
            return new String(content, Charset.forName("windows-1252"));
        }
    }

    private char detectDelimiter(String content) {
        int end = firstRecordEnd(content);
        String header = content.substring(0, end);
        char bestDelimiter = ';';
        int maximumCount = 0;
        for (char delimiter : SUPPORTED_DELIMITERS) {
            int count = countOutsideQuotes(header, delimiter);
            if (count > maximumCount) {
                bestDelimiter = delimiter;
                maximumCount = count;
            }
        }
        if (maximumCount == 0) {
            throw new AnpCsvParseException("Não foi possível identificar o separador do CSV ANP.");
        }
        return bestDelimiter;
    }

    private int firstRecordEnd(String content) {
        boolean insideQuotes = false;
        for (int index = 0; index < content.length(); index++) {
            char current = content.charAt(index);
            if (current == '"') {
                if (insideQuotes && index + 1 < content.length()
                        && content.charAt(index + 1) == '"') {
                    index++;
                } else {
                    insideQuotes = !insideQuotes;
                }
            } else if ((current == '\n' || current == '\r') && !insideQuotes) {
                return index;
            }
        }
        if (insideQuotes) {
            throw new AnpCsvParseException("O cabeçalho do CSV contém aspas sem fechamento.");
        }
        return content.length();
    }

    private int countOutsideQuotes(String value, char delimiter) {
        boolean insideQuotes = false;
        int count = 0;
        for (int index = 0; index < value.length(); index++) {
            char current = value.charAt(index);
            if (current == '"') {
                if (insideQuotes && index + 1 < value.length()
                        && value.charAt(index + 1) == '"') {
                    index++;
                } else {
                    insideQuotes = !insideQuotes;
                }
            } else if (current == delimiter && !insideQuotes) {
                count++;
            }
        }
        return count;
    }

    private List<CsvRecord> parseRecords(String content, char delimiter) {
        List<CsvRecord> records = new ArrayList<>();
        List<String> fields = new ArrayList<>();
        StringBuilder field = new StringBuilder();
        boolean insideQuotes = false;
        int lineNumber = 1;
        int recordNumber = 1;
        int recordStartLine = 1;

        for (int index = 0; index < content.length(); index++) {
            char current = content.charAt(index);
            if (insideQuotes) {
                if (current == '"' && index + 1 < content.length()
                        && content.charAt(index + 1) == '"') {
                    field.append('"');
                    index++;
                } else if (current == '"') {
                    insideQuotes = false;
                } else {
                    field.append(current);
                    if (current == '\n') {
                        lineNumber++;
                    }
                }
            } else if (current == '"' && field.isEmpty()) {
                insideQuotes = true;
            } else if (current == delimiter) {
                fields.add(field.toString());
                field.setLength(0);
            } else if (current == '\n' || current == '\r') {
                fields.add(field.toString());
                field.setLength(0);
                records.add(new CsvRecord(recordNumber++, recordStartLine, List.copyOf(fields)));
                fields.clear();
                if (current == '\r' && index + 1 < content.length()
                        && content.charAt(index + 1) == '\n') {
                    index++;
                }
                lineNumber++;
                recordStartLine = lineNumber;
            } else {
                field.append(current);
            }
        }
        if (insideQuotes) {
            throw new AnpCsvParseException(
                    "O CSV contém um registro com aspas sem fechamento.");
        }
        if (!fields.isEmpty() || !field.isEmpty()) {
            fields.add(field.toString());
            records.add(new CsvRecord(recordNumber, recordStartLine, List.copyOf(fields)));
        }
        return records;
    }

    private void validateHeaders(List<String> headers) {
        if (headers.stream().anyMatch(String::isBlank)) {
            throw new AnpCsvParseException("O cabeçalho do CSV contém coluna sem nome.");
        }
        Set<String> uniqueHeaders = new HashSet<>(headers);
        if (uniqueHeaders.size() != headers.size()) {
            throw new AnpCsvParseException("O cabeçalho do CSV contém colunas duplicadas.");
        }
        Set<String> missingHeaders = new HashSet<>(REQUIRED_HEADERS);
        missingHeaders.removeAll(uniqueHeaders);
        if (!missingHeaders.isEmpty()) {
            throw new AnpCsvParseException(
                    "Layout ANP inválido; colunas obrigatórias ausentes: "
                            + String.join(", ", missingHeaders) + ".");
        }
    }

    static String normalizeHeader(String value) {
        String normalized = Normalizer.normalize(value, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", " ")
                .trim();
        return normalized;
    }

    private record CsvRecord(int recordNumber, int startLine, List<String> fields) {
    }
}
