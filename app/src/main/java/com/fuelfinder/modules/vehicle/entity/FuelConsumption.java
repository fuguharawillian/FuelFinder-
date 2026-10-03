package com.fuelfinder.modules.vehicle.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;

import java.math.BigDecimal;

@Embeddable
public class FuelConsumption {

    @Column(precision = 5, scale = 2)
    private BigDecimal value;

    @Enumerated(EnumType.STRING)
    @Column(length = 24)
    private ConsumptionUnit unit;

    protected FuelConsumption() {
    }

    public FuelConsumption(BigDecimal value, ConsumptionUnit unit) {
        this.value = value;
        this.unit = unit;
    }

    public BigDecimal getValue() {
        return value;
    }

    public ConsumptionUnit getUnit() {
        return unit;
    }
}
