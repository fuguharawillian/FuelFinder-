INSERT INTO fuel_types (id, code, name, unit_of_measure, active) VALUES
    (gen_random_uuid(), 'GASOLINE_REGULAR', 'Gasolina Comum', 'R$/litro', TRUE),
    (gen_random_uuid(), 'GASOLINE_PREMIUM', 'Gasolina Aditivada', 'R$/litro', TRUE),
    (gen_random_uuid(), 'ETHANOL', 'Etanol', 'R$/litro', TRUE),
    (gen_random_uuid(), 'DIESEL_S10', 'Diesel S10', 'R$/litro', TRUE),
    (gen_random_uuid(), 'DIESEL_S500', 'Diesel S500', 'R$/litro', TRUE),
    (gen_random_uuid(), 'CNG', 'GNV', 'R$/m³', TRUE);
