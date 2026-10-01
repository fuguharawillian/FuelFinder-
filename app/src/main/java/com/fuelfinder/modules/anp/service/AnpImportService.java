package com.fuelfinder.modules.anp.service;

import com.fuelfinder.common.exception.ExternalServiceException;
import com.fuelfinder.modules.anp.dto.AnpImportLogDTO;
import com.fuelfinder.modules.anp.entity.AnpImportLog;
import com.fuelfinder.modules.anp.entity.ImportStatus;
import com.fuelfinder.modules.anp.repository.AnpImportLogRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class AnpImportService {

    private static final Logger LOGGER = LoggerFactory.getLogger(AnpImportService.class);
    private static final int MAX_ERROR_DETAILS_LENGTH = 10_000;
    private final AnpCsvDownloader csvDownloader;
    private final AnpCsvParser csvParser;
    private final AnpImportProcessor importProcessor;
    private final AnpImportLogRepository importLogRepository;
    private final Clock clock;

    public AnpImportService(
            AnpCsvDownloader csvDownloader,
            AnpCsvParser csvParser,
            AnpImportProcessor importProcessor,
            AnpImportLogRepository importLogRepository,
            Clock clock) {
        this.csvDownloader = csvDownloader;
        this.csvParser = csvParser;
        this.importProcessor = importProcessor;
        this.importLogRepository = importLogRepository;
        this.clock = clock;
    }

    public AnpImportLog executeImport(
            String sourceUrl,
            String referencePeriod,
            UUID triggeredBy) {
        URI sourceUri = csvDownloader.validateSourceUrl(sourceUrl);
        String safeSourceUrl = csvDownloader.sanitizeSourceUrl(sourceUri);
        AnpImportLog importLog = importLogRepository.saveAndFlush(new AnpImportLog(
                extractFileName(sourceUri),
                referencePeriod.trim(),
                safeSourceUrl,
                LocalDateTime.now(clock),
                triggeredBy));
        try {
            ParsedAnpCsv csv = csvParser.parse(csvDownloader.download(sourceUrl));
            importLog.setTotalRecordsRead(csv.totalRecordsRead());
            AnpImportResult result = importProcessor.process(csv);
            importLog.setTotalRecordsImported(result.totalRecordsImported());
            importLog.setStatus(result.errors().isEmpty()
                    ? ImportStatus.SUCCESS : ImportStatus.PARTIAL);
            importLog.setErrorDetails(joinErrors(result.errors()));
        } catch (ExternalServiceException | AnpCsvParseException exception) {
            markFailed(importLog, exception.getMessage());
            return importLogRepository.save(importLog);
        } catch (RuntimeException exception) {
            markFailed(
                    importLog,
                    "A importação foi abortada por uma falha interna; os dados anteriores foram preservados.");
            importLogRepository.save(importLog);
            LOGGER.error("ANP import {} failed; previous data was preserved.", importLog.getId(), exception);
            throw exception;
        }
        importLog.setImportEnd(LocalDateTime.now(clock));
        return importLogRepository.save(importLog);
    }

    public List<AnpImportLog> listImports() {
        return importLogRepository.findAllByOrderByImportStartDesc();
    }

    public AnpImportLog getImport(UUID importId) {
        return importLogRepository.findById(importId)
                .orElseThrow(() -> new com.fuelfinder.common.exception.ResourceNotFoundException(
                        "Log de importação não encontrado."));
    }

    public AnpImportLogDTO toDTO(AnpImportLog log) {
        return new AnpImportLogDTO(
                log.getId(),
                log.getFileName(),
                log.getReferencePeriod(),
                log.getSourceUrl(),
                log.getImportStart(),
                log.getImportEnd(),
                log.getTotalRecordsRead(),
                log.getTotalRecordsImported(),
                log.getStatus(),
                log.getErrorDetails(),
                log.getTriggeredBy());
    }

    private void markFailed(AnpImportLog importLog, String message) {
        importLog.setStatus(ImportStatus.FAILED);
        importLog.setErrorDetails(truncate(message));
        importLog.setImportEnd(LocalDateTime.now(clock));
    }

    private String joinErrors(List<String> errors) {
        if (errors.isEmpty()) {
            return null;
        }
        String separator = System.lineSeparator();
        String details = errors.stream().collect(Collectors.joining(separator));
        if (details.length() <= MAX_ERROR_DETAILS_LENGTH) {
            return details;
        }
        String notice = separator + "[Detalhes truncados pelo limite de "
                + MAX_ERROR_DETAILS_LENGTH + " caracteres.]";
        int prefixLimit = MAX_ERROR_DETAILS_LENGTH - notice.length();
        int lastCompleteLine = details.lastIndexOf(separator, prefixLimit);
        int end = lastCompleteLine > 0 ? lastCompleteLine : prefixLimit;
        return details.substring(0, end) + notice;
    }

    private String extractFileName(URI sourceUri) {
        String path = sourceUri.getPath();
        String fileName = path == null || path.isBlank()
                ? "anp-import.csv"
                : path.substring(path.lastIndexOf('/') + 1);
        if (fileName.isBlank()) {
            return "anp-import.csv";
        }
        return truncate(fileName, 255);
    }

    private String truncate(String value) {
        return truncate(value, MAX_ERROR_DETAILS_LENGTH);
    }

    private String truncate(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength);
    }
}
