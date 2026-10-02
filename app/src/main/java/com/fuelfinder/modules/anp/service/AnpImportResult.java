package com.fuelfinder.modules.anp.service;

import java.util.List;
import java.util.Set;

public record AnpImportResult(
        int totalRecordsRead,
        int totalRecordsImported,
        int rowsImportedFromSaoPaulo,
        int rowsIgnoredOtherStates,
        int invalidRows,
        int stationsCreated,
        int stationsUpdated,
        int stationsWithoutCoordinates,
        int pricesAssociated,
        Set<String> importedCnpjs,
        List<String> errors) {

    public AnpImportResult(int totalRecordsRead, int totalRecordsImported, List<String> errors) {
        this(
                totalRecordsRead,
                totalRecordsImported,
                totalRecordsImported,
                0,
                errors.size(),
                0,
                0,
                0,
                totalRecordsImported,
                Set.of(),
                errors);
    }
}
