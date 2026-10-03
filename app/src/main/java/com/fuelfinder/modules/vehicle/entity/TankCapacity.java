package com.fuelfinder.modules.vehicle.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;

import java.math.BigDecimal;

@Embeddable
public class TankCapacity {

    @Column(name = "tank_capacity_value", nullable = false, precision = 10, scale = 5)
    private BigDecimal value;

    @Enumerated(EnumType.STRING)
    @Column(name = "tank_capacity_unit", nullable = false, length = 16)
    private VolumeUnit unit;

    protected TankCapacity() {
    }

    public TankCapacity(BigDecimal value, VolumeUnit unit) {
        this.value = value;
        this.unit = unit;
    }

    public BigDecimal getValue() {
        return value;
    }

    public VolumeUnit getUnit() {
        return unit;
    }
}
