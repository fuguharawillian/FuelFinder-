package com.fuelfinder.modules.anp.service;

import java.util.Map;

public record AnpCsvRow(int lineNumber, Map<String, String> values) {
}
