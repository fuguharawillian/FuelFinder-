package com.fuelfinder.modules.station.entity;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class StationTest {

    @Test
    void initializesAndUpdatesTimestamps() {
        Station station = station();
        station.initializeTimestamps();
        LocalDateTime createdAt = station.getCreatedAt();
        assertNotNull(createdAt);
        assertNotNull(station.getUpdatedAt());

        station.initializeTimestamps();
        assertEquals(createdAt, station.getCreatedAt());
        station.updateTimestamp();
        assertNotNull(station.getUpdatedAt());
    }

    @Test
    void exposesStationDataForPersistenceAndResponses() {
        Station station = station();
        UUID id = UUID.randomUUID();
        station.setId(id);
        station.setCnpj("12345678000195");
        station.setCorporateName("Corporate");
        station.setTradeName("Trade");
        station.setBrand("Brand");
        station.setStreet("Street");
        station.setNumber("10");
        station.setNeighborhood("Center");
        station.setCity("City");
        station.setState("SP");
        station.setPostalCode("01000-000");
        station.setLatitude(new BigDecimal("-23.5500000"));
        station.setLongitude(new BigDecimal("-46.6300000"));
        station.setAverageRating(new BigDecimal("4.50"));
        station.setTotalReviews(12);
        station.setStatus(StationStatus.INACTIVE);

        assertEquals(id, station.getId());
        assertEquals("12345678000195", station.getCnpj());
        assertEquals("Corporate", station.getCorporateName());
        assertEquals("Trade", station.getTradeName());
        assertEquals("Brand", station.getBrand());
        assertEquals("Street", station.getStreet());
        assertEquals("10", station.getNumber());
        assertEquals("Center", station.getNeighborhood());
        assertEquals("City", station.getCity());
        assertEquals("SP", station.getState());
        assertEquals("01000-000", station.getPostalCode());
        assertEquals(new BigDecimal("-23.5500000"), station.getLatitude());
        assertEquals(new BigDecimal("-46.6300000"), station.getLongitude());
        assertEquals(new BigDecimal("4.50"), station.getAverageRating());
        assertEquals(12, station.getTotalReviews());
        assertEquals(StationStatus.INACTIVE, station.getStatus());
    }

    private Station station() {
        return new Station(
                "12345678000195",
                "Corporate",
                null,
                null,
                null,
                null,
                null,
                "City",
                "SP",
                null,
                BigDecimal.ZERO,
                BigDecimal.ZERO);
    }
}
