CREATE TABLE flights (
    id UUID PRIMARY KEY,
    flight_number VARCHAR(10) NOT NULL,
    origin_airport_id UUID NOT NULL,
    destination_airport_id UUID NOT NULL,
    aircraft_id UUID,
    scheduled_departure_time TIMESTAMP WITH TIME ZONE NOT NULL,
    scheduled_arrival_time TIMESTAMP WITH TIME ZONE NOT NULL,
    actual_departure_time TIMESTAMP WITH TIME ZONE,
    actual_arrival_time TIMESTAMP WITH TIME ZONE,
    status VARCHAR(20) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT fk_flights_origin_airport FOREIGN KEY (origin_airport_id) REFERENCES airports (id),
    CONSTRAINT fk_flights_destination_airport FOREIGN KEY (destination_airport_id) REFERENCES airports (id),
    CONSTRAINT fk_flights_aircraft FOREIGN KEY (aircraft_id) REFERENCES aircraft (id)
);

CREATE INDEX idx_flights_flight_number ON flights (flight_number);
CREATE INDEX idx_flights_origin_airport_id ON flights (origin_airport_id);
CREATE INDEX idx_flights_destination_airport_id ON flights (destination_airport_id);
