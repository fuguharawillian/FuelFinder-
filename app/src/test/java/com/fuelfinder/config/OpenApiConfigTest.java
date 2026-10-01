package com.fuelfinder.config;

import io.swagger.v3.oas.models.OpenAPI;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class OpenApiConfigTest {

    @Test
    void declaresApiMetadataAndBearerAuthentication() {
        OpenAPI openApi = new OpenApiConfig().fuelFinderOpenApi();

        assertEquals("FuelFinder API", openApi.getInfo().getTitle());
        assertEquals("1.0.0", openApi.getInfo().getVersion());
        assertEquals("FuelFinder Team", openApi.getInfo().getContact().getName());
        assertEquals(java.util.List.of(),
                openApi.getSecurity().getFirst().get("bearerAuth"));
        assertNotNull(openApi.getComponents());
        assertEquals("JWT", openApi.getComponents()
                .getSecuritySchemes().get("bearerAuth").getBearerFormat());
    }
}
