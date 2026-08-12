CREATE TABLE idempotency_records (
    id uuid PRIMARY KEY,
    tenant_id uuid NOT NULL,
    actor_account_id uuid NOT NULL,
    operation varchar(120) NOT NULL,
    idempotency_key varchar(255) NOT NULL,
    request_hash varchar(64) NOT NULL,
    state varchar(32) NOT NULL,
    resource_type varchar(120),
    resource_id uuid,
    http_status integer,
    safe_response jsonb,
    created_at timestamptz NOT NULL,
    expires_at timestamptz NOT NULL,
    updated_at timestamptz NOT NULL,
    CONSTRAINT uq_idempotency_tenant_id UNIQUE (tenant_id, id),
    CONSTRAINT uq_idempotency_scope
        UNIQUE (tenant_id, actor_account_id, operation, idempotency_key),
    CONSTRAINT fk_idempotency_membership
        FOREIGN KEY (tenant_id, actor_account_id)
        REFERENCES tenant_memberships (tenant_id, user_account_id),
    CONSTRAINT chk_idempotency_hash CHECK (request_hash ~ '^[0-9a-f]{64}$'),
    CONSTRAINT chk_idempotency_state
        CHECK (state IN ('IN_PROGRESS', 'COMPLETED', 'FAILED')),
    CONSTRAINT chk_idempotency_expiry CHECK (expires_at > created_at),
    CONSTRAINT chk_idempotency_completion CHECK (
        (state = 'COMPLETED' AND resource_type IS NOT NULL AND resource_id IS NOT NULL
            AND http_status BETWEEN 200 AND 299)
        OR (state <> 'COMPLETED' AND resource_type IS NULL AND resource_id IS NULL
            AND http_status IS NULL AND safe_response IS NULL)
    )
);

CREATE TABLE outbox_events (
    id uuid PRIMARY KEY,
    tenant_id uuid NOT NULL REFERENCES tenants(id),
    aggregate_type varchar(120) NOT NULL,
    aggregate_id uuid NOT NULL,
    aggregate_version bigint NOT NULL,
    event_type varchar(160) NOT NULL,
    event_version integer NOT NULL,
    payload jsonb NOT NULL,
    occurred_at timestamptz NOT NULL,
    available_at timestamptz NOT NULL,
    correlation_id varchar(128),
    causation_id uuid,
    status varchar(32) NOT NULL,
    attempts integer NOT NULL DEFAULT 0,
    lease_started_at timestamptz,
    lease_expires_at timestamptz,
    published_at timestamptz,
    last_error varchar(500),
    CONSTRAINT uq_outbox_tenant_id UNIQUE (tenant_id, id),
    CONSTRAINT chk_outbox_aggregate_version CHECK (aggregate_version >= 0),
    CONSTRAINT chk_outbox_event_version CHECK (event_version > 0),
    CONSTRAINT chk_outbox_status
        CHECK (status IN ('PENDING', 'PROCESSING', 'PUBLISHED', 'FAILED')),
    CONSTRAINT chk_outbox_attempts CHECK (attempts >= 0),
    CONSTRAINT chk_outbox_lease CHECK (
        (status = 'PROCESSING' AND lease_started_at IS NOT NULL AND lease_expires_at > lease_started_at)
        OR (status <> 'PROCESSING' AND lease_started_at IS NULL AND lease_expires_at IS NULL)
    ),
    CONSTRAINT chk_outbox_published CHECK (
        (status = 'PUBLISHED' AND published_at IS NOT NULL)
        OR (status <> 'PUBLISHED' AND published_at IS NULL)
    )
);

CREATE TABLE inbox_receipts (
    id uuid PRIMARY KEY,
    tenant_id uuid NOT NULL REFERENCES tenants(id),
    consumer varchar(160) NOT NULL,
    event_id uuid NOT NULL,
    received_at timestamptz NOT NULL,
    CONSTRAINT uq_inbox_tenant_id UNIQUE (tenant_id, id),
    CONSTRAINT uq_inbox_receipt UNIQUE (tenant_id, consumer, event_id)
);

CREATE TABLE audit_events (
    id uuid PRIMARY KEY,
    tenant_id uuid NOT NULL,
    actor_account_id uuid NOT NULL,
    action varchar(160) NOT NULL,
    target_type varchar(120) NOT NULL,
    target_id uuid,
    outcome varchar(32) NOT NULL,
    correlation_id varchar(128),
    summary jsonb NOT NULL,
    occurred_at timestamptz NOT NULL,
    CONSTRAINT uq_audit_tenant_id UNIQUE (tenant_id, id),
    CONSTRAINT fk_audit_membership
        FOREIGN KEY (tenant_id, actor_account_id)
        REFERENCES tenant_memberships (tenant_id, user_account_id),
    CONSTRAINT chk_audit_outcome CHECK (outcome IN ('SUCCESS', 'FAILURE', 'DENIED'))
);

CREATE INDEX idx_idempotency_tenant_actor_expiry
    ON idempotency_records (tenant_id, actor_account_id, expires_at);
CREATE INDEX idx_outbox_tenant_poll
    ON outbox_events (tenant_id, status, available_at, lease_expires_at, occurred_at);
CREATE INDEX idx_outbox_tenant_aggregate
    ON outbox_events (tenant_id, aggregate_type, aggregate_id, aggregate_version);
CREATE INDEX idx_inbox_tenant_received
    ON inbox_receipts (tenant_id, received_at DESC);
CREATE INDEX idx_audit_tenant_occurred
    ON audit_events (tenant_id, occurred_at DESC, id DESC);
CREATE INDEX idx_audit_tenant_target
    ON audit_events (tenant_id, target_type, target_id, occurred_at DESC);

CREATE FUNCTION reject_audit_event_mutation() RETURNS trigger
LANGUAGE plpgsql AS $$
BEGIN
    RAISE EXCEPTION 'audit_events is append-only';
END;
$$;

CREATE TRIGGER audit_events_immutable
BEFORE UPDATE OR DELETE ON audit_events
FOR EACH ROW EXECUTE FUNCTION reject_audit_event_mutation();
