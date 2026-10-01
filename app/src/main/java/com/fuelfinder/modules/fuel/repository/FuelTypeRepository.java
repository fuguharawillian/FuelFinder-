package com.fuelfinder.modules.fuel.repository;

import com.fuelfinder.modules.fuel.entity.FuelType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface FuelTypeRepository extends JpaRepository<FuelType, UUID> {

    Optional<FuelType> findByCode(String code);

    List<FuelType> findByActiveTrueOrderByNameAsc();
}
