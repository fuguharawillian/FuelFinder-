package com.fuelfinder.modules.anp.service;

import java.util.List;

public record ParsedAnpCsv(
        int totalRecordsRead,
        List<AnpCsvRow> records,
        List<String> errors) {
}
