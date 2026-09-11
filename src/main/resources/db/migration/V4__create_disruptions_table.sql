CREATE TABLE disruptions (
    id UUID PRIMARY KEY,
    flight_id UUID NOT NULL,
    type VARCHAR(30) NOT NULL,
    severity VARCHAR(20) NOT NULL,
    status VARCHAR(20) NOT NULL,
    description VARCHAR(2000) NOT NULL,
    estimated_delay_minutes INTEGER,
    reported_at TIMESTAMP WITH TIME ZONE NOT NULL,
    resolved_at TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT fk_disruptions_flight FOREIGN KEY (flight_id) REFERENCES flights (id)
);

CREATE INDEX idx_disruptions_flight_id ON disruptions (flight_id);
CREATE INDEX idx_disruptions_status ON disruptions (status);
