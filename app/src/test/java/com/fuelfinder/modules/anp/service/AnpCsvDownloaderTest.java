package com.fuelfinder.modules.anp.service;

import com.fuelfinder.common.exception.BusinessException;
import com.fuelfinder.common.exception.ExternalServiceException;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withException;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
import static org.springframework.http.HttpStatus.NOT_FOUND;

class AnpCsvDownloaderTest {

    @Test
    void downloadsFilesFromOfficialHttpsHosts() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        String sourceUrl = "https://www.gov.br/anp/historico.csv";
        byte[] expected = "ANP CSV".getBytes(StandardCharsets.UTF_8);
        server.expect(requestTo(sourceUrl))
                .andRespond(withSuccess(expected, MediaType.TEXT_PLAIN));
        AnpCsvDownloader downloader = new AnpCsvDownloader(builder.build());

        assertArrayEquals(expected, downloader.download(sourceUrl));

        server.verify();
    }

    @Test
    void rejectsNonHttpsNonOfficialUrlsAndCredentials() {
        AnpCsvDownloader downloader = new AnpCsvDownloader(RestClient.builder().build());

        assertThrows(BusinessException.class,
                () -> downloader.validateSourceUrl("http://www.gov.br/anp.csv"));
        assertThrows(BusinessException.class,
                () -> downloader.validateSourceUrl("https://example.com/anp.csv"));
        assertThrows(BusinessException.class,
                () -> downloader.validateSourceUrl("https://www.gov.br:8443/anp.csv"));
        assertThrows(BusinessException.class,
                () -> downloader.validateSourceUrl("https://user@www.gov.br/anp.csv"));
        assertThrows(BusinessException.class,
                () -> downloader.validateSourceUrl("https://www.gov.br/anp.csv#fragment"));
        assertThrows(BusinessException.class,
                () -> downloader.validateSourceUrl("not a url"));
        assertThrows(BusinessException.class, () -> downloader.validateSourceUrl(" "));
    }

    @Test
    void stripsQueryAndFragmentDataFromAuditSourceUrls() {
        AnpCsvDownloader downloader = new AnpCsvDownloader(RestClient.builder().build());

        URI source = downloader.validateSourceUrl(
                "https://dados.gov.br/dataset/arquivo.csv?token=do-not-log");

        assertEquals(
                "https://dados.gov.br/dataset/arquivo.csv",
                downloader.sanitizeSourceUrl(source));
    }

    @Test
    void convertsHttpAndNetworkFailuresToExternalServiceErrors() {
        RestClient.Builder httpErrorBuilder = RestClient.builder();
        MockRestServiceServer httpErrorServer =
                MockRestServiceServer.bindTo(httpErrorBuilder).build();
        String sourceUrl = "https://www.gov.br/anp.csv";
        httpErrorServer.expect(requestTo(sourceUrl))
                .andRespond(withStatus(NOT_FOUND));
        AnpCsvDownloader httpErrorDownloader =
                new AnpCsvDownloader(httpErrorBuilder.build());
        assertThrows(ExternalServiceException.class,
                () -> httpErrorDownloader.download(sourceUrl));
        httpErrorServer.verify();

        RestClient.Builder networkErrorBuilder = RestClient.builder();
        MockRestServiceServer networkErrorServer =
                MockRestServiceServer.bindTo(networkErrorBuilder).build();
        networkErrorServer.expect(requestTo(sourceUrl))
                .andRespond(withException(new IOException("network failure")));
        AnpCsvDownloader networkErrorDownloader =
                new AnpCsvDownloader(networkErrorBuilder.build());
        assertThrows(ExternalServiceException.class,
                () -> networkErrorDownloader.download(sourceUrl));
        networkErrorServer.verify();
    }

    @Test
    void rejectsEmptyDownloadedContent() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        String sourceUrl = "https://www.gov.br/anp-empty.csv";
        server.expect(requestTo(sourceUrl))
                .andRespond(withSuccess(new byte[0], MediaType.APPLICATION_OCTET_STREAM));
        AnpCsvDownloader downloader = new AnpCsvDownloader(builder.build());

        assertThrows(ExternalServiceException.class, () -> downloader.download(sourceUrl));

        server.verify();
    }

    @Test
    void rejectsDownloadsLargerThanFiftyMegabytes() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        String sourceUrl = "https://www.gov.br/large-anp.csv";
        server.expect(requestTo(sourceUrl))
                .andRespond(withSuccess(
                        new byte[50 * 1024 * 1024 + 1], MediaType.APPLICATION_OCTET_STREAM));
        AnpCsvDownloader downloader = new AnpCsvDownloader(builder.build());

        assertThrows(ExternalServiceException.class, () -> downloader.download(sourceUrl));

        server.verify();
    }

}
