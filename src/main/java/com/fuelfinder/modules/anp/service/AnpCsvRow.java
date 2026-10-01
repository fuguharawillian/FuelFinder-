package com.fuelfinder.modules.anp.service;

import java.util.Map;

public record AnpCsvRow(int recordNumber, Map<String, String> values) {
}
