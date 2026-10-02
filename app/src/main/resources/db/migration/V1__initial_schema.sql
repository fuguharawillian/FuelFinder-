CREATE TABLE users (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    email VARCHAR(255) NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    full_name VARCHAR(255) NOT NULL,
    role VARCHAR(30) NOT NULL DEFAULT 'ROLE_DRIVER',
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW(),
    CONSTRAINT uk_users_email UNIQUE (email),
    CONSTRAINT ck_users_role CHECK (role IN ('ROLE_DRIVER', 'ROLE_ADMIN')),
    CONSTRAINT ck_users_status CHECK (status IN ('ACTIVE', 'INACTIVE', 'BLOCKED'))
);

CREATE TABLE vehicles (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL,
    nickname VARCHAR(50) NOT NULL,
    brand VARCHAR(100) NOT NULL,
    model VARCHAR(100) NOT NULL,
    year_manufacture INTEGER NOT NULL,
    fuel_type_accepted VARCHAR(20) NOT NULL,
    tank_capacity DECIMAL(6,2) NOT NULL,
    avg_consumption_gasoline DECIMAL(5,2),
    avg_consumption_ethanol DECIMAL(5,2),
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW(),
    CONSTRAINT fk_vehicles_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    CONSTRAINT ck_vehicles_fuel_type CHECK (fuel_type_accepted IN ('GASOLINE', 'ETHANOL', 'FLEX', 'DIESEL', 'CNG')),
    CONSTRAINT ck_vehicles_tank_positive CHECK (tank_capacity > 0),
    CONSTRAINT ck_vehicles_year CHECK (year_manufacture >= 1950)
);

CREATE TABLE stations (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    cnpj VARCHAR(18) NOT NULL,
    corporate_name VARCHAR(255) NOT NULL,
    trade_name VARCHAR(255),
    brand VARCHAR(100),
    street VARCHAR(255),
    number VARCHAR(20),
    neighborhood VARCHAR(100),
    city VARCHAR(100) NOT NULL,
    state CHAR(2) NOT NULL,
    postal_code VARCHAR(10),
    latitude DECIMAL(10,7) NOT NULL,
    longitude DECIMAL(10,7) NOT NULL,
    average_rating DECIMAL(3,2) NOT NULL DEFAULT 0.00,
    total_reviews INTEGER NOT NULL DEFAULT 0,
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW(),
    CONSTRAINT uk_stations_cnpj UNIQUE (cnpj),
    CONSTRAINT ck_stations_status CHECK (status IN ('ACTIVE', 'INACTIVE')),
    CONSTRAINT ck_stations_latitude CHECK (latitude BETWEEN -90.0 AND 90.0),
    CONSTRAINT ck_stations_longitude CHECK (longitude BETWEEN -180.0 AND 180.0),
    CONSTRAINT ck_stations_rating CHECK (average_rating BETWEEN 0.00 AND 5.00)
);

CREATE TABLE fuel_types (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    code VARCHAR(50) NOT NULL,
    name VARCHAR(100) NOT NULL,
    unit_of_measure VARCHAR(20) NOT NULL DEFAULT 'R$/litro',
    active BOOLEAN NOT NULL DEFAULT TRUE,
    CONSTRAINT uk_fuel_types_code UNIQUE (code)
);

CREATE TABLE fuel_prices (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    station_id UUID NOT NULL,
    fuel_type_id UUID NOT NULL,
    sale_value DECIMAL(8,3) NOT NULL,
    collection_date DATE NOT NULL,
    data_source VARCHAR(30) NOT NULL DEFAULT 'MANUAL_ADMIN',
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW(),
    CONSTRAINT fk_fuel_prices_station FOREIGN KEY (station_id) REFERENCES stations(id) ON DELETE CASCADE,
    CONSTRAINT fk_fuel_prices_fuel_type FOREIGN KEY (fuel_type_id) REFERENCES fuel_types(id),
    CONSTRAINT uk_fuel_prices_unique UNIQUE (station_id, fuel_type_id, collection_date),
    CONSTRAINT ck_fuel_prices_value_positive CHECK (sale_value > 0),
    CONSTRAINT ck_fuel_prices_source CHECK (data_source IN ('ANP_IMPORT', 'MANUAL_ADMIN'))
);

CREATE TABLE reviews (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL,
    station_id UUID NOT NULL,
    rating INTEGER NOT NULL,
    comment VARCHAR(500),
    status VARCHAR(20) NOT NULL DEFAULT 'APPROVED',
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW(),
    CONSTRAINT fk_reviews_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    CONSTRAINT fk_reviews_station FOREIGN KEY (station_id) REFERENCES stations(id) ON DELETE CASCADE,
    CONSTRAINT uk_reviews_user_station UNIQUE (user_id, station_id),
    CONSTRAINT ck_reviews_rating CHECK (rating BETWEEN 1 AND 5),
    CONSTRAINT ck_reviews_status CHECK (status IN ('APPROVED', 'PENDING', 'REJECTED'))
);

CREATE TABLE anp_import_logs (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    file_name VARCHAR(255) NOT NULL,
    reference_period VARCHAR(20) NOT NULL,
    source_url VARCHAR(500),
    import_start TIMESTAMP NOT NULL,
    import_end TIMESTAMP,
    total_records_read INTEGER NOT NULL DEFAULT 0,
    total_records_imported INTEGER NOT NULL DEFAULT 0,
    status VARCHAR(20) NOT NULL DEFAULT 'FAILED',
    error_details TEXT,
    triggered_by UUID,
    CONSTRAINT fk_anp_logs_user FOREIGN KEY (triggered_by) REFERENCES users(id),
    CONSTRAINT ck_anp_logs_status CHECK (status IN ('SUCCESS', 'PARTIAL', 'FAILED'))
);

CREATE INDEX idx_vehicles_user_id ON vehicles(user_id);
CREATE INDEX idx_stations_status ON stations(status);
CREATE INDEX idx_stations_city_state ON stations(city, state);
CREATE INDEX idx_stations_lat_lng ON stations(latitude, longitude);
CREATE INDEX idx_fuel_prices_station ON fuel_prices(station_id);
CREATE INDEX idx_fuel_prices_fuel_type ON fuel_prices(fuel_type_id);
CREATE INDEX idx_fuel_prices_date ON fuel_prices(collection_date DESC);
CREATE INDEX idx_reviews_station ON reviews(station_id);
CREATE INDEX idx_reviews_user ON reviews(user_id);
CREATE INDEX idx_reviews_status ON reviews(status);
