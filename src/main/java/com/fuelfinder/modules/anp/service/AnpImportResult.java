package com.fuelfinder.modules.anp.service;

import java.util.List;

public record AnpImportResult(
        int totalRecordsRead,
        int totalRecordsImported,
        List<String> errors) {
}
