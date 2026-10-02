package com.fuelfinder.modules.station.repository;

import com.fuelfinder.modules.station.entity.Station;
import com.fuelfinder.modules.station.entity.StationStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface StationRepository extends JpaRepository<Station, UUID> {

    boolean existsByCnpj(String cnpj);

    Optional<Station> findByCnpj(String cnpj);

    List<Station> findByCnpjIn(Collection<String> cnpjs);

    Optional<Station> findByIdAndStatus(UUID id, StationStatus status);

    List<Station> findByStatusAndLatitudeBetween(
            StationStatus status,
            BigDecimal minimumLatitude,
            BigDecimal maximumLatitude);

    List<Station> findByStatusAndLatitudeBetweenAndLongitudeBetween(
            StationStatus status,
            BigDecimal minimumLatitude,
            BigDecimal maximumLatitude,
            BigDecimal minimumLongitude,
            BigDecimal maximumLongitude);

    @Query("""
            SELECT station FROM Station station
            WHERE station.status = :status
              AND (
                LOWER(station.corporateName) LIKE LOWER(CONCAT('%', :query, '%'))
                OR LOWER(COALESCE(station.tradeName, '')) LIKE LOWER(CONCAT('%', :query, '%'))
                OR LOWER(COALESCE(station.brand, '')) LIKE LOWER(CONCAT('%', :query, '%'))
                OR LOWER(COALESCE(station.street, '')) LIKE LOWER(CONCAT('%', :query, '%'))
                OR LOWER(COALESCE(station.neighborhood, '')) LIKE LOWER(CONCAT('%', :query, '%'))
                OR LOWER(station.city) LIKE LOWER(CONCAT('%', :query, '%'))
                OR LOWER(station.state) LIKE LOWER(CONCAT('%', :query, '%'))
                OR LOWER(COALESCE(station.postalCode, '')) LIKE LOWER(CONCAT('%', :query, '%'))
              )
            ORDER BY station.city, station.corporateName
            """)
    List<Station> searchByText(
            @Param("status") StationStatus status,
            @Param("query") String query);
}
