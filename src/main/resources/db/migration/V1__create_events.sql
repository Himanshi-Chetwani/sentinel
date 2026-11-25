CREATE TABLE events (
    id               UUID         PRIMARY KEY,
    customer_id      VARCHAR(64)  NOT NULL,
    type             VARCHAR(16)  NOT NULL,
    event_time       TIMESTAMPTZ  NOT NULL,
    source_ip        VARCHAR(45)  NOT NULL,
    known_bad_ip     BOOLEAN      NOT NULL,
    username         VARCHAR(256),
    success          BOOLEAN,
    destination_ip   VARCHAR(45),
    destination_port INTEGER,
    action           VARCHAR(5),
    stored_at        TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE INDEX idx_events_customer_type_time ON events (customer_id, type, event_time);
