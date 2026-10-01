package com.fuelfinder.modules.vehicle.dto;

import com.fuelfinder.modules.vehicle.entity.FuelTypeAccepted;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;

public class UpdateVehicleRequestDTO {

    @Size(min = 2, max = 50, message = "O apelido deve conter entre 2 e 50 caracteres")
    private String nickname;
    private boolean nicknameProvided;

    @Size(max = 100, message = "A marca deve conter no máximo 100 caracteres")
    private String brand;
    private boolean brandProvided;

    @Size(max = 100, message = "O modelo deve conter no máximo 100 caracteres")
    private String model;
    private boolean modelProvided;

    private Integer yearManufacture;
    private boolean yearManufactureProvided;

    private FuelTypeAccepted fuelTypeAccepted;
    private boolean fuelTypeAcceptedProvided;

    @Valid
    private TankCapacityDTO tankCapacity;
    private boolean tankCapacityProvided;

    @Valid
    private ConsumptionDTO averageConsumptionGasoline;
    private boolean averageConsumptionGasolineProvided;

    @Valid
    private ConsumptionDTO averageConsumptionEthanol;
    private boolean averageConsumptionEthanolProvided;

    @Valid
    private ConsumptionDTO averageConsumptionCng;
    private boolean averageConsumptionCngProvided;

    public String getNickname() {
        return nickname;
    }

    public void setNickname(String nickname) {
        this.nickname = nickname;
        nicknameProvided = true;
    }

    public boolean hasNickname() {
        return nicknameProvided;
    }

    public String getBrand() {
        return brand;
    }

    public void setBrand(String brand) {
        this.brand = brand;
        brandProvided = true;
    }

    public boolean hasBrand() {
        return brandProvided;
    }

    public String getModel() {
        return model;
    }

    public void setModel(String model) {
        this.model = model;
        modelProvided = true;
    }

    public boolean hasModel() {
        return modelProvided;
    }

    public Integer getYearManufacture() {
        return yearManufacture;
    }

    public void setYearManufacture(Integer yearManufacture) {
        this.yearManufacture = yearManufacture;
        yearManufactureProvided = true;
    }

    public boolean hasYearManufacture() {
        return yearManufactureProvided;
    }

    public FuelTypeAccepted getFuelTypeAccepted() {
        return fuelTypeAccepted;
    }

    public void setFuelTypeAccepted(FuelTypeAccepted fuelTypeAccepted) {
        this.fuelTypeAccepted = fuelTypeAccepted;
        fuelTypeAcceptedProvided = true;
    }

    public boolean hasFuelTypeAccepted() {
        return fuelTypeAcceptedProvided;
    }

    public TankCapacityDTO getTankCapacity() {
        return tankCapacity;
    }

    public void setTankCapacity(TankCapacityDTO tankCapacity) {
        this.tankCapacity = tankCapacity;
        tankCapacityProvided = true;
    }

    public boolean hasTankCapacity() {
        return tankCapacityProvided;
    }

    public ConsumptionDTO getAverageConsumptionGasoline() {
        return averageConsumptionGasoline;
    }

    public void setAverageConsumptionGasoline(ConsumptionDTO averageConsumptionGasoline) {
        this.averageConsumptionGasoline = averageConsumptionGasoline;
        averageConsumptionGasolineProvided = true;
    }

    public boolean hasAverageConsumptionGasoline() {
        return averageConsumptionGasolineProvided;
    }

    public ConsumptionDTO getAverageConsumptionEthanol() {
        return averageConsumptionEthanol;
    }

    public void setAverageConsumptionEthanol(ConsumptionDTO averageConsumptionEthanol) {
        this.averageConsumptionEthanol = averageConsumptionEthanol;
        averageConsumptionEthanolProvided = true;
    }

    public boolean hasAverageConsumptionEthanol() {
        return averageConsumptionEthanolProvided;
    }

    @Valid
    private ConsumptionDTO averageConsumptionDiesel;
    private boolean averageConsumptionDieselProvided;

    public ConsumptionDTO getAverageConsumptionDiesel() {
        return averageConsumptionDiesel;
    }

    public void setAverageConsumptionDiesel(ConsumptionDTO averageConsumptionDiesel) {
        this.averageConsumptionDiesel = averageConsumptionDiesel;
        averageConsumptionDieselProvided = true;
    }

    public boolean hasAverageConsumptionDiesel() {
        return averageConsumptionDieselProvided;
    }

    public ConsumptionDTO getAverageConsumptionCng() {
        return averageConsumptionCng;
    }

    public void setAverageConsumptionCng(ConsumptionDTO averageConsumptionCng) {
        this.averageConsumptionCng = averageConsumptionCng;
        averageConsumptionCngProvided = true;
    }

    public boolean hasAverageConsumptionCng() {
        return averageConsumptionCngProvided;
    }
}
