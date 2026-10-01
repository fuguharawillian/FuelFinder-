package com.fuelfinder.config;

import org.junit.jupiter.api.Test;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WebConfigTest {

    private final CorsConfigurationSource corsConfigurationSource =
            new WebConfig("http://localhost:3000,http://localhost:5500")
                    .corsConfigurationSource();

    @Test
    void allowsConfiguredLocalFrontendOrigins() {
        CorsConfiguration configuration = getConfiguration();

        assertEquals("http://localhost:3000",
                configuration.checkOrigin("http://localhost:3000"));
        assertEquals("http://localhost:5500",
                configuration.checkOrigin("http://localhost:5500"));
        assertTrue(configuration.getAllowedMethods().contains("OPTIONS"));
        assertTrue(configuration.getAllowCredentials());
    }

    @Test
    void rejectsOriginsOutsideLocalFrontendAllowlist() {
        assertNull(getConfiguration().checkOrigin("https://example.com"));
    }

    private CorsConfiguration getConfiguration() {
        return corsConfigurationSource.getCorsConfiguration(
                new org.springframework.mock.web.MockHttpServletRequest());
    }
}
