package com.fuelfinder.modules.anp.service;

import com.fuelfinder.common.exception.BusinessException;
import com.fuelfinder.common.exception.ExternalServiceException;
import com.fuelfinder.common.exception.ResourceNotFoundException;
import com.fuelfinder.modules.anp.entity.AnpImportLog;
import com.fuelfinder.modules.anp.entity.ImportStatus;
import com.fuelfinder.modules.anp.repository.AnpImportLogRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AnpImportServiceTest {

    private static final String SOURCE_URL = "https://www.gov.br/anp.csv?token=secret";
    private static final URI SOURCE_URI = URI.create(SOURCE_URL);
    private static final UUID TRIGGERED_BY = UUID.randomUUID();

    @Mock
    private AnpCsvDownloader downloader;
    @Mock
    private AnpImportProcessor processor;
    @Mock
    private AnpImportLogRepository repository;
    private AnpCsvParser parser;
    private AnpImportService service;

    @BeforeEach
    void setUp() {
        parser = new AnpCsvParser();
        service = new AnpImportService(
                downloader,
                parser,
                processor,
                repository,
                Clock.fixed(Instant.parse("2026-09-29T12:00:00Z"), ZoneOffset.UTC));
        lenient().when(downloader.validateSourceUrl(SOURCE_URL)).thenReturn(SOURCE_URI);
        lenient().when(downloader.sanitizeSourceUrl(SOURCE_URI))
                .thenReturn("https://www.gov.br/anp.csv");
        lenient().when(repository.saveAndFlush(any(AnpImportLog.class))).thenAnswer(invocation -> {
            AnpImportLog log = invocation.getArgument(0);
            log.setId(UUID.randomUUID());
            return log;
        });
        lenient().when(repository.save(any(AnpImportLog.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void savesSuccessfulAuditAndSanitizesSourceQueryBeforeReturningDto() {
        byte[] csv = validCsv();
        when(downloader.download(SOURCE_URL)).thenReturn(csv);
        when(processor.process(any(ParsedAnpCsv.class)))
                .thenReturn(new AnpImportResult(1, 1, List.of()));

        AnpImportLog result = service.executeImport(SOURCE_URL, "2026-S1", TRIGGERED_BY);
        var dto = service.toDTO(result);

        assertEquals(ImportStatus.SUCCESS, result.getStatus());
        assertEquals(1, result.getTotalRecordsRead());
        assertEquals(1, result.getTotalRecordsImported());
        assertEquals("https://www.gov.br/anp.csv", result.getSourceUrl());
        assertEquals(TRIGGERED_BY, dto.triggeredBy());
        assertNotNull(result.getImportEnd());
        verify(processor).process(any(ParsedAnpCsv.class));
    }

    @Test
    void recordsPartialImportErrorsAndFailedDownloadOrInvalidCsv() {
        doReturn(validCsv()).when(downloader).download(SOURCE_URL);
        when(processor.process(any(ParsedAnpCsv.class)))
                .thenReturn(new AnpImportResult(1, 0, List.of("Geoapify indisponível.")));
        AnpImportLog partial = service.executeImport(SOURCE_URL, "2026-S1", TRIGGERED_BY);
        assertEquals(ImportStatus.PARTIAL, partial.getStatus());
        assertEquals("Geoapify indisponível.", partial.getErrorDetails());

        doThrow(new ExternalServiceException("download failed", new RuntimeException()))
                .when(downloader).download(SOURCE_URL);
        AnpImportLog failedDownload =
                service.executeImport(SOURCE_URL, "2026-S1", TRIGGERED_BY);
        assertEquals(ImportStatus.FAILED, failedDownload.getStatus());
        assertEquals("download failed", failedDownload.getErrorDetails());

        doReturn("invalid;headers".getBytes(StandardCharsets.UTF_8))
                .when(downloader).download(SOURCE_URL);
        AnpImportLog failedLayout =
                service.executeImport(SOURCE_URL, "2026-S1", TRIGGERED_BY);
        assertEquals(ImportStatus.FAILED, failedLayout.getStatus());
        assertNotNull(failedLayout.getImportEnd());
    }

    @Test
    void marksUnexpectedTransactionalFailureAndRethrowsIt() {
        doReturn(validCsv()).when(downloader).download(SOURCE_URL);
        when(processor.process(any(ParsedAnpCsv.class)))
                .thenThrow(new IllegalStateException("database unavailable"));

        assertThrows(IllegalStateException.class,
                () -> service.executeImport(SOURCE_URL, "2026-S1", TRIGGERED_BY));

        verify(repository).saveAndFlush(any(AnpImportLog.class));
        verify(repository).save(any(AnpImportLog.class));
    }

    @Test
    void validatesSourceBeforeCreatingAuditAndHandlesImportLogQueries() {
        when(downloader.validateSourceUrl("https://example.com/anp.csv"))
                .thenThrow(new BusinessException("invalid source host"));
        assertThrows(BusinessException.class, () ->
                service.executeImport("https://example.com/anp.csv", "2026-S1", TRIGGERED_BY));
        verify(repository, never()).saveAndFlush(any(AnpImportLog.class));

        when(repository.findAllByOrderByImportStartDesc()).thenReturn(List.of());
        assertEquals(List.of(), service.listImports());
        assertThrows(ResourceNotFoundException.class,
                () -> service.getImport(UUID.randomUUID()));
    }

    @Test
    void truncatesLongAuditErrorsAndUsesDefaultFileNameForRootUrls() {
        URI rootUrl = URI.create("https://www.gov.br/");
        when(downloader.validateSourceUrl("https://www.gov.br/")).thenReturn(rootUrl);
        when(downloader.sanitizeSourceUrl(rootUrl)).thenReturn("https://www.gov.br/");
        doReturn(validCsv()).when(downloader).download("https://www.gov.br/");
        when(processor.process(any(ParsedAnpCsv.class))).thenReturn(new AnpImportResult(
                1, 0, List.of("x".repeat(1_010_000))));

        AnpImportLog result = service.executeImport(
                "https://www.gov.br/", "2026-S1", TRIGGERED_BY);

        assertEquals("anp-import.csv", result.getFileName());
        assertEquals(1_000_000, result.getErrorDetails().length());
        assertTrue(result.getErrorDetails().endsWith(
                "[Detalhes truncados pelo limite de 1000000 caracteres.]"));
        assertEquals(ImportStatus.PARTIAL, result.getStatus());
    }

    @Test
    void usesDefaultFileNameWhenSourceUrlHasNoPath() {
        URI rootUrl = URI.create("https://www.gov.br");
        when(downloader.validateSourceUrl("https://www.gov.br")).thenReturn(rootUrl);
        when(downloader.sanitizeSourceUrl(rootUrl)).thenReturn("https://www.gov.br");
        doReturn(validCsv()).when(downloader).download("https://www.gov.br");
        when(processor.process(any(ParsedAnpCsv.class)))
                .thenReturn(new AnpImportResult(1, 1, List.of()));

        AnpImportLog result = service.executeImport(
                "https://www.gov.br", "2026-S1", TRIGGERED_BY);

        assertEquals("anp-import.csv", result.getFileName());
        assertEquals(ImportStatus.SUCCESS, result.getStatus());
    }

    @Test
    void preservesMissingErrorMessageAsNullInFailedAudit() {
        doThrow(new ExternalServiceException(null, new RuntimeException()))
                .when(downloader).download(SOURCE_URL);

        AnpImportLog result = service.executeImport(SOURCE_URL, "2026-S1", TRIGGERED_BY);

        assertEquals(ImportStatus.FAILED, result.getStatus());
        assertNull(result.getErrorDetails());
    }

    private byte[] validCsv() {
        return """
                Regiao - Sigla;Estado - Sigla;Municipio;Revenda;CNPJ da Revenda;Nome da Rua;Numero Rua;Bairro;Cep;Produto;Data da Coleta;Valor de Venda;Unidade de Medida;Bandeira
                SE;SP;Sao Paulo;Posto;12345678000195;Rua;1;Centro;01000-000;ETANOL;29/09/2026;4,199;R$/L;Marca
                """.getBytes(StandardCharsets.UTF_8);
    }
}
