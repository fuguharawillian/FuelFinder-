package com.fuelfinder.modules.station.service;

import com.fuelfinder.common.exception.ExternalServiceException;

public class GeoapifyRateLimitException extends ExternalServiceException {

    public GeoapifyRateLimitException(Throwable cause) {
        super("Geoapify request limit reached.", cause);
    }
}
