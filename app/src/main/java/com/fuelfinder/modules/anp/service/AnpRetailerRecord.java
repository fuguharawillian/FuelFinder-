package com.fuelfinder.modules.anp.service;

public record AnpRetailerRecord(
        String cnpj,
        String state,
        String latitude,
        String longitude) {
}
