package com.fuelfinder.modules.vehicle.service;

import com.fuelfinder.common.exception.BusinessException;
import com.fuelfinder.common.exception.ResourceNotFoundException;
import com.fuelfinder.modules.vehicle.dto.CreateVehicleRequestDTO;
import com.fuelfinder.modules.vehicle.dto.ConsumptionDTO;
import com.fuelfinder.modules.vehicle.dto.TankCapacityDTO;
import com.fuelfinder.modules.vehicle.dto.UpdateVehicleRequestDTO;
import com.fuelfinder.modules.vehicle.dto.VehicleResponseDTO;
import com.fuelfinder.modules.vehicle.entity.ConsumptionUnit;
import com.fuelfinder.modules.vehicle.entity.FuelConsumption;
import com.fuelfinder.modules.vehicle.entity.FuelTypeAccepted;
import com.fuelfinder.modules.vehicle.entity.TankCapacity;
import com.fuelfinder.modules.vehicle.entity.Vehicle;
import com.fuelfinder.modules.vehicle.entity.VolumeUnit;
import com.fuelfinder.modules.vehicle.repository.VehicleRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Year;
import java.util.List;
import java.util.UUID;

@Service
@Transactional(readOnly = true)
public class VehicleService {

    private static final BigDecimal MIN_CONSUMPTION = new BigDecimal("1.0");
    private static final BigDecimal MAX_CONSUMPTION = new BigDecimal("40.0");

    private final VehicleRepository vehicleRepository;
    private final Clock clock;

    public VehicleService(VehicleRepository vehicleRepository, Clock clock) {
        this.vehicleRepository = vehicleRepository;
        this.clock = clock;
    }

    @Transactional
    public VehicleResponseDTO create(CreateVehicleRequestDTO request, UUID userId) {
        validateYear(request.yearManufacture());
        validateFuelConsumption(
                request.fuelTypeAccepted(),
                request.averageConsumptionGasoline(),
                request.averageConsumptionEthanol(),
                request.averageConsumptionDiesel(),
                request.averageConsumptionCng());
        validateTankCapacity(request.fuelTypeAccepted(), request.tankCapacity());

        Vehicle vehicle = new Vehicle(
                userId,
                request.nickname(),
                request.brand(),
                request.model(),
                request.yearManufacture(),
                request.fuelTypeAccepted(),
                toTankCapacity(request.tankCapacity()),
                toConsumption(request.averageConsumptionGasoline()),
                toConsumption(request.averageConsumptionEthanol()),
                toConsumption(request.averageConsumptionDiesel()),
                toConsumption(request.averageConsumptionCng()));

        return toResponse(vehicleRepository.save(vehicle));
    }

    public List<VehicleResponseDTO> listByUser(UUID userId) {
        return vehicleRepository.findByUserIdOrderByCreatedAtDesc(userId)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    public VehicleResponseDTO findByIdAndUser(UUID vehicleId, UUID userId) {
        return toResponse(findOwnedVehicle(vehicleId, userId));
    }

    @Transactional
    public VehicleResponseDTO update(
            UUID vehicleId,
            UpdateVehicleRequestDTO request,
            UUID userId) {
        Vehicle vehicle = findOwnedVehicle(vehicleId, userId);
        applyUpdates(vehicle, request);
        validateYear(vehicle.getYearManufacture());
        validateTankCapacity(vehicle.getFuelTypeAccepted(), toTankCapacityDTO(vehicle.getTankCapacity()));
        validateFuelConsumption(
                vehicle.getFuelTypeAccepted(),
                toConsumptionDTO(vehicle.getAvgConsumptionGasoline()),
                toConsumptionDTO(vehicle.getAvgConsumptionEthanol()),
                toConsumptionDTO(vehicle.getAvgConsumptionDiesel()),
                toConsumptionDTO(vehicle.getAvgConsumptionCng()));
        return toResponse(vehicleRepository.save(vehicle));
    }

    @Transactional
    public void delete(UUID vehicleId, UUID userId) {
        vehicleRepository.delete(findOwnedVehicle(vehicleId, userId));
    }

    private Vehicle findOwnedVehicle(UUID vehicleId, UUID userId) {
        return vehicleRepository.findByIdAndUserId(vehicleId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Veículo não encontrado."));
    }

    private void applyUpdates(Vehicle vehicle, UpdateVehicleRequestDTO request) {
        if (request.hasNickname()) {
            vehicle.setNickname(requireText(request.getNickname(), "O apelido"));
        }
        if (request.hasBrand()) {
            vehicle.setBrand(requireText(request.getBrand(), "A marca"));
        }
        if (request.hasModel()) {
            vehicle.setModel(requireText(request.getModel(), "O modelo"));
        }
        if (request.hasYearManufacture()) {
            if (request.getYearManufacture() == null) {
                throw new BusinessException("O ano de fabricação não pode ser nulo.");
            }
            vehicle.setYearManufacture(request.getYearManufacture());
        }
        if (request.hasFuelTypeAccepted()) {
            if (request.getFuelTypeAccepted() == null) {
                throw new BusinessException("O tipo de combustível não pode ser nulo.");
            }
            vehicle.setFuelTypeAccepted(request.getFuelTypeAccepted());
        }
        if (request.hasTankCapacity()) {
            if (request.getTankCapacity() == null
                    || request.getTankCapacity().value() == null
                    || request.getTankCapacity().value().signum() <= 0
                    || request.getTankCapacity().unit() == null) {
                throw new BusinessException("A capacidade do tanque deve ser maior que zero.");
            }
            vehicle.setTankCapacity(toTankCapacity(request.getTankCapacity()));
        }
        if (request.hasAverageConsumptionGasoline()) {
            vehicle.setAvgConsumptionGasoline(toConsumption(request.getAverageConsumptionGasoline()));
        }
        if (request.hasAverageConsumptionEthanol()) {
            vehicle.setAvgConsumptionEthanol(toConsumption(request.getAverageConsumptionEthanol()));
        }
        if (request.hasAverageConsumptionDiesel()) {
            vehicle.setAvgConsumptionDiesel(toConsumption(request.getAverageConsumptionDiesel()));
        }
        if (request.hasAverageConsumptionCng()) {
            vehicle.setAvgConsumptionCng(toConsumption(request.getAverageConsumptionCng()));
        }
    }

    private String requireText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new BusinessException(fieldName + " não pode ser vazio.");
        }
        return value;
    }

    private void validateYear(Integer year) {
        if (year == null) {
            throw new BusinessException("O ano de fabricação é obrigatório.");
        }
        int maximumYear = Year.now(clock).getValue() + 1;
        if (year < 1950 || year > maximumYear) {
            throw new BusinessException(
                    "Ano de fabricação deve ser entre 1950 e " + maximumYear + ".");
        }
    }

    private void validateFuelConsumption(
            FuelTypeAccepted fuelType,
            ConsumptionDTO gasolineConsumption,
            ConsumptionDTO ethanolConsumption,
            ConsumptionDTO dieselConsumption,
            ConsumptionDTO cngConsumption) {
        if (fuelType == null) {
            throw new BusinessException("O tipo de combustível é obrigatório.");
        }
        validateConsumption(gasolineConsumption,
                ConsumptionUnit.KM_PER_LITER, "gasolina");
        validateConsumption(ethanolConsumption,
                ConsumptionUnit.KM_PER_LITER, "etanol");
        validateConsumption(dieselConsumption,
                ConsumptionUnit.KM_PER_LITER, "diesel");
        validateConsumption(cngConsumption,
                ConsumptionUnit.KM_PER_CUBIC_METER, "GNV");

        if (fuelType == FuelTypeAccepted.FLEX) {
            if (gasolineConsumption == null || ethanolConsumption == null
                    || dieselConsumption != null || cngConsumption != null) {
                throw new BusinessException(
                        "Veículos Flex devem informar somente consumos de gasolina e etanol.");
            }
        } else if (fuelType == FuelTypeAccepted.GASOLINE) {
            if (gasolineConsumption == null || ethanolConsumption != null
                    || dieselConsumption != null || cngConsumption != null) {
                throw new BusinessException(
                        "Veículos a gasolina devem informar somente o consumo de gasolina.");
            }
        } else if (fuelType == FuelTypeAccepted.ETHANOL) {
            if (ethanolConsumption == null || gasolineConsumption != null
                    || dieselConsumption != null || cngConsumption != null) {
                throw new BusinessException(
                        "Veículos a etanol devem informar somente o consumo de etanol.");
            }
        } else if (fuelType == FuelTypeAccepted.DIESEL) {
            if (dieselConsumption == null || gasolineConsumption != null
                    || ethanolConsumption != null || cngConsumption != null) {
                throw new BusinessException(
                        "Veículos a diesel devem informar somente o consumo de diesel.");
            }
        } else if (cngConsumption == null || gasolineConsumption != null
                || ethanolConsumption != null || dieselConsumption != null) {
            throw new BusinessException(
                    "Veículos a GNV devem informar somente o consumo de GNV.");
        }
    }

    private void validateConsumption(
            ConsumptionDTO consumption,
            ConsumptionUnit expectedUnit,
            String fuelName) {
        if (consumption == null) {
            return;
        }
        if (consumption.value() == null
                || consumption.value().compareTo(MIN_CONSUMPTION) < 0
                || consumption.value().compareTo(MAX_CONSUMPTION) > 0) {
            throw new BusinessException("O consumo médio de " + fuelName
                    + " deve estar entre 1.0 e 40.0.");
        }
        if (consumption.unit() != expectedUnit) {
            throw new BusinessException("A unidade do consumo de " + fuelName
                    + " deve ser " + expectedUnit + ".");
        }
    }

    private void validateTankCapacity(FuelTypeAccepted fuelType, TankCapacityDTO capacity) {
        if (capacity == null || capacity.value() == null || capacity.value().signum() <= 0) {
            throw new BusinessException("A capacidade do tanque deve ser maior que zero.");
        }
        VolumeUnit expectedUnit = fuelType == FuelTypeAccepted.CNG
                ? VolumeUnit.CUBIC_METER
                : VolumeUnit.LITER;
        if (capacity.unit() != expectedUnit) {
            throw new BusinessException("A unidade da capacidade do tanque deve ser "
                    + expectedUnit + " para o combustível informado.");
        }
    }

    private VehicleResponseDTO toResponse(Vehicle vehicle) {
        return new VehicleResponseDTO(
                vehicle.getId(),
                vehicle.getNickname(),
                vehicle.getBrand(),
                vehicle.getModel(),
                vehicle.getYearManufacture(),
                vehicle.getFuelTypeAccepted().name(),
                toTankCapacityDTO(vehicle.getTankCapacity()),
                toConsumptionDTO(vehicle.getAvgConsumptionGasoline()),
                toConsumptionDTO(vehicle.getAvgConsumptionEthanol()),
                toConsumptionDTO(vehicle.getAvgConsumptionDiesel()),
                toConsumptionDTO(vehicle.getAvgConsumptionCng()));
    }

    private TankCapacity toTankCapacity(TankCapacityDTO capacity) {
        return new TankCapacity(capacity.value(), capacity.unit());
    }

    private TankCapacityDTO toTankCapacityDTO(TankCapacity capacity) {
        return capacity == null ? null : new TankCapacityDTO(capacity.getValue(), capacity.getUnit());
    }

    private FuelConsumption toConsumption(ConsumptionDTO consumption) {
        return consumption == null
                ? null
                : new FuelConsumption(consumption.value(), consumption.unit());
    }

    private ConsumptionDTO toConsumptionDTO(FuelConsumption consumption) {
        return consumption == null
                ? null
                : new ConsumptionDTO(consumption.getValue(), consumption.getUnit());
    }
}
