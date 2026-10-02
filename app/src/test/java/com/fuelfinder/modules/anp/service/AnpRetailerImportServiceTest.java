package com.fuelfinder.modules.anp.service;

import com.fuelfinder.common.exception.ExternalServiceException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anySet;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AnpRetailerImportServiceTest {

    private static final String CNPJ = "12345678000195";

    @Mock
    private AnpRetailerApiClient apiClient;
    @Mock
    private AnpCoordinateUpdater coordinateUpdater;

    private AnpRetailerImportService service;

    @BeforeEach
    void setUp() {
        service = new AnpRetailerImportService(apiClient, coordinateUpdater);
        lenient().when(coordinateUpdater.update(anySet(), anyMap(), anySet(), anyList()))
                .thenReturn(new AnpCoordinateUpdateResult(0, 1, 0, 0, 0, List.of()));
    }

    @Test
    void readsEveryPageNormalizesCnpjAndChecksStateBeforeAssociation() {
        when(apiClient.getSaoPauloPage(1)).thenReturn(new AnpRetailerPage(List.of(
                new AnpRetailerRecord("12.345.678/0001-95", "SP", "", ""))));
        when(apiClient.getSaoPauloPage(2)).thenReturn(new AnpRetailerPage(List.of(
                new AnpRetailerRecord(CNPJ, "RJ", "-23.5", "-46.6"),
                new AnpRetailerRecord(CNPJ, "SP", "-23.5", "-46.6"))));
        when(apiClient.getSaoPauloPage(3)).thenReturn(new AnpRetailerPage(List.of()));
        List<Integer> pageProgress = new ArrayList<>();

        AnpCoordinateUpdateResult result = service.updateCoordinates(
                Set.of(CNPJ),
                pageProgress::add);

        assertEquals(3, result.pagesProcessed());
        assertEquals(List.of(1, 2, 3), pageProgress);
        assertEquals(1, result.coordinatesUpdated());
        List<String> apiErrors = capturedApiErrors();
        assertEquals(2, apiErrors.size());
        assertTrue(apiErrors.stream().anyMatch(error -> error.contains("UF SP")));
        assertTrue(apiErrors.stream().anyMatch(error -> error.contains("coordenadas válidas")));
        assertEquals(CNPJ, capturedStations().get(CNPJ).cnpj());
        assertEquals("-23.5", capturedStations().get(CNPJ).latitude());
        verify(apiClient).getSaoPauloPage(3);
    }

    @Test
    void reportsConflictingDuplicateCnpjsAndDoesNotChooseCoordinatesSilently() {
        when(apiClient.getSaoPauloPage(1)).thenReturn(new AnpRetailerPage(List.of(
                new AnpRetailerRecord(CNPJ, "SP", "-23.5", "-46.6"),
                new AnpRetailerRecord(CNPJ, "SP", "-24.0", "-47.0"))));
        when(apiClient.getSaoPauloPage(2)).thenReturn(new AnpRetailerPage(List.of()));

        AnpCoordinateUpdateResult result = service.updateCoordinates(Set.of(CNPJ), ignored -> { });

        assertEquals(2, result.pagesProcessed());
        verify(coordinateUpdater).update(
                Set.of(CNPJ),
                Map.of(CNPJ, new AnpRetailerRecord(CNPJ, "SP", "-23.5", "-46.6")),
                Set.of(CNPJ),
                List.of("Página 1: CNPJ " + CNPJ
                        + " aparece com coordenadas conflitantes na API da ANP."));
        verify(apiClient, never()).getSaoPauloPage(3);
    }

    @Test
    void stopsIfTheApiRepeatsAFullPageInsteadOfAdvancing() {
        AnpRetailerPage repeatedPage = new AnpRetailerPage(List.of(
                new AnpRetailerRecord(CNPJ, "SP", "-23.5", "-46.6")));
        when(apiClient.getSaoPauloPage(1)).thenReturn(repeatedPage);
        when(apiClient.getSaoPauloPage(2)).thenReturn(repeatedPage);

        assertThrows(
                ExternalServiceException.class,
                () -> service.updateCoordinates(Set.of(CNPJ), ignored -> { }));
        verify(coordinateUpdater, never()).update(anySet(), anyMap(), anySet(), anyList());
    }

    private Map<String, AnpRetailerRecord> capturedStations() {
        org.mockito.ArgumentCaptor<Map<String, AnpRetailerRecord>> captor =
                org.mockito.ArgumentCaptor.forClass(Map.class);
        verify(coordinateUpdater).update(anySet(), captor.capture(), anySet(), anyList());
        return captor.getValue();
    }

    private List<String> capturedApiErrors() {
        org.mockito.ArgumentCaptor<List<String>> captor =
                org.mockito.ArgumentCaptor.forClass(List.class);
        verify(coordinateUpdater).update(anySet(), anyMap(), anySet(), captor.capture());
        return captor.getValue();
    }
}
