CREATE TABLE airports (
    id UUID PRIMARY KEY,
    iata_code VARCHAR(3) NOT NULL,
    icao_code VARCHAR(4),
    name VARCHAR(255) NOT NULL,
    city VARCHAR(255) NOT NULL,
    country VARCHAR(255) NOT NULL,
    timezone VARCHAR(100) NOT NULL,
    latitude NUMERIC(9,6) NOT NULL,
    longitude NUMERIC(9,6) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL
);

ALTER TABLE airports ADD CONSTRAINT uk_airports_iata_code UNIQUE (iata_code);
ALTER TABLE airports ADD CONSTRAINT uk_airports_icao_code UNIQUE (icao_code);
