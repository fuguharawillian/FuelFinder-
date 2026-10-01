package com.fuelfinder.modules.anp.dto;

import com.fuelfinder.modules.anp.entity.ImportStatus;

import java.time.LocalDateTime;
import java.util.UUID;

public record AnpImportLogDTO(
        UUID id,
        String fileName,
        String referencePeriod,
        String sourceUrl,
        LocalDateTime importStart,
        LocalDateTime importEnd,
        Integer totalRecordsRead,
        Integer totalRecordsImported,
        ImportStatus status,
        String errorDetails,
        UUID triggeredBy) {
}
