package com.fuelfinder.modules.vehicle.entity;

import jakarta.persistence.Column;
import jakarta.persistence.AttributeOverride;
import jakarta.persistence.AttributeOverrides;
import jakarta.persistence.Entity;
import jakarta.persistence.Embedded;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "vehicles")
public class Vehicle {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(nullable = false, length = 50)
    private String nickname;

    @Column(nullable = false, length = 100)
    private String brand;

    @Column(nullable = false, length = 100)
    private String model;

    @Column(name = "year_manufacture", nullable = false)
    private Integer yearManufacture;

    @Enumerated(EnumType.STRING)
    @Column(name = "fuel_type_accepted", nullable = false, length = 20)
    private FuelTypeAccepted fuelTypeAccepted;

    @Embedded
    private TankCapacity tankCapacity;

    @Embedded
    @AttributeOverrides({
            @AttributeOverride(name = "value", column = @Column(name = "avg_consumption_gasoline_value")),
            @AttributeOverride(name = "unit", column = @Column(name = "avg_consumption_gasoline_unit"))
    })
    private FuelConsumption avgConsumptionGasoline;

    @Embedded
    @AttributeOverrides({
            @AttributeOverride(name = "value", column = @Column(name = "avg_consumption_ethanol_value")),
            @AttributeOverride(name = "unit", column = @Column(name = "avg_consumption_ethanol_unit"))
    })
    private FuelConsumption avgConsumptionEthanol;

    @Embedded
    @AttributeOverrides({
            @AttributeOverride(name = "value", column = @Column(name = "avg_consumption_diesel_value")),
            @AttributeOverride(name = "unit", column = @Column(name = "avg_consumption_diesel_unit"))
    })
    private FuelConsumption avgConsumptionDiesel;

    @Embedded
    @AttributeOverrides({
            @AttributeOverride(name = "value", column = @Column(name = "avg_consumption_cng_value")),
            @AttributeOverride(name = "unit", column = @Column(name = "avg_consumption_cng_unit"))
    })
    private FuelConsumption avgConsumptionCng;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    protected Vehicle() {
    }

    public Vehicle(
            UUID userId,
            String nickname,
            String brand,
            String model,
            Integer yearManufacture,
            FuelTypeAccepted fuelTypeAccepted,
            TankCapacity tankCapacity,
            FuelConsumption avgConsumptionGasoline,
            FuelConsumption avgConsumptionEthanol,
            FuelConsumption avgConsumptionDiesel,
            FuelConsumption avgConsumptionCng) {
        this.userId = userId;
        this.nickname = nickname;
        this.brand = brand;
        this.model = model;
        this.yearManufacture = yearManufacture;
        this.fuelTypeAccepted = fuelTypeAccepted;
        this.tankCapacity = tankCapacity;
        this.avgConsumptionGasoline = avgConsumptionGasoline;
        this.avgConsumptionEthanol = avgConsumptionEthanol;
        this.avgConsumptionDiesel = avgConsumptionDiesel;
        this.avgConsumptionCng = avgConsumptionCng;
    }

    @PrePersist
    void initializeTimestamps() {
        LocalDateTime now = LocalDateTime.now();
        if (createdAt == null) {
            createdAt = now;
        }
        updatedAt = now;
    }

    @PreUpdate
    void updateTimestamp() {
        updatedAt = LocalDateTime.now();
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public UUID getUserId() {
        return userId;
    }

    public void setUserId(UUID userId) {
        this.userId = userId;
    }

    public String getNickname() {
        return nickname;
    }

    public void setNickname(String nickname) {
        this.nickname = nickname;
    }

    public String getBrand() {
        return brand;
    }

    public void setBrand(String brand) {
        this.brand = brand;
    }

    public String getModel() {
        return model;
    }

    public void setModel(String model) {
        this.model = model;
    }

    public Integer getYearManufacture() {
        return yearManufacture;
    }

    public void setYearManufacture(Integer yearManufacture) {
        this.yearManufacture = yearManufacture;
    }

    public FuelTypeAccepted getFuelTypeAccepted() {
        return fuelTypeAccepted;
    }

    public void setFuelTypeAccepted(FuelTypeAccepted fuelTypeAccepted) {
        this.fuelTypeAccepted = fuelTypeAccepted;
    }

    public TankCapacity getTankCapacity() {
        return tankCapacity;
    }

    public void setTankCapacity(TankCapacity tankCapacity) {
        this.tankCapacity = tankCapacity;
    }

    public FuelConsumption getAvgConsumptionGasoline() {
        return avgConsumptionGasoline;
    }

    public void setAvgConsumptionGasoline(FuelConsumption avgConsumptionGasoline) {
        this.avgConsumptionGasoline = avgConsumptionGasoline;
    }

    public FuelConsumption getAvgConsumptionEthanol() {
        return avgConsumptionEthanol;
    }

    public void setAvgConsumptionEthanol(FuelConsumption avgConsumptionEthanol) {
        this.avgConsumptionEthanol = avgConsumptionEthanol;
    }

    public FuelConsumption getAvgConsumptionDiesel() {
        return avgConsumptionDiesel;
    }

    public void setAvgConsumptionDiesel(FuelConsumption avgConsumptionDiesel) {
        this.avgConsumptionDiesel = avgConsumptionDiesel;
    }

    public FuelConsumption getAvgConsumptionCng() {
        return avgConsumptionCng;
    }

    public void setAvgConsumptionCng(FuelConsumption avgConsumptionCng) {
        this.avgConsumptionCng = avgConsumptionCng;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }
}
