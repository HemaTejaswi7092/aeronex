CREATE TABLE flight_status_history (
    id UUID PRIMARY KEY,
    flight_id UUID NOT NULL,
    previous_status VARCHAR(20),
    new_status VARCHAR(20) NOT NULL,
    source VARCHAR(30) NOT NULL,
    reason VARCHAR(500),
    changed_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT fk_flight_status_history_flight FOREIGN KEY (flight_id) REFERENCES flights (id)
);

CREATE INDEX idx_flight_status_history_flight_id ON flight_status_history (flight_id);
