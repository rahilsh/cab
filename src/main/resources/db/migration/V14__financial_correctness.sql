ALTER TABLE payments
    ADD COLUMN driver_share_minor bigint,
    ADD COLUMN platform_share_minor bigint,
    ADD COLUMN commission_basis_points integer,
    ADD CONSTRAINT chk_payment_capture_split CHECK (
        (driver_share_minor IS NULL AND platform_share_minor IS NULL
            AND commission_basis_points IS NULL)
        OR (driver_share_minor >= 0 AND platform_share_minor >= 0
            AND commission_basis_points BETWEEN 0 AND 10000
            AND driver_share_minor + platform_share_minor = captured_minor
            AND state = 'CAPTURED'));

ALTER TABLE refunds
    ADD COLUMN driver_share_minor bigint,
    ADD COLUMN platform_share_minor bigint,
    ADD CONSTRAINT chk_refund_split CHECK (
        (driver_share_minor IS NULL AND platform_share_minor IS NULL)
        OR (driver_share_minor >= 0 AND platform_share_minor >= 0
            AND driver_share_minor + platform_share_minor = amount_minor
            AND state = 'SUCCEEDED'));

ALTER TABLE payouts
    ADD COLUMN payment_account_id uuid,
    ADD COLUMN provider_version bigint NOT NULL DEFAULT 0,
    ADD COLUMN failure_code varchar(120);

UPDATE payouts payout
SET payment_account_id = (
    SELECT payment_account.id
    FROM payment_accounts payment_account
    WHERE payment_account.tenant_id = payout.tenant_id
    ORDER BY payment_account.active DESC, payment_account.created_at, payment_account.id
    LIMIT 1);

ALTER TABLE payouts
    ALTER COLUMN payment_account_id SET NOT NULL,
    ADD CONSTRAINT fk_payout_payment_account
        FOREIGN KEY (tenant_id, payment_account_id)
        REFERENCES payment_accounts (tenant_id, id),
    ADD CONSTRAINT chk_payout_provider_version CHECK (provider_version >= 0);

CREATE TABLE payout_attempts (
    id uuid PRIMARY KEY,
    tenant_id uuid NOT NULL,
    payout_id uuid NOT NULL,
    idempotency_key varchar(255) NOT NULL,
    state varchar(32) NOT NULL,
    provider_request_id varchar(255),
    failure_code varchar(120),
    created_at timestamptz NOT NULL,
    completed_at timestamptz,
    CONSTRAINT uq_payout_attempt_tenant_id UNIQUE (tenant_id, id),
    CONSTRAINT uq_payout_attempt_payout UNIQUE (tenant_id, payout_id),
    CONSTRAINT fk_payout_attempt_payout FOREIGN KEY (tenant_id, payout_id)
        REFERENCES payouts (tenant_id, id),
    CONSTRAINT chk_payout_attempt_state
        CHECK (state IN ('PENDING', 'PROCESSING', 'SUCCEEDED', 'FAILED'))
);

ALTER TABLE payout_attempts ENABLE ROW LEVEL SECURITY;
ALTER TABLE payout_attempts FORCE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation ON payout_attempts USING
    (tenant_id = nullif(current_setting('app.tenant_id', true), '')::uuid)
WITH CHECK
    (tenant_id = nullif(current_setting('app.tenant_id', true), '')::uuid);

CREATE UNIQUE INDEX uq_payout_provider_id
    ON payouts (payment_account_id, provider_payout_id)
    WHERE provider_payout_id IS NOT NULL;

ALTER TABLE provider_events
    ADD COLUMN payment_id uuid,
    ADD COLUMN refund_id uuid,
    ADD COLUMN payout_id uuid,
    ADD CONSTRAINT chk_provider_event_object_type CHECK (
        (payment_id IS NULL AND refund_id IS NULL AND payout_id IS NULL)
        OR (event_type IN ('CAPTURE_SUCCEEDED', 'CAPTURE_FAILED')
            AND payment_id IS NOT NULL AND refund_id IS NULL AND payout_id IS NULL)
        OR (event_type IN ('REFUND_SUCCEEDED', 'REFUND_FAILED')
            AND payment_id IS NOT NULL AND refund_id IS NOT NULL AND payout_id IS NULL)
        OR (event_type IN ('PAYOUT_SUCCEEDED', 'PAYOUT_FAILED')
            AND payment_id IS NULL AND refund_id IS NULL AND payout_id IS NOT NULL));

CREATE INDEX idx_provider_events_payout
    ON provider_events (tenant_id, payout_id, provider_version)
    WHERE payout_id IS NOT NULL;

CREATE OR REPLACE FUNCTION reject_payment_identity_mutation() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    IF NEW.tenant_id IS DISTINCT FROM OLD.tenant_id
       OR NEW.ride_id IS DISTINCT FROM OLD.ride_id
       OR NEW.rider_account_id IS DISTINCT FROM OLD.rider_account_id
       OR NEW.amount_minor IS DISTINCT FROM OLD.amount_minor
       OR NEW.currency IS DISTINCT FROM OLD.currency THEN
        RAISE EXCEPTION 'payment tenant, ride, rider, amount, and currency are immutable';
    END IF;
    IF OLD.driver_share_minor IS NOT NULL
       AND (NEW.driver_share_minor IS DISTINCT FROM OLD.driver_share_minor
         OR NEW.platform_share_minor IS DISTINCT FROM OLD.platform_share_minor
         OR NEW.commission_basis_points IS DISTINCT FROM OLD.commission_basis_points) THEN
        RAISE EXCEPTION 'captured payment split is immutable';
    END IF;
    IF OLD.driver_share_minor IS NULL
       AND (OLD.platform_share_minor IS NOT NULL OR OLD.commission_basis_points IS NOT NULL) THEN
        RAISE EXCEPTION 'payment split is inconsistent';
    END IF;
    RETURN NEW;
END;
$$;

CREATE FUNCTION reject_refund_split_mutation() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    IF NEW.tenant_id IS DISTINCT FROM OLD.tenant_id
       OR NEW.payment_id IS DISTINCT FROM OLD.payment_id
       OR NEW.amount_minor IS DISTINCT FROM OLD.amount_minor
       OR NEW.currency IS DISTINCT FROM OLD.currency THEN
        RAISE EXCEPTION 'refund tenant, payment, amount, and currency are immutable';
    END IF;
    IF OLD.driver_share_minor IS NOT NULL
       AND (NEW.driver_share_minor IS DISTINCT FROM OLD.driver_share_minor
         OR NEW.platform_share_minor IS DISTINCT FROM OLD.platform_share_minor) THEN
        RAISE EXCEPTION 'successful refund split is immutable';
    END IF;
    RETURN NEW;
END;
$$;

CREATE TRIGGER refund_split_immutable BEFORE UPDATE ON refunds
FOR EACH ROW EXECUTE FUNCTION reject_refund_split_mutation();
