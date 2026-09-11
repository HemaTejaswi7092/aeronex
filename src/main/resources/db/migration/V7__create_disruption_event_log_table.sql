CREATE TABLE disruption_event_log (
    id UUID PRIMARY KEY,
    event_id UUID NOT NULL,
    disruption_id UUID NOT NULL,
    flight_id UUID NOT NULL,
    event_type VARCHAR(100) NOT NULL,
    occurred_at TIMESTAMP WITH TIME ZONE NOT NULL,
    summary VARCHAR(500) NOT NULL,
    recorded_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE INDEX idx_disruption_event_log_disruption_id ON disruption_event_log (disruption_id);
CREATE INDEX idx_disruption_event_log_event_id ON disruption_event_log (event_id);
