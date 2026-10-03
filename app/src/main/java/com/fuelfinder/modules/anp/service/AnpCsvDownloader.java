package com.fuelfinder.modules.anp.service;

import com.fuelfinder.common.exception.BusinessException;
import com.fuelfinder.common.exception.ExternalServiceException;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.net.URI;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.Locale;
import java.util.Set;
import org.springframework.beans.factory.annotation.Autowired;

@Service
public class AnpCsvDownloader {

    private static final Set<String> ALLOWED_HOSTS =
            Set.of("www.gov.br", "gov.br", "www.anp.gov.br", "anp.gov.br", "dados.gov.br");
    private static final int MAX_FILE_BYTES = 50 * 1024 * 1024;
    private final RestClient restClient;

    @Autowired
    public AnpCsvDownloader(RestClient.Builder restClientBuilder) {
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();
        JdkClientHttpRequestFactory requestFactory =
                new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(Duration.ofSeconds(30));
        this.restClient = restClientBuilder.clone()
                .requestFactory(requestFactory)
                .build();
    }

    AnpCsvDownloader(RestClient restClient) {
        this.restClient = restClient;
    }

    public byte[] download(String sourceUrl) {
        URI sourceUri = validateSourceUrl(sourceUrl);
        try {
            byte[] content = restClient.get()
                    .uri(sourceUri)
                    .exchange((request, response) -> {
                        if (!response.getStatusCode().is2xxSuccessful()) {
                            throw new ExternalServiceException(
                                    "A fonte da ANP respondeu com erro HTTP.", null);
                        }
                        byte[] responseContent = response.getBody().readNBytes(MAX_FILE_BYTES + 1);
                        if (responseContent.length > MAX_FILE_BYTES) {
                            throw new ExternalServiceException(
                                    "O arquivo ANP excede o limite de 50 MB.", null);
                        }
                        return responseContent;
                    });
            if (content == null || content.length == 0) {
                throw new ExternalServiceException(
                        "A fonte da ANP retornou um arquivo vazio.", null);
            }
            return content;
        } catch (RestClientException exception) {
            throw new ExternalServiceException(
                    "Não foi possível baixar o arquivo da fonte da ANP.", exception);
        }
    }

    public URI validateSourceUrl(String sourceUrl) {
        if (sourceUrl == null || sourceUrl.isBlank()) {
            throw new BusinessException("A URL de origem da ANP é obrigatória.");
        }
        URI uri;
        try {
            uri = URI.create(sourceUrl.trim());
        } catch (IllegalArgumentException exception) {
            throw new BusinessException("A URL de origem da ANP é inválida.");
        }
        String host = uri.getHost() == null ? "" : uri.getHost().toLowerCase(Locale.ROOT);
        if (!uri.isAbsolute()
                || !"https".equalsIgnoreCase(uri.getScheme())
                || !ALLOWED_HOSTS.contains(host)
                || (uri.getPort() != -1 && uri.getPort() != 443)
                || uri.getUserInfo() != null
                || uri.getFragment() != null) {
            throw new BusinessException(
                    "A URL deve usar HTTPS e pertencer a um domínio oficial da ANP ou do governo federal.");
        }
        return uri;
    }

    public String sanitizeSourceUrl(URI sourceUri) {
        String source = sourceUri.toASCIIString();
        int queryStart = source.indexOf('?');
        return queryStart < 0 ? source : source.substring(0, queryStart);
    }
}
