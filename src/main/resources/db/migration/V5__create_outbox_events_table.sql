CREATE TABLE outbox_events (
    id UUID PRIMARY KEY,
    topic VARCHAR(200) NOT NULL,
    aggregate_id UUID NOT NULL,
    event_type VARCHAR(100) NOT NULL,
    event_version INTEGER NOT NULL,
    payload VARCHAR(4000) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    published_at TIMESTAMP WITH TIME ZONE
);

CREATE INDEX idx_outbox_events_published_at ON outbox_events (published_at);
CREATE INDEX idx_outbox_events_created_at ON outbox_events (created_at);
