package com.fuelfinder.modules.fuel.entity;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FuelTypeTest {

    @Test
    void exposesCatalogFields() {
        FuelType fuelType = new FuelType();
        UUID id = UUID.randomUUID();
        fuelType.setId(id);
        fuelType.setCode("CNG");
        fuelType.setName("GNV");
        fuelType.setUnitOfMeasure("R$/m³");
        fuelType.setActive(true);

        assertEquals(id, fuelType.getId());
        assertEquals("CNG", fuelType.getCode());
        assertEquals("GNV", fuelType.getName());
        assertEquals("R$/m³", fuelType.getUnitOfMeasure());
        assertTrue(fuelType.getActive());
    }
}
