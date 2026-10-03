package com.fuelfinder.modules.anp.service;

import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
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
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

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
    private static final Charset LEGACY_ZIP_ENTRY_CHARSET = Charset.forName("CP437");
    private static final int MAX_ARCHIVE_ENTRIES = 1_000;
    private static final int MAX_UNCOMPRESSED_ENTRY_BYTES = 100 * 1024 * 1024;
    private static final int MAX_TOTAL_ARCHIVE_BYTES = 200 * 1024 * 1024;

    public ParsedAnpCsv parse(byte[] fileContent) {
        if (fileContent == null || fileContent.length == 0) {
            throw new AnpCsvParseException("O arquivo ANP está vazio.");
        }
        if (isZipArchive(fileContent)) {
            return parseZipArchive(fileContent);
        }
        return parseCsv(fileContent);
    }

    private ParsedAnpCsv parseCsv(byte[] fileContent) {
        String content = decode(fileContent);
        if (!content.isEmpty() && content.charAt(0) == '\uFEFF') {
            content = content.substring(1);
        }
        if (content.isBlank()) {
            throw new AnpCsvParseException("O arquivo ANP não contém cabeçalho.");
        }
        char delimiter = detectDelimiter(content);
        List<CsvRecord> parsedRecords = parseRecords(content, delimiter);

        List<String> rawHeaders = parsedRecords.get(0).fields();
        List<String> headers = rawHeaders.stream()
                .map(AnpCsvParser::normalizeHeader)
                .toList();
        validateHeaders(headers, rawHeaders, delimiter);

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
                errors.add("Linha " + record.startLine()
                        + ": quantidade de colunas diferente do cabeçalho.");
                continue;
            }
            Map<String, String> values = new HashMap<>();
            for (int column = 0; column < headers.size(); column++) {
                values.put(headers.get(column), record.fields().get(column).trim());
            }
            records.add(new AnpCsvRow(record.startLine(), Map.copyOf(values)));
        }
        if (totalRecordsRead == 0) {
            throw new AnpCsvParseException("O arquivo ANP não contém registros de dados.");
        }
        return new ParsedAnpCsv(
                totalRecordsRead,
                List.copyOf(records),
                List.copyOf(errors));
    }

    private ParsedAnpCsv parseZipArchive(byte[] archive) {
        ParsedAnpCsv matchingCsv = null;
        String matchingEntry = null;
        String firstCsvEntry = null;
        String firstCsvError = null;
        int entryCount = 0;
        int totalArchiveBytes = 0;

        try (ZipInputStream zip = new ZipInputStream(
                new ByteArrayInputStream(archive),
                LEGACY_ZIP_ENTRY_CHARSET)) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                entryCount++;
                if (entryCount > MAX_ARCHIVE_ENTRIES) {
                    throw new AnpCsvParseException(
                            "O ZIP da ANP excede o limite de " + MAX_ARCHIVE_ENTRIES + " arquivos.");
                }
                boolean csvEntry = !entry.isDirectory() && isCsvEntry(entry.getName());
                String entryName = safeEntryName(entry.getName());
                EntryContent content = readEntry(zip, entryName, csvEntry);
                totalArchiveBytes += content.uncompressedBytes();
                if (totalArchiveBytes > MAX_TOTAL_ARCHIVE_BYTES) {
                    throw new AnpCsvParseException(
                            "O conteúdo descompactado do ZIP excede o limite de 200 MB.");
                }
                zip.closeEntry();
                if (!csvEntry) continue;
                if (firstCsvEntry == null) firstCsvEntry = entryName;

                ParsedAnpCsv parsed = null;
                try {
                    parsed = parseCsv(content.bytes());
                } catch (AnpCsvParseException exception) {
                    if (firstCsvError == null) firstCsvError = exception.getMessage();
                }
                if (parsed != null) {
                    if (matchingCsv != null) {
                        throw new AnpCsvParseException(
                                "O ZIP contém mais de um CSV/TSV com layout ANP válido: "
                                        + matchingEntry + " e " + entryName
                                        + ". Envie um arquivo com apenas um CSV elegível.");
                    }
                    matchingCsv = parsed;
                    matchingEntry = entryName;
                }
            }
        } catch (IOException exception) {
            throw new AnpCsvParseException("Não foi possível ler o arquivo ZIP da ANP.");
        }

        if (matchingCsv != null) return matchingCsv;
        if (firstCsvEntry == null) {
            throw new AnpCsvParseException(
                    "O ZIP da ANP não contém arquivos .csv ou .tsv para importar.");
        }
        throw new AnpCsvParseException(
                "Nenhum CSV/TSV do ZIP possui um layout ANP válido. Arquivo examinado: "
                        + firstCsvEntry + ". " + firstCsvError);
    }

    private EntryContent readEntry(
            ZipInputStream zip,
            String entryName,
            boolean retainContent) throws IOException {
        ByteArrayOutputStream output = retainContent ? new ByteArrayOutputStream() : null;
        byte[] buffer = new byte[8192];
        int totalBytes = 0;
        int read;
        while ((read = zip.read(buffer)) != -1) {
            totalBytes += read;
            if (totalBytes > MAX_UNCOMPRESSED_ENTRY_BYTES) {
                throw new AnpCsvParseException(
                        "A entrada " + entryName
                                + " excede o limite descompactado de 100 MB.");
            }
            if (output != null) {
                output.write(buffer, 0, read);
            }
        }
        return new EntryContent(output == null ? null : output.toByteArray(), totalBytes);
    }

    private boolean isZipArchive(byte[] content) {
        return content.length >= 4
                && content[0] == 'P'
                && content[1] == 'K'
                && ((content[2] == 3 && content[3] == 4)
                        || (content[2] == 5 && content[3] == 6)
                        || (content[2] == 7 && content[3] == 8));
    }

    private boolean isCsvEntry(String name) {
        String normalized = name.toLowerCase(Locale.ROOT);
        return normalized.endsWith(".csv") || normalized.endsWith(".tsv");
    }

    private String safeEntryName(String name) {
        String basename = name.replace('\\', '/');
        basename = basename.substring(basename.lastIndexOf('/') + 1);
        return basename.replaceAll("\\p{C}", "?");
    }

    private String decode(byte[] content) {
        if (hasPrefix(content, 0xFF, 0xFE)) {
            return StandardCharsets.UTF_16LE.decode(ByteBuffer.wrap(content, 2, content.length - 2))
                    .toString();
        }
        if (hasPrefix(content, 0xFE, 0xFF)) {
            return StandardCharsets.UTF_16BE.decode(ByteBuffer.wrap(content, 2, content.length - 2))
                    .toString();
        }
        try {
            return StandardCharsets.UTF_8.newDecoder().decode(ByteBuffer.wrap(content)).toString();
        } catch (CharacterCodingException exception) {
            return new String(content, Charset.forName("windows-1252"));
        }
    }

    private boolean hasPrefix(byte[] content, int first, int second) {
        return content.length >= 2
                && Byte.toUnsignedInt(content[0]) == first
                && Byte.toUnsignedInt(content[1]) == second;
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
                records.add(new CsvRecord(recordStartLine, List.copyOf(fields)));
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
            records.add(new CsvRecord(recordStartLine, List.copyOf(fields)));
        }
        return records;
    }

    private void validateHeaders(
            List<String> headers,
            List<String> rawHeaders,
            char delimiter) {
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
                            + String.join(", ", missingHeaders)
                            + ". Cabeçalhos lidos: "
                            + rawHeaders.stream()
                                    .map(AnpCsvParser::describeHeader)
                                    .toList()
                            + "; delimitador: " + describeDelimiter(delimiter) + ".");
        }
    }

    private static String describeHeader(String value) {
        return value.replaceAll("\\p{C}", "?").trim();
    }

    private String describeDelimiter(char delimiter) {
        return delimiter == '\t' ? "TAB" : "'" + delimiter + "'";
    }

    static String normalizeHeader(String value) {
        String normalized = Normalizer.normalize(value, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", " ")
                .trim();
        return normalized;
    }

    private record CsvRecord(int startLine, List<String> fields) {
    }

    private record EntryContent(byte[] bytes, int uncompressedBytes) {
    }
}
