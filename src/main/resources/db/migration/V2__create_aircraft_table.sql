CREATE TABLE aircraft (
    id UUID PRIMARY KEY,
    registration_number VARCHAR(20) NOT NULL,
    manufacturer VARCHAR(255) NOT NULL,
    model VARCHAR(255) NOT NULL,
    seat_capacity INTEGER NOT NULL,
    status VARCHAR(20) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL
);

ALTER TABLE aircraft ADD CONSTRAINT uk_aircraft_registration_number UNIQUE (registration_number);
