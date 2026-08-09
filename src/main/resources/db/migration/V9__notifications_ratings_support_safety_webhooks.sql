CREATE TABLE notification_preferences (
    id uuid PRIMARY KEY,
    tenant_id uuid NOT NULL,
    recipient_account_id uuid NOT NULL,
    event_type varchar(160) NOT NULL,
    channel varchar(32) NOT NULL,
    enabled boolean NOT NULL,
    updated_at timestamptz NOT NULL,
    CONSTRAINT uq_notification_preference_tenant_id UNIQUE (tenant_id, id),
    CONSTRAINT uq_notification_preference UNIQUE
        (tenant_id, recipient_account_id, event_type, channel),
    CONSTRAINT fk_notification_preference_recipient
        FOREIGN KEY (tenant_id, recipient_account_id)
        REFERENCES tenant_memberships (tenant_id, user_account_id),
    CONSTRAINT chk_notification_preference_channel CHECK (channel IN ('LOCAL'))
);

CREATE TABLE notification_deliveries (
    id uuid PRIMARY KEY,
    tenant_id uuid NOT NULL,
    recipient_account_id uuid NOT NULL,
    event_id uuid NOT NULL,
    event_type varchar(160) NOT NULL,
    channel varchar(32) NOT NULL,
    template_key varchar(120) NOT NULL,
    template_version integer NOT NULL,
    status varchar(32) NOT NULL,
    created_at timestamptz NOT NULL,
    delivered_at timestamptz,
    CONSTRAINT uq_notification_delivery_tenant_id UNIQUE (tenant_id, id),
    CONSTRAINT uq_notification_delivery UNIQUE
        (tenant_id, recipient_account_id, event_id, channel, template_key, template_version),
    CONSTRAINT fk_notification_delivery_recipient
        FOREIGN KEY (tenant_id, recipient_account_id)
        REFERENCES tenant_memberships (tenant_id, user_account_id),
    CONSTRAINT fk_notification_delivery_event
        FOREIGN KEY (tenant_id, event_id) REFERENCES outbox_events (tenant_id, id),
    CONSTRAINT chk_notification_delivery_channel CHECK (channel IN ('LOCAL')),
    CONSTRAINT chk_notification_template_version CHECK (template_version > 0),
    CONSTRAINT chk_notification_delivery_status
        CHECK (status IN ('PENDING', 'PROCESSING', 'DELIVERED', 'SKIPPED', 'FAILED'))
);

CREATE TABLE notification_attempts (
    id uuid PRIMARY KEY,
    tenant_id uuid NOT NULL,
    delivery_id uuid NOT NULL,
    attempt_number integer NOT NULL,
    status varchar(32) NOT NULL,
    provider_message_id varchar(255),
    error_code varchar(120),
    attempted_at timestamptz NOT NULL,
    CONSTRAINT uq_notification_attempt_tenant_id UNIQUE (tenant_id, id),
    CONSTRAINT uq_notification_attempt UNIQUE (tenant_id, delivery_id, attempt_number),
    CONSTRAINT fk_notification_attempt_delivery
        FOREIGN KEY (tenant_id, delivery_id)
        REFERENCES notification_deliveries (tenant_id, id),
    CONSTRAINT chk_notification_attempt_number CHECK (attempt_number > 0),
    CONSTRAINT chk_notification_attempt_status CHECK (status IN ('SUCCEEDED', 'FAILED'))
);

CREATE TABLE ratings (
    id uuid PRIMARY KEY,
    tenant_id uuid NOT NULL,
    ride_id uuid NOT NULL,
    reviewer_account_id uuid NOT NULL,
    reviewee_account_id uuid NOT NULL,
    reviewer_role varchar(32) NOT NULL,
    reviewee_role varchar(32) NOT NULL,
    score smallint NOT NULL,
    comment varchar(1000),
    moderation_status varchar(32) NOT NULL,
    created_at timestamptz NOT NULL,
    moderated_at timestamptz,
    CONSTRAINT uq_rating_tenant_id UNIQUE (tenant_id, id),
    CONSTRAINT uq_rating_reviewer_per_ride UNIQUE (tenant_id, ride_id, reviewer_account_id),
    CONSTRAINT fk_rating_ride FOREIGN KEY (tenant_id, ride_id) REFERENCES rides (tenant_id, id),
    CONSTRAINT fk_rating_reviewer FOREIGN KEY (tenant_id, reviewer_account_id)
        REFERENCES tenant_memberships (tenant_id, user_account_id),
    CONSTRAINT fk_rating_reviewee FOREIGN KEY (tenant_id, reviewee_account_id)
        REFERENCES tenant_memberships (tenant_id, user_account_id),
    CONSTRAINT chk_rating_roles CHECK (
        (reviewer_role = 'RIDER' AND reviewee_role = 'DRIVER')
        OR (reviewer_role = 'DRIVER' AND reviewee_role = 'RIDER')),
    CONSTRAINT chk_rating_score CHECK (score BETWEEN 1 AND 5),
    CONSTRAINT chk_rating_moderation CHECK
        (moderation_status IN ('PUBLISHED', 'HIDDEN', 'FLAGGED'))
);

CREATE TABLE support_cases (
    id uuid PRIMARY KEY,
    tenant_id uuid NOT NULL,
    opened_by_account_id uuid NOT NULL,
    ride_id uuid,
    subject varchar(160) NOT NULL,
    state varchar(32) NOT NULL,
    priority varchar(32) NOT NULL,
    created_at timestamptz NOT NULL,
    updated_at timestamptz NOT NULL,
    CONSTRAINT uq_support_case_tenant_id UNIQUE (tenant_id, id),
    CONSTRAINT fk_support_case_opener FOREIGN KEY (tenant_id, opened_by_account_id)
        REFERENCES tenant_memberships (tenant_id, user_account_id),
    CONSTRAINT fk_support_case_ride FOREIGN KEY (tenant_id, ride_id) REFERENCES rides (tenant_id, id),
    CONSTRAINT chk_support_case_subject CHECK (length(trim(subject)) BETWEEN 1 AND 160),
    CONSTRAINT chk_support_case_state CHECK (state IN ('OPEN', 'IN_PROGRESS', 'WAITING', 'RESOLVED', 'CLOSED')),
    CONSTRAINT chk_support_case_priority CHECK (priority IN ('LOW', 'NORMAL', 'HIGH', 'URGENT'))
);

CREATE TABLE support_messages (
    id uuid PRIMARY KEY,
    tenant_id uuid NOT NULL,
    case_id uuid NOT NULL,
    author_account_id uuid NOT NULL,
    body varchar(4000) NOT NULL,
    internal boolean NOT NULL DEFAULT false,
    created_at timestamptz NOT NULL,
    CONSTRAINT uq_support_message_tenant_id UNIQUE (tenant_id, id),
    CONSTRAINT fk_support_message_case FOREIGN KEY (tenant_id, case_id)
        REFERENCES support_cases (tenant_id, id),
    CONSTRAINT fk_support_message_author FOREIGN KEY (tenant_id, author_account_id)
        REFERENCES tenant_memberships (tenant_id, user_account_id),
    CONSTRAINT chk_support_message_body CHECK (length(trim(body)) BETWEEN 1 AND 4000)
);

CREATE TABLE support_assignments (
    id uuid PRIMARY KEY,
    tenant_id uuid NOT NULL,
    case_id uuid NOT NULL,
    assignee_account_id uuid NOT NULL,
    assigned_by_account_id uuid NOT NULL,
    active boolean NOT NULL,
    assigned_at timestamptz NOT NULL,
    ended_at timestamptz,
    CONSTRAINT uq_support_assignment_tenant_id UNIQUE (tenant_id, id),
    CONSTRAINT fk_support_assignment_case FOREIGN KEY (tenant_id, case_id)
        REFERENCES support_cases (tenant_id, id),
    CONSTRAINT fk_support_assignment_assignee FOREIGN KEY (tenant_id, assignee_account_id)
        REFERENCES tenant_memberships (tenant_id, user_account_id),
    CONSTRAINT fk_support_assignment_actor FOREIGN KEY (tenant_id, assigned_by_account_id)
        REFERENCES tenant_memberships (tenant_id, user_account_id),
    CONSTRAINT chk_support_assignment_end CHECK
        ((active AND ended_at IS NULL) OR (NOT active AND ended_at IS NOT NULL))
);

CREATE UNIQUE INDEX uq_active_support_assignment
    ON support_assignments (tenant_id, case_id) WHERE active;

CREATE TABLE support_case_state_history (
    id uuid PRIMARY KEY,
    tenant_id uuid NOT NULL,
    case_id uuid NOT NULL,
    from_state varchar(32),
    to_state varchar(32) NOT NULL,
    actor_account_id uuid NOT NULL,
    reason varchar(500),
    occurred_at timestamptz NOT NULL,
    CONSTRAINT uq_support_history_tenant_id UNIQUE (tenant_id, id),
    CONSTRAINT fk_support_history_case FOREIGN KEY (tenant_id, case_id)
        REFERENCES support_cases (tenant_id, id),
    CONSTRAINT fk_support_history_actor FOREIGN KEY (tenant_id, actor_account_id)
        REFERENCES tenant_memberships (tenant_id, user_account_id),
    CONSTRAINT chk_support_history_state CHECK
        (to_state IN ('OPEN', 'IN_PROGRESS', 'WAITING', 'RESOLVED', 'CLOSED'))
);

CREATE TABLE safety_incidents (
    id uuid PRIMARY KEY,
    tenant_id uuid NOT NULL,
    ride_id uuid NOT NULL,
    reported_by_account_id uuid NOT NULL,
    category varchar(64) NOT NULL,
    description varchar(2000) NOT NULL,
    state varchar(32) NOT NULL,
    severity varchar(32) NOT NULL,
    created_at timestamptz NOT NULL,
    updated_at timestamptz NOT NULL,
    CONSTRAINT uq_safety_incident_tenant_id UNIQUE (tenant_id, id),
    CONSTRAINT fk_safety_incident_ride FOREIGN KEY (tenant_id, ride_id) REFERENCES rides (tenant_id, id),
    CONSTRAINT fk_safety_incident_reporter FOREIGN KEY (tenant_id, reported_by_account_id)
        REFERENCES tenant_memberships (tenant_id, user_account_id),
    CONSTRAINT chk_safety_category CHECK (category ~ '^[A-Z][A-Z0-9_]{0,63}$'),
    CONSTRAINT chk_safety_description CHECK (length(trim(description)) BETWEEN 1 AND 2000),
    CONSTRAINT chk_safety_state CHECK (state IN ('REPORTED', 'TRIAGED', 'INVESTIGATING', 'RESOLVED', 'CLOSED')),
    CONSTRAINT chk_safety_severity CHECK (severity IN ('UNASSESSED', 'LOW', 'MEDIUM', 'HIGH', 'CRITICAL'))
);

CREATE TABLE safety_evidence (
    id uuid PRIMARY KEY,
    tenant_id uuid NOT NULL,
    incident_id uuid NOT NULL,
    submitted_by_account_id uuid NOT NULL,
    object_key varchar(512) NOT NULL,
    media_type varchar(120) NOT NULL,
    size_bytes bigint,
    checksum_sha256 varchar(64),
    created_at timestamptz NOT NULL,
    CONSTRAINT uq_safety_evidence_tenant_id UNIQUE (tenant_id, id),
    CONSTRAINT uq_safety_evidence_object UNIQUE (tenant_id, incident_id, object_key),
    CONSTRAINT fk_safety_evidence_incident FOREIGN KEY (tenant_id, incident_id)
        REFERENCES safety_incidents (tenant_id, id),
    CONSTRAINT fk_safety_evidence_submitter FOREIGN KEY (tenant_id, submitted_by_account_id)
        REFERENCES tenant_memberships (tenant_id, user_account_id),
    CONSTRAINT chk_safety_evidence_key CHECK (
        length(object_key) BETWEEN 1 AND 512
        AND object_key ~ '^[A-Za-z0-9][A-Za-z0-9._/-]*$'
        AND object_key !~ '(^|/)\.\.(/|$)' AND object_key !~ '^[a-zA-Z][a-zA-Z0-9+.-]*:'),
    CONSTRAINT chk_safety_evidence_size CHECK (size_bytes IS NULL OR size_bytes >= 0),
    CONSTRAINT chk_safety_evidence_checksum CHECK
        (checksum_sha256 IS NULL OR checksum_sha256 ~ '^[0-9a-f]{64}$')
);

CREATE TABLE safety_incident_actions (
    id uuid PRIMARY KEY,
    tenant_id uuid NOT NULL,
    incident_id uuid NOT NULL,
    actor_account_id uuid NOT NULL,
    action varchar(64) NOT NULL,
    from_state varchar(32),
    to_state varchar(32),
    redacted_note varchar(500),
    occurred_at timestamptz NOT NULL,
    CONSTRAINT uq_safety_action_tenant_id UNIQUE (tenant_id, id),
    CONSTRAINT fk_safety_action_incident FOREIGN KEY (tenant_id, incident_id)
        REFERENCES safety_incidents (tenant_id, id),
    CONSTRAINT fk_safety_action_actor FOREIGN KEY (tenant_id, actor_account_id)
        REFERENCES tenant_memberships (tenant_id, user_account_id)
);

CREATE TABLE webhook_subscriptions (
    id uuid PRIMARY KEY,
    tenant_id uuid NOT NULL REFERENCES tenants(id),
    url varchar(2048) NOT NULL,
    secret_reference varchar(255) NOT NULL,
    event_filters jsonb NOT NULL,
    enabled boolean NOT NULL,
    created_at timestamptz NOT NULL,
    updated_at timestamptz NOT NULL,
    deleted_at timestamptz,
    version bigint NOT NULL DEFAULT 0,
    CONSTRAINT uq_webhook_subscription_tenant_id UNIQUE (tenant_id, id),
    CONSTRAINT chk_webhook_https CHECK (url ~ '^https://[^[:space:]]+$'),
    CONSTRAINT chk_webhook_secret_reference CHECK
        (secret_reference ~ '^env:[A-Za-z_][A-Za-z0-9_]*$'),
    CONSTRAINT chk_webhook_filters CHECK
        (jsonb_typeof(event_filters) = 'array' AND jsonb_array_length(event_filters) > 0)
);

CREATE TABLE webhook_deliveries (
    id uuid PRIMARY KEY,
    tenant_id uuid NOT NULL,
    subscription_id uuid NOT NULL,
    event_id uuid NOT NULL,
    event_type varchar(160) NOT NULL,
    event_version integer NOT NULL,
    payload jsonb NOT NULL,
    signature_timestamp timestamptz NOT NULL,
    status varchar(32) NOT NULL,
    attempt_count integer NOT NULL DEFAULT 0,
    next_attempt_at timestamptz NOT NULL,
    delivered_at timestamptz,
    created_at timestamptz NOT NULL,
    CONSTRAINT uq_webhook_delivery_tenant_id UNIQUE (tenant_id, id),
    CONSTRAINT uq_webhook_delivery UNIQUE (tenant_id, subscription_id, event_id),
    CONSTRAINT fk_webhook_delivery_subscription FOREIGN KEY (tenant_id, subscription_id)
        REFERENCES webhook_subscriptions (tenant_id, id),
    CONSTRAINT fk_webhook_delivery_event FOREIGN KEY (tenant_id, event_id)
        REFERENCES outbox_events (tenant_id, id),
    CONSTRAINT chk_webhook_delivery_event_version CHECK (event_version > 0),
    CONSTRAINT chk_webhook_delivery_status CHECK
        (status IN ('PENDING', 'PROCESSING', 'DELIVERED', 'RETRY', 'FAILED')),
    CONSTRAINT chk_webhook_delivery_attempts CHECK (attempt_count >= 0)
);

CREATE TABLE webhook_attempts (
    id uuid PRIMARY KEY,
    tenant_id uuid NOT NULL,
    delivery_id uuid NOT NULL,
    attempt_number integer NOT NULL,
    status varchar(32) NOT NULL,
    response_status integer,
    error_code varchar(120),
    attempted_at timestamptz NOT NULL,
    CONSTRAINT uq_webhook_attempt_tenant_id UNIQUE (tenant_id, id),
    CONSTRAINT uq_webhook_attempt UNIQUE (tenant_id, delivery_id, attempt_number),
    CONSTRAINT fk_webhook_attempt_delivery FOREIGN KEY (tenant_id, delivery_id)
        REFERENCES webhook_deliveries (tenant_id, id),
    CONSTRAINT chk_webhook_attempt_number CHECK (attempt_number > 0),
    CONSTRAINT chk_webhook_attempt_status CHECK (status IN ('SUCCEEDED', 'FAILED'))
);

CREATE INDEX idx_notification_delivery_status ON notification_deliveries (tenant_id, status, created_at);
CREATE INDEX idx_rating_reviewee ON ratings (tenant_id, reviewee_account_id, created_at DESC);
CREATE INDEX idx_support_case_owner ON support_cases (tenant_id, opened_by_account_id, updated_at DESC);
CREATE INDEX idx_support_case_state ON support_cases (tenant_id, state, priority, updated_at DESC);
CREATE INDEX idx_safety_incident_state ON safety_incidents (tenant_id, state, created_at DESC);
CREATE INDEX idx_webhook_delivery_retry ON webhook_deliveries (tenant_id, status, next_attempt_at);

CREATE FUNCTION reject_v9_append_only_mutation() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    RAISE EXCEPTION '% is append-only', TG_TABLE_NAME;
END;
$$;
CREATE TRIGGER support_messages_immutable BEFORE UPDATE OR DELETE ON support_messages
FOR EACH ROW EXECUTE FUNCTION reject_v9_append_only_mutation();
CREATE TRIGGER support_history_immutable BEFORE UPDATE OR DELETE ON support_case_state_history
FOR EACH ROW EXECUTE FUNCTION reject_v9_append_only_mutation();
CREATE TRIGGER safety_evidence_immutable BEFORE UPDATE OR DELETE ON safety_evidence
FOR EACH ROW EXECUTE FUNCTION reject_v9_append_only_mutation();
CREATE TRIGGER safety_actions_immutable BEFORE UPDATE OR DELETE ON safety_incident_actions
FOR EACH ROW EXECUTE FUNCTION reject_v9_append_only_mutation();
CREATE TRIGGER webhook_attempts_immutable BEFORE UPDATE OR DELETE ON webhook_attempts
FOR EACH ROW EXECUTE FUNCTION reject_v9_append_only_mutation();

CREATE FUNCTION reject_webhook_snapshot_mutation() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    IF NEW.tenant_id IS DISTINCT FROM OLD.tenant_id
       OR NEW.subscription_id IS DISTINCT FROM OLD.subscription_id
       OR NEW.event_id IS DISTINCT FROM OLD.event_id
       OR NEW.event_type IS DISTINCT FROM OLD.event_type
       OR NEW.event_version IS DISTINCT FROM OLD.event_version
       OR NEW.payload IS DISTINCT FROM OLD.payload
       OR NEW.signature_timestamp IS DISTINCT FROM OLD.signature_timestamp
       OR NEW.created_at IS DISTINCT FROM OLD.created_at THEN
        RAISE EXCEPTION 'webhook delivery event snapshot is immutable';
    END IF;
    RETURN NEW;
END;
$$;
CREATE TRIGGER webhook_delivery_snapshot_immutable BEFORE UPDATE ON webhook_deliveries
FOR EACH ROW EXECUTE FUNCTION reject_webhook_snapshot_mutation();
