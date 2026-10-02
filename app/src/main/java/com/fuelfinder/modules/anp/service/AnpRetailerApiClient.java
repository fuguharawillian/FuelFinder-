package com.fuelfinder.modules.anp.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fuelfinder.common.exception.ExternalServiceException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

@Component
public class AnpRetailerApiClient {

    private static final String API_BASE_URL = "https://revendedoresapi.anp.gov.br";
    private final RestClient restClient;

    @Autowired
    public AnpRetailerApiClient(RestClient.Builder restClientBuilder) {
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();
        JdkClientHttpRequestFactory requestFactory =
                new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(Duration.ofSeconds(30));
        this.restClient = restClientBuilder.clone()
                .requestFactory(requestFactory)
                .baseUrl(API_BASE_URL)
                .build();
    }

    AnpRetailerApiClient(RestClient restClient) {
        this.restClient = restClient;
    }

    public AnpRetailerPage getSaoPauloPage(int pageNumber) {
        if (pageNumber < 1) {
            throw new IllegalArgumentException("O número da página da ANP deve ser positivo.");
        }
        try {
            JsonNode response = restClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/v1/combustivel")
                            .queryParam("uf", "SP")
                            .queryParam("numeropagina", pageNumber)
                            .build())
                    .retrieve()
                    .body(JsonNode.class);
            if (response == null
                    || !response.path("status").canConvertToInt()
                    || response.path("status").asInt() != 200
                    || !response.path("succeeded").asBoolean(false)
                    || !response.path("data").isArray()) {
                throw new ExternalServiceException(
                        "A API de revendedores da ANP retornou uma resposta inválida na página "
                                + pageNumber + ".", null);
            }
            List<AnpRetailerRecord> records = new ArrayList<>();
            for (JsonNode item : response.path("data")) {
                records.add(new AnpRetailerRecord(
                        textValue(item, "cnpj"),
                        textValue(item, "uf"),
                        textValue(item, "latitude"),
                        textValue(item, "longitude")));
            }
            return new AnpRetailerPage(List.copyOf(records));
        } catch (RestClientException exception) {
            throw new ExternalServiceException(
                    "Falha ao consultar a API de revendedores da ANP na página "
                            + pageNumber + ".", exception);
        }
    }

    private String textValue(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return value == null || value.isNull() ? null : value.asText();
    }
}
