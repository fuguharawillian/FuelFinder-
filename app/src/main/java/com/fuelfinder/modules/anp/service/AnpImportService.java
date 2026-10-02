package com.fuelfinder.modules.anp.service;

import com.fuelfinder.common.exception.ExternalServiceException;
import com.fuelfinder.modules.anp.dto.AnpImportLogDTO;
import com.fuelfinder.modules.anp.entity.AnpImportLog;
import com.fuelfinder.modules.anp.entity.ImportStatus;
import com.fuelfinder.modules.anp.repository.AnpImportLogRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.task.TaskExecutor;
import org.springframework.core.task.TaskRejectedException;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

@Service
public class AnpImportService {

    private static final Logger LOGGER = LoggerFactory.getLogger(AnpImportService.class);
    private static final int MAX_ERROR_DETAILS_LENGTH = 1_000_000;
    private final AnpCsvDownloader csvDownloader;
    private final AnpCsvParser csvParser;
    private final AnpImportProcessor importProcessor;
    private final AnpRetailerImportService retailerImportService;
    private final AnpImportLogRepository importLogRepository;
    private final AnpImportProgressUpdater progressUpdater;
    private final TaskExecutor taskExecutor;
    private final Clock clock;

    public AnpImportService(
            AnpCsvDownloader csvDownloader,
            AnpCsvParser csvParser,
            AnpImportProcessor importProcessor,
            AnpRetailerImportService retailerImportService,
            AnpImportLogRepository importLogRepository,
            AnpImportProgressUpdater progressUpdater,
            @Qualifier("anpImportExecutor") TaskExecutor taskExecutor,
            Clock clock) {
        this.csvDownloader = csvDownloader;
        this.csvParser = csvParser;
        this.importProcessor = importProcessor;
        this.retailerImportService = retailerImportService;
        this.importLogRepository = importLogRepository;
        this.progressUpdater = progressUpdater;
        this.taskExecutor = taskExecutor;
        this.clock = clock;
    }

    public AnpImportLog startImport(
            String sourceUrl,
            String referencePeriod,
            UUID triggeredBy) {
        AnpImportLog importLog = createImportLog(sourceUrl, referencePeriod, triggeredBy);
        try {
            taskExecutor.execute(() -> runImport(importLog.getId(), sourceUrl, false));
        } catch (TaskRejectedException exception) {
            markFailed(
                    importLog,
                    "A fila de importações ANP está cheia; tente novamente mais tarde.");
            return importLogRepository.save(importLog);
        }
        return importLog;
    }

    public AnpImportLog executeImport(
            String sourceUrl,
            String referencePeriod,
            UUID triggeredBy) {
        AnpImportLog importLog = createImportLog(sourceUrl, referencePeriod, triggeredBy);
        runImport(importLog.getId(), sourceUrl, true);
        return getImport(importLog.getId());
    }

    private AnpImportLog createImportLog(
            String sourceUrl,
            String referencePeriod,
            UUID triggeredBy) {
        URI sourceUri = csvDownloader.validateSourceUrl(sourceUrl);
        String safeSourceUrl = csvDownloader.sanitizeSourceUrl(sourceUri);
        AnpImportLog importLog = new AnpImportLog(
                extractFileName(sourceUri),
                referencePeriod.trim(),
                safeSourceUrl,
                LocalDateTime.now(clock),
                triggeredBy);
        importLog.setStatus(ImportStatus.RUNNING);
        importLog.setProgressStage("CSV_DOWNLOAD");
        importLog.setProgressMessage("Baixando o arquivo CSV da ANP.");
        return importLogRepository.saveAndFlush(importLog);
    }

    private void runImport(UUID importId, String sourceUrl, boolean propagateInternalFailure) {
        ParsedAnpCsv csv;
        try {
            progressUpdater.update(
                    importId,
                    "CSV_DOWNLOAD",
                    "Baixando o arquivo CSV da ANP.",
                    null);
            csv = csvParser.parse(csvDownloader.download(sourceUrl));
        } catch (ExternalServiceException | AnpCsvParseException exception) {
            markFailed(importId, exception.getMessage());
            return;
        }

        AnpImportResult csvResult;
        try {
            progressUpdater.update(
                    importId,
                    "CSV_PROCESSING",
                    "Filtrando SP e associando postos e preços do CSV.",
                    0);
            csvResult = importProcessor.process(csv, (processed, total) -> {
                int percent = total == 0
                        ? 50
                        : Math.min(50, (int) ((long) processed * 50 / total));
                progressUpdater.update(
                        importId,
                        "CSV_PROCESSING",
                        "Processadas " + processed + " de " + total + " linhas SP do CSV.",
                        percent);
            });
        } catch (RuntimeException exception) {
            markFailed(
                    importId,
                    "A importação do CSV foi abortada por uma falha interna; os dados anteriores foram preservados.");
            LOGGER.error("ANP CSV import {} failed; previous data was preserved.", importId, exception);
            if (propagateInternalFailure) {
                throw exception;
            }
            return;
        }

        List<String> errors = new ArrayList<>(csvResult.errors());
        AtomicInteger processedApiPages = new AtomicInteger();
        try {
            persistCsvSummary(importId, csvResult);
            progressUpdater.update(
                    importId,
                    "ANP_API",
                    "Consultando todas as páginas da API de revendedores da ANP.",
                    null);
            try {
                AnpCoordinateUpdateResult coordinateResult =
                        retailerImportService.updateCoordinates(
                                csvResult.importedCnpjs(),
                                pagesProcessed -> {
                                    processedApiPages.set(pagesProcessed);
                                    progressUpdater.update(
                                            importId,
                                            "ANP_API",
                                            "Página " + pagesProcessed
                                                    + " consultada; total de páginas desconhecido.",
                                            null);
                                });
                errors.addAll(coordinateResult.errors());
                persistCoordinateSummary(importId, coordinateResult);
            } catch (RuntimeException exception) {
                AnpImportLog log = getImport(importId);
                log.setApiPagesProcessed(processedApiPages.get());
                importLogRepository.save(log);
                errors.add("Falha na atualização de coordenadas pela API da ANP: "
                        + safeExceptionMessage(exception)
                        + " Os postos e preços do CSV foram preservados; uma nova importação pode retomar o processo.");
                LOGGER.error("ANP coordinate enrichment failed for import {}.", importId, exception);
            }
        } catch (RuntimeException exception) {
            errors.add("Os dados do CSV foram importados, mas houve falha ao registrar ou executar "
                    + "a etapa de coordenadas: "
                    + safeExceptionMessage(exception)
                    + " Os postos e preços do CSV foram preservados; uma nova importação pode retomar o processo.");
            LOGGER.error("ANP post-CSV processing failed for import {}.", importId, exception);
        }

        AnpImportLog importLog = getImport(importId);
        importLog.setStatus(errors.isEmpty() ? ImportStatus.SUCCESS : ImportStatus.PARTIAL);
        importLog.setErrorDetails(joinErrors(errors));
        importLog.setImportEnd(LocalDateTime.now(clock));
        importLog.setProgressStage("COMPLETED");
        importLog.setProgressMessage(errors.isEmpty()
                ? "Importação do CSV e atualização das coordenadas concluídas."
                : "Importação concluída com divergências; consulte o relatório.");
        importLog.setProgressPercent(100);
        importLogRepository.save(importLog);
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
                log.getTriggeredBy(),
                log.getRowsImportedFromSaoPaulo(),
                log.getRowsIgnoredOtherStates(),
                log.getInvalidRows(),
                log.getStationsCreated(),
                log.getStationsUpdated(),
                log.getPricesAssociated(),
                log.getApiCnpjsUnmatched(),
                log.getApiStationsWithoutCoordinates(),
                log.getStationsWithoutCoordinates(),
                log.getCoordinatesUpdated(),
                log.getApiPagesProcessed(),
                log.getProgressStage(),
                log.getProgressMessage(),
                log.getProgressPercent());
    }

    private void persistCsvSummary(UUID importId, AnpImportResult result) {
        AnpImportLog log = getImport(importId);
        log.setTotalRecordsRead(result.totalRecordsRead());
        log.setTotalRecordsImported(result.totalRecordsImported());
        log.setRowsImportedFromSaoPaulo(result.rowsImportedFromSaoPaulo());
        log.setRowsIgnoredOtherStates(result.rowsIgnoredOtherStates());
        log.setInvalidRows(result.invalidRows());
        log.setStationsCreated(result.stationsCreated());
        log.setStationsUpdated(result.stationsUpdated());
        log.setStationsWithoutCoordinates(result.stationsWithoutCoordinates());
        log.setPricesAssociated(result.pricesAssociated());
        importLogRepository.save(log);
    }

    private void persistCoordinateSummary(UUID importId, AnpCoordinateUpdateResult result) {
        AnpImportLog log = getImport(importId);
        log.setApiPagesProcessed(result.pagesProcessed());
        log.setApiCnpjsUnmatched(result.apiCnpjsUnmatched());
        log.setApiStationsWithoutCoordinates(result.apiStationsWithoutCoordinates());
        log.setStationsWithoutCoordinates(result.stationsWithoutCoordinates());
        log.setCoordinatesUpdated(result.coordinatesUpdated());
        importLogRepository.save(log);
    }

    private void markFailed(UUID importId, String message) {
        AnpImportLog importLog = getImport(importId);
        markFailed(importLog, message);
        importLogRepository.save(importLog);
    }

    private void markFailed(AnpImportLog importLog, String message) {
        importLog.setStatus(ImportStatus.FAILED);
        importLog.setErrorDetails(truncate(message));
        importLog.setImportEnd(LocalDateTime.now(clock));
        importLog.setProgressStage("FAILED");
        importLog.setProgressMessage("A importação falhou antes de concluir as etapas.");
        importLog.setProgressPercent(null);
    }

    private String safeExceptionMessage(RuntimeException exception) {
        String message = exception.getMessage();
        return message == null || message.isBlank()
                ? "erro inesperado"
                : truncate(message, 500);
    }

    private String joinErrors(List<String> errors) {
        if (errors.isEmpty()) {
            return null;
        }
        String separator = System.lineSeparator();
        String details = new LinkedHashSet<>(errors).stream()
                .collect(Collectors.joining(separator));
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
