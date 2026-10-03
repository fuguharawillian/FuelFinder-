package com.fuelfinder.modules.anp.service;

import com.fuelfinder.common.exception.ExternalServiceException;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
import static org.springframework.http.HttpMethod.GET;

class AnpRetailerApiClientTest {

    @Test
    void requestsTheDocumentedSpPageParameterAndMapsTheEnvelope() {
        RestClient.Builder builder = RestClient.builder()
                .baseUrl("https://revendedoresapi.anp.gov.br");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo(
                        "https://revendedoresapi.anp.gov.br/v1/combustivel?uf=SP&numeropagina=4"))
                .andExpect(method(GET))
                .andRespond(withSuccess("""
                        {
                          "status": 200,
                          "title": "Dados Encontrados",
                          "succeeded": true,
                          "data": [{
                            "cnpj": "12345678000195",
                            "uf": "SP",
                            "latitude": "-23.5",
                            "longitude": "-46.6"
                          }]
                        }
                        """, MediaType.APPLICATION_JSON));
        AnpRetailerApiClient client = new AnpRetailerApiClient(builder.build());

        AnpRetailerPage page = client.getSaoPauloPage(4);

        assertEquals(1, page.records().size());
        assertEquals("-23.5", page.records().get(0).latitude());
        assertEquals("-46.6", page.records().get(0).longitude());
        server.verify();
    }

    @Test
    void rejectsApiResponsesThatDoNotIndicateSuccess() {
        RestClient.Builder builder = RestClient.builder()
                .baseUrl("https://revendedoresapi.anp.gov.br");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo(
                        "https://revendedoresapi.anp.gov.br/v1/combustivel?uf=SP&numeropagina=1"))
                .andRespond(withSuccess("""
                        {"status": 200, "succeeded": false, "data": []}
                        """, MediaType.APPLICATION_JSON));
        AnpRetailerApiClient client = new AnpRetailerApiClient(builder.build());

        assertThrows(ExternalServiceException.class, () -> client.getSaoPauloPage(1));
        server.verify();
    }
}
