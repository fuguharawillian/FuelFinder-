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
        UUID triggeredBy,
        Integer rowsImportedFromSaoPaulo,
        Integer rowsIgnoredOtherStates,
        Integer invalidRows,
        Integer stationsCreated,
        Integer stationsUpdated,
        Integer pricesAssociated,
        Integer apiCnpjsUnmatched,
        Integer apiStationsWithoutCoordinates,
        Integer stationsWithoutCoordinates,
        Integer coordinatesUpdated,
        Integer apiPagesProcessed,
        String progressStage,
        String progressMessage,
        Integer progressPercent) {
}
