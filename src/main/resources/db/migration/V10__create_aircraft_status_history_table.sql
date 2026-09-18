CREATE TABLE aircraft_status_history (
    id UUID PRIMARY KEY,
    aircraft_id UUID NOT NULL,
    previous_status VARCHAR(20),
    new_status VARCHAR(20) NOT NULL,
    source VARCHAR(30) NOT NULL,
    reason VARCHAR(500),
    changed_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT fk_aircraft_status_history_aircraft FOREIGN KEY (aircraft_id) REFERENCES aircraft (id)
);

CREATE INDEX idx_aircraft_status_history_aircraft_id ON aircraft_status_history (aircraft_id);
