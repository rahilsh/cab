ALTER TABLE outbox_events ADD COLUMN lease_token uuid;

ALTER TABLE outbox_events DROP CONSTRAINT chk_outbox_lease;
ALTER TABLE outbox_events ADD CONSTRAINT chk_outbox_lease CHECK (
    (status = 'PROCESSING' AND lease_token IS NOT NULL
        AND lease_started_at IS NOT NULL AND lease_expires_at > lease_started_at)
    OR (status <> 'PROCESSING' AND lease_token IS NULL
        AND lease_started_at IS NULL AND lease_expires_at IS NULL)
);

ALTER TABLE notification_deliveries
    ADD COLUMN lease_token uuid,
    ADD COLUMN lease_started_at timestamptz,
    ADD COLUMN lease_expires_at timestamptz,
    ADD COLUMN next_attempt_at timestamptz,
    ADD COLUMN body text;

UPDATE notification_deliveries
SET status = CASE WHEN status IN ('PROCESSING', 'FAILED') THEN 'RETRY' ELSE status END,
    next_attempt_at = created_at,
    body = event_type;

ALTER TABLE notification_deliveries ALTER COLUMN next_attempt_at SET NOT NULL;
ALTER TABLE notification_deliveries ALTER COLUMN body SET NOT NULL;
ALTER TABLE notification_deliveries DROP CONSTRAINT chk_notification_delivery_status;
ALTER TABLE notification_deliveries ADD CONSTRAINT chk_notification_delivery_status
    CHECK (status IN ('PENDING', 'PROCESSING', 'DELIVERED', 'SKIPPED', 'RETRY', 'FAILED'));
ALTER TABLE notification_deliveries ADD CONSTRAINT chk_notification_delivery_lease CHECK (
    (status = 'PROCESSING' AND lease_token IS NOT NULL
        AND lease_started_at IS NOT NULL AND lease_expires_at > lease_started_at)
    OR (status <> 'PROCESSING' AND lease_token IS NULL
        AND lease_started_at IS NULL AND lease_expires_at IS NULL)
);
DROP INDEX idx_notification_delivery_status;
CREATE INDEX idx_notification_delivery_due
    ON notification_deliveries (tenant_id, status, next_attempt_at, lease_expires_at);

ALTER TABLE webhook_deliveries
    ADD COLUMN lease_token uuid,
    ADD COLUMN lease_started_at timestamptz,
    ADD COLUMN lease_expires_at timestamptz;

UPDATE webhook_deliveries
SET status = 'RETRY'
WHERE status = 'PROCESSING';

ALTER TABLE webhook_deliveries ADD CONSTRAINT chk_webhook_delivery_lease CHECK (
    (status = 'PROCESSING' AND lease_token IS NOT NULL
        AND lease_started_at IS NOT NULL AND lease_expires_at > lease_started_at)
    OR (status <> 'PROCESSING' AND lease_token IS NULL
        AND lease_started_at IS NULL AND lease_expires_at IS NULL)
);

CREATE OR REPLACE FUNCTION reject_webhook_snapshot_mutation() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    IF NEW.tenant_id IS DISTINCT FROM OLD.tenant_id
       OR NEW.subscription_id IS DISTINCT FROM OLD.subscription_id
       OR NEW.event_id IS DISTINCT FROM OLD.event_id
       OR NEW.event_type IS DISTINCT FROM OLD.event_type
       OR NEW.event_version IS DISTINCT FROM OLD.event_version
       OR NEW.payload IS DISTINCT FROM OLD.payload
       OR NEW.created_at IS DISTINCT FROM OLD.created_at THEN
        RAISE EXCEPTION 'webhook delivery event snapshot is immutable';
    END IF;
    RETURN NEW;
END;
$$;
