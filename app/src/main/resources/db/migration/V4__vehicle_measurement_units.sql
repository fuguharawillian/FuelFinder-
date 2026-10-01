ALTER TABLE vehicles
    RENAME COLUMN tank_capacity TO tank_capacity_value;

ALTER TABLE vehicles
    ALTER COLUMN tank_capacity_value TYPE DECIMAL(10,5);

ALTER TABLE vehicles
    ADD COLUMN tank_capacity_unit VARCHAR(16) NOT NULL DEFAULT 'LITER';

ALTER TABLE vehicles
    RENAME COLUMN avg_consumption_gasoline TO avg_consumption_gasoline_value;

ALTER TABLE vehicles
    RENAME COLUMN avg_consumption_ethanol TO avg_consumption_ethanol_value;

ALTER TABLE vehicles
    ADD COLUMN avg_consumption_gasoline_unit VARCHAR(24),
    ADD COLUMN avg_consumption_ethanol_unit VARCHAR(24),
    ADD COLUMN avg_consumption_diesel_value DECIMAL(5,2),
    ADD COLUMN avg_consumption_diesel_unit VARCHAR(24),
    ADD COLUMN avg_consumption_cng_value DECIMAL(5,2),
    ADD COLUMN avg_consumption_cng_unit VARCHAR(24);

UPDATE vehicles
SET avg_consumption_ethanol_value = COALESCE(
        avg_consumption_ethanol_value,
        avg_consumption_gasoline_value),
    avg_consumption_gasoline_value = NULL
WHERE fuel_type_accepted = 'ETHANOL';

UPDATE vehicles
SET avg_consumption_diesel_value = avg_consumption_gasoline_value,
    avg_consumption_gasoline_value = NULL
WHERE fuel_type_accepted = 'DIESEL';

UPDATE vehicles
SET avg_consumption_cng_value = avg_consumption_gasoline_value,
    avg_consumption_gasoline_value = NULL,
    tank_capacity_value = tank_capacity_value / 1000.0,
    tank_capacity_unit = 'CUBIC_METER'
WHERE fuel_type_accepted = 'CNG';

UPDATE vehicles
SET avg_consumption_gasoline_unit = 'KM_PER_LITER'
WHERE avg_consumption_gasoline_value IS NOT NULL;

UPDATE vehicles
SET avg_consumption_ethanol_unit = 'KM_PER_LITER'
WHERE avg_consumption_ethanol_value IS NOT NULL;

UPDATE vehicles
SET avg_consumption_diesel_unit = 'KM_PER_LITER'
WHERE avg_consumption_diesel_value IS NOT NULL;

-- Legacy CNG consumption values are preserved numerically and classified as km/m³.
UPDATE vehicles
SET avg_consumption_cng_unit = 'KM_PER_CUBIC_METER'
WHERE avg_consumption_cng_value IS NOT NULL;

ALTER TABLE vehicles
    ALTER COLUMN tank_capacity_unit DROP DEFAULT,
    DROP CONSTRAINT ck_vehicles_tank_positive,
    ADD CONSTRAINT ck_vehicles_tank_positive CHECK (tank_capacity_value > 0),
    ADD CONSTRAINT ck_vehicles_tank_unit
        CHECK (tank_capacity_unit IN ('LITER', 'CUBIC_METER')),
    ADD CONSTRAINT ck_vehicles_consumption_gasoline
        CHECK ((avg_consumption_gasoline_value IS NULL) = (avg_consumption_gasoline_unit IS NULL)
            AND (avg_consumption_gasoline_unit IS NULL
                OR (avg_consumption_gasoline_unit = 'KM_PER_LITER'
                    AND avg_consumption_gasoline_value BETWEEN 1.0 AND 40.0))),
    ADD CONSTRAINT ck_vehicles_consumption_ethanol
        CHECK ((avg_consumption_ethanol_value IS NULL) = (avg_consumption_ethanol_unit IS NULL)
            AND (avg_consumption_ethanol_unit IS NULL
                OR (avg_consumption_ethanol_unit = 'KM_PER_LITER'
                    AND avg_consumption_ethanol_value BETWEEN 1.0 AND 40.0))),
    ADD CONSTRAINT ck_vehicles_consumption_diesel
        CHECK ((avg_consumption_diesel_value IS NULL) = (avg_consumption_diesel_unit IS NULL)
            AND (avg_consumption_diesel_unit IS NULL
                OR (avg_consumption_diesel_unit = 'KM_PER_LITER'
                    AND avg_consumption_diesel_value BETWEEN 1.0 AND 40.0))),
    ADD CONSTRAINT ck_vehicles_consumption_cng
        CHECK ((avg_consumption_cng_value IS NULL) = (avg_consumption_cng_unit IS NULL)
            AND (avg_consumption_cng_unit IS NULL
                OR (avg_consumption_cng_unit = 'KM_PER_CUBIC_METER'
                    AND avg_consumption_cng_value BETWEEN 1.0 AND 40.0))),
    ADD CONSTRAINT ck_vehicles_capacity_matches_fuel CHECK (
        (fuel_type_accepted = 'CNG' AND tank_capacity_unit = 'CUBIC_METER')
        OR (fuel_type_accepted <> 'CNG' AND tank_capacity_unit = 'LITER')
    ),
    ADD CONSTRAINT ck_vehicles_consumptions_match_fuel CHECK (
        (fuel_type_accepted = 'FLEX'
            AND avg_consumption_gasoline_value IS NOT NULL
            AND avg_consumption_ethanol_value IS NOT NULL
            AND avg_consumption_diesel_value IS NULL
            AND avg_consumption_cng_value IS NULL)
        OR (fuel_type_accepted = 'GASOLINE'
            AND avg_consumption_gasoline_value IS NOT NULL
            AND avg_consumption_ethanol_value IS NULL
            AND avg_consumption_diesel_value IS NULL
            AND avg_consumption_cng_value IS NULL)
        OR (fuel_type_accepted = 'ETHANOL'
            AND avg_consumption_gasoline_value IS NULL
            AND avg_consumption_ethanol_value IS NOT NULL
            AND avg_consumption_diesel_value IS NULL
            AND avg_consumption_cng_value IS NULL)
        OR (fuel_type_accepted = 'DIESEL'
            AND avg_consumption_gasoline_value IS NULL
            AND avg_consumption_ethanol_value IS NULL
            AND avg_consumption_diesel_value IS NOT NULL
            AND avg_consumption_cng_value IS NULL)
        OR (fuel_type_accepted = 'CNG'
            AND avg_consumption_gasoline_value IS NULL
            AND avg_consumption_ethanol_value IS NULL
            AND avg_consumption_diesel_value IS NULL
            AND avg_consumption_cng_value IS NOT NULL)
    );
