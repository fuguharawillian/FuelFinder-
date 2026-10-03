package com.fuelfinder.modules.anp.service;

import java.util.List;

public record AnpCoordinateUpdateResult(
        int pagesProcessed,
        int coordinatesUpdated,
        int apiCnpjsUnmatched,
        int apiStationsWithoutCoordinates,
        int stationsWithoutCoordinates,
        List<String> errors) {
}
