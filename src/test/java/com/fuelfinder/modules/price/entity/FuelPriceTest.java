package com.fuelfinder.modules.price.entity;

import com.fuelfinder.modules.fuel.entity.FuelType;
import com.fuelfinder.modules.station.entity.Station;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class FuelPriceTest {

    @Test
    void exposesPriceFieldsAndMaintainsTimestamps() {
        FuelType fuelType = new FuelType();
        Station station = new Station(
                "12345678901234",
                "Corporate",
                "Trade",
                "Brand",
                "Street",
                "1",
                "Center",
                "City",
                "SP",
                "00000-000",
                BigDecimal.ZERO,
                BigDecimal.ZERO);
        FuelPrice price = new FuelPrice();
        UUID id = UUID.randomUUID();
        LocalDate date = LocalDate.of(2026, 9, 28);
        price.setId(id);
        price.setStation(station);
        price.setFuelType(fuelType);
        price.setSaleValue(new BigDecimal("4.250"));
        price.setCollectionDate(date);
        price.setDataSource(DataSource.MANUAL_ADMIN);
        price.initializeTimestamps();
        LocalDateTime createdAt = price.getCreatedAt();
        price.initializeTimestamps();
        price.updateTimestamp();

        assertEquals(id, price.getId());
        assertEquals(station, price.getStation());
        assertEquals(fuelType, price.getFuelType());
        assertEquals(new BigDecimal("4.250"), price.getSaleValue());
        assertEquals(date, price.getCollectionDate());
        assertEquals(DataSource.MANUAL_ADMIN, price.getDataSource());
        assertNotNull(createdAt);
        assertNotNull(price.getUpdatedAt());
    }
}
