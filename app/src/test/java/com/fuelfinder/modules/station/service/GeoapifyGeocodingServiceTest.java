package com.fuelfinder.modules.station.service;

import com.fuelfinder.common.exception.ExternalServiceException;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.queryParam;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
import static org.springframework.http.HttpMethod.GET;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;

class GeoapifyGeocodingServiceTest {

    @Test
    void disablesGeocodingWhenApiKeyIsMissing() {
        GeoapifyGeocodingService service =
                new GeoapifyGeocodingService(RestClient.builder(), "  ");

        assertFalse(service.isConfigured());
        assertEquals(Optional.empty(), service.geocode("São Paulo"));
        assertEquals(Optional.empty(),
                new GeoapifyGeocodingService(RestClient.builder(), null).geocode("São Paulo"));

        GeoapifyGeocodingService configured =
                new GeoapifyGeocodingService(RestClient.builder(), " test-key ");
        assertTrue(configured.isConfigured());
    }

    @Test
    void parsesGeocodingResultsAndIgnoresResultsWithoutCoordinates() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo(containsString(
                        "https://api.geoapify.com/v1/geocode/search")))
                .andExpect(method(GET))
                .andExpect(queryParam("text", is("S%C3%A3o%20Paulo")))
                .andExpect(queryParam("format", is("json")))
                .andExpect(queryParam("apiKey", is("test-key")))
                .andRespond(withSuccess("""
                        {
                          "results": [
                            {"name":"missing coordinates"},
                            {"lat":-23.55},
                            {"lat":-23.55,"lon":-46.63}
                          ]
                        }
                        """, MediaType.APPLICATION_JSON));

        GeoapifyGeocodingService service = new GeoapifyGeocodingService(builder, "test-key");
        var result = service.geocode("São Paulo");

        assertEquals(Optional.of(new GeoapifyGeocodingService.GeoPoint(-23.55, -46.63)), result);
        server.verify();
    }

    @Test
    void returnsEmptyWhenGeocoderHasNoResults() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo(containsString("api.geoapify.com")))
                .andRespond(withSuccess("{\"results\":[]}", MediaType.APPLICATION_JSON));

        assertEquals(Optional.empty(),
                new GeoapifyGeocodingService(builder, "test-key").geocode("Unknown"));
        server.verify();
    }

    @Test
    void returnsEmptyWhenGeocoderResponseBodyIsMissing() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo(containsString("api.geoapify.com")))
                .andRespond(withSuccess("", MediaType.APPLICATION_JSON));

        assertEquals(Optional.empty(),
                new GeoapifyGeocodingService(builder, "test-key").geocode("Unknown"));
        server.verify();
    }

    @Test
    void returnsEmptyWhenGeocoderResponseOmitsResults() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo(containsString("api.geoapify.com")))
                .andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));

        assertEquals(Optional.empty(),
                new GeoapifyGeocodingService(builder, "test-key").geocode("Unknown"));
        server.verify();
    }

    @Test
    void surfacesExternalServiceFailuresWithoutLeakingTheApiKeyInTheMessage() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo(containsString("api.geoapify.com")))
                .andRespond(withServerError());

        ExternalServiceException exception = assertThrows(
                ExternalServiceException.class,
                () -> new GeoapifyGeocodingService(builder, "secret-test-key")
                        .geocode("São Paulo"));

        assertEquals(
                "Não foi possível concluir a busca geográfica neste momento.",
                exception.getMessage());
        server.verify();
    }

    @Test
    void identifiesRateLimitResponsesSeparately() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo(containsString("api.geoapify.com")))
                .andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS));

        assertThrows(
                GeoapifyRateLimitException.class,
                () -> new GeoapifyGeocodingService(builder, "test-key").geocode("São Paulo"));
        server.verify();
    }
}
