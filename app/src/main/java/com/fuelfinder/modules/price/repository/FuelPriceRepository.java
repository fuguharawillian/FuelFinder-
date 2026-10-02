package com.fuelfinder.modules.price.repository;

import com.fuelfinder.modules.price.entity.FuelPrice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface FuelPriceRepository extends JpaRepository<FuelPrice, UUID> {

    @Query("""
            SELECT price FROM FuelPrice price
            JOIN FETCH price.fuelType
            WHERE price.station.id = :stationId
              AND price.collectionDate = (
                SELECT MAX(latest.collectionDate) FROM FuelPrice latest
                WHERE latest.station.id = price.station.id
                  AND latest.fuelType.id = price.fuelType.id
              )
            ORDER BY price.fuelType.name
            """)
    List<FuelPrice> findLatestByStationId(@Param("stationId") UUID stationId);

    @Query("""
            SELECT price FROM FuelPrice price
            JOIN FETCH price.station
            JOIN FETCH price.fuelType
            WHERE price.station.id IN :stationIds
              AND (:fuelTypeCode IS NULL OR price.fuelType.code = :fuelTypeCode)
              AND price.collectionDate = (
                SELECT MAX(latest.collectionDate) FROM FuelPrice latest
                WHERE latest.station.id = price.station.id
                  AND latest.fuelType.id = price.fuelType.id
              )
            """)
    List<FuelPrice> findLatestForStations(
            @Param("stationIds") List<UUID> stationIds,
            @Param("fuelTypeCode") String fuelTypeCode);

    Optional<FuelPrice> findByStationIdAndFuelTypeIdAndCollectionDate(
            UUID stationId,
            UUID fuelTypeId,
            LocalDate collectionDate);

    @Query("""
            SELECT price FROM FuelPrice price
            JOIN FETCH price.station
            JOIN FETCH price.fuelType
            WHERE price.collectionDate BETWEEN :startDate AND :endDate
            """)
    List<FuelPrice> findByCollectionDateBetweenWithRelations(
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate);

    Optional<FuelPrice> findByIdAndStationId(UUID id, UUID stationId);
}
