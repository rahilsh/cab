CREATE TABLE payment_accounts (
    id uuid PRIMARY KEY,
    tenant_id uuid NOT NULL REFERENCES tenants(id),
    provider varchar(64) NOT NULL,
    config_reference varchar(255) NOT NULL,
    webhook_secret_reference varchar(255) NOT NULL,
    active boolean NOT NULL DEFAULT true,
    created_at timestamptz NOT NULL,
    updated_at timestamptz NOT NULL,
    CONSTRAINT uq_payment_account_tenant_id UNIQUE (tenant_id, id),
    CONSTRAINT uq_payment_account_provider UNIQUE (tenant_id, provider),
    CONSTRAINT chk_payment_account_references CHECK (
        config_reference ~ '^(env|vault|aws-sm|gcp-sm):[A-Za-z0-9_./-]+$'
        AND webhook_secret_reference ~ '^(env|vault|aws-sm|gcp-sm):[A-Za-z0-9_./-]+$')
);

CREATE TABLE payments (
    id uuid PRIMARY KEY,
    tenant_id uuid NOT NULL,
    payment_account_id uuid NOT NULL,
    ride_id uuid NOT NULL,
    rider_account_id uuid NOT NULL,
    amount_minor bigint NOT NULL,
    authorized_minor bigint NOT NULL DEFAULT 0,
    captured_minor bigint NOT NULL DEFAULT 0,
    currency varchar(3) NOT NULL,
    state varchar(32) NOT NULL,
    payment_method_token varchar(255),
    provider_payment_id varchar(255),
    provider_version bigint NOT NULL DEFAULT 0,
    version bigint NOT NULL DEFAULT 0,
    failure_code varchar(120),
    created_at timestamptz NOT NULL,
    updated_at timestamptz NOT NULL,
    CONSTRAINT uq_payment_tenant_id UNIQUE (tenant_id, id),
    CONSTRAINT uq_payment_ride UNIQUE (tenant_id, ride_id),
    CONSTRAINT uq_payment_provider_id UNIQUE (payment_account_id, provider_payment_id),
    CONSTRAINT fk_payment_account FOREIGN KEY (tenant_id, payment_account_id)
        REFERENCES payment_accounts (tenant_id, id),
    CONSTRAINT fk_payment_ride FOREIGN KEY (tenant_id, ride_id) REFERENCES rides (tenant_id, id),
    CONSTRAINT fk_payment_rider FOREIGN KEY (tenant_id, rider_account_id)
        REFERENCES tenant_memberships (tenant_id, user_account_id),
    CONSTRAINT chk_payment_amounts CHECK (
        amount_minor > 0 AND authorized_minor >= 0 AND captured_minor >= 0
        AND authorized_minor <= amount_minor AND captured_minor <= authorized_minor),
    CONSTRAINT chk_payment_state CHECK (state IN (
        'CREATED', 'AUTHORIZATION_PENDING', 'AUTHORIZED', 'CAPTURE_PENDING',
        'CAPTURED', 'FAILED', 'VOIDED')),
    CONSTRAINT chk_payment_token CHECK (
        payment_method_token IS NULL OR payment_method_token ~ '^[A-Za-z0-9_.:-]{1,255}$')
);

CREATE TABLE payment_attempts (
    id uuid PRIMARY KEY,
    tenant_id uuid NOT NULL,
    payment_id uuid NOT NULL,
    operation varchar(32) NOT NULL,
    idempotency_key varchar(255) NOT NULL,
    state varchar(32) NOT NULL,
    provider_request_id varchar(255),
    failure_code varchar(120),
    created_at timestamptz NOT NULL,
    completed_at timestamptz,
    CONSTRAINT uq_payment_attempt_tenant_id UNIQUE (tenant_id, id),
    CONSTRAINT uq_payment_attempt_operation UNIQUE (tenant_id, payment_id, operation, idempotency_key),
    CONSTRAINT fk_payment_attempt_payment FOREIGN KEY (tenant_id, payment_id)
        REFERENCES payments (tenant_id, id),
    CONSTRAINT chk_payment_attempt_operation CHECK (operation IN ('AUTHORIZE', 'CAPTURE', 'VOID')),
    CONSTRAINT chk_payment_attempt_state CHECK (state IN ('PENDING', 'PROCESSING', 'SUCCEEDED', 'FAILED'))
);

CREATE TABLE refunds (
    id uuid PRIMARY KEY,
    tenant_id uuid NOT NULL,
    payment_id uuid NOT NULL,
    amount_minor bigint NOT NULL,
    currency varchar(3) NOT NULL,
    reason varchar(500) NOT NULL,
    state varchar(32) NOT NULL,
    provider_refund_id varchar(255),
    provider_version bigint NOT NULL DEFAULT 0,
    version bigint NOT NULL DEFAULT 0,
    failure_code varchar(120),
    requested_by_account_id uuid NOT NULL,
    created_at timestamptz NOT NULL,
    updated_at timestamptz NOT NULL,
    CONSTRAINT uq_refund_tenant_id UNIQUE (tenant_id, id),
    CONSTRAINT uq_refund_provider_id UNIQUE (payment_id, provider_refund_id),
    CONSTRAINT fk_refund_payment FOREIGN KEY (tenant_id, payment_id)
        REFERENCES payments (tenant_id, id),
    CONSTRAINT fk_refund_requester FOREIGN KEY (tenant_id, requested_by_account_id)
        REFERENCES tenant_memberships (tenant_id, user_account_id),
    CONSTRAINT chk_refund_amount CHECK (amount_minor > 0),
    CONSTRAINT chk_refund_reason CHECK (length(trim(reason)) BETWEEN 1 AND 500),
    CONSTRAINT chk_refund_state CHECK (state IN ('REQUESTED', 'PENDING', 'SUCCEEDED', 'FAILED'))
);

CREATE TABLE provider_events (
    id uuid PRIMARY KEY,
    tenant_id uuid NOT NULL,
    payment_account_id uuid NOT NULL,
    provider_event_id varchar(255) NOT NULL,
    event_type varchar(64) NOT NULL,
    provider_object_id varchar(255) NOT NULL,
    provider_version bigint NOT NULL,
    amount_minor bigint,
    currency varchar(3),
    status varchar(32) NOT NULL,
    received_at timestamptz NOT NULL,
    processed_at timestamptz,
    CONSTRAINT uq_provider_event_tenant_id UNIQUE (tenant_id, id),
    CONSTRAINT uq_provider_event UNIQUE (payment_account_id, provider_event_id),
    CONSTRAINT fk_provider_event_account FOREIGN KEY (tenant_id, payment_account_id)
        REFERENCES payment_accounts (tenant_id, id),
    CONSTRAINT chk_provider_event_version CHECK (provider_version >= 0),
    CONSTRAINT chk_provider_event_amount CHECK (amount_minor IS NULL OR amount_minor > 0),
    CONSTRAINT chk_provider_event_status CHECK (status IN ('RECEIVED', 'APPLIED', 'IGNORED'))
);

CREATE TABLE ledger_transactions (
    id uuid PRIMARY KEY,
    tenant_id uuid NOT NULL REFERENCES tenants(id),
    source_type varchar(64) NOT NULL,
    source_id uuid NOT NULL,
    description varchar(255) NOT NULL,
    created_at timestamptz NOT NULL,
    CONSTRAINT uq_ledger_transaction_tenant_id UNIQUE (tenant_id, id),
    CONSTRAINT uq_ledger_source UNIQUE (tenant_id, source_type, source_id)
);

CREATE TABLE ledger_entries (
    id uuid PRIMARY KEY,
    tenant_id uuid NOT NULL,
    transaction_id uuid NOT NULL,
    account_type varchar(64) NOT NULL,
    account_id uuid,
    debit_minor bigint NOT NULL DEFAULT 0,
    credit_minor bigint NOT NULL DEFAULT 0,
    currency varchar(3) NOT NULL,
    created_at timestamptz NOT NULL,
    CONSTRAINT uq_ledger_entry_tenant_id UNIQUE (tenant_id, id),
    CONSTRAINT fk_ledger_entry_transaction FOREIGN KEY (tenant_id, transaction_id)
        REFERENCES ledger_transactions (tenant_id, id),
    CONSTRAINT chk_ledger_side CHECK (
        (debit_minor > 0 AND credit_minor = 0) OR (credit_minor > 0 AND debit_minor = 0)),
    CONSTRAINT chk_ledger_account_type CHECK (account_type IN (
        'PROVIDER_RECEIVABLE', 'DRIVER_PAYABLE', 'PLATFORM_REVENUE', 'PAYOUT_CLEARING'))
);

CREATE TABLE settlement_batches (
    id uuid PRIMARY KEY,
    tenant_id uuid NOT NULL REFERENCES tenants(id),
    currency varchar(3) NOT NULL,
    state varchar(32) NOT NULL,
    total_minor bigint NOT NULL,
    created_by_account_id uuid NOT NULL,
    created_at timestamptz NOT NULL,
    completed_at timestamptz,
    CONSTRAINT uq_settlement_batch_tenant_id UNIQUE (tenant_id, id),
    CONSTRAINT fk_settlement_creator FOREIGN KEY (tenant_id, created_by_account_id)
        REFERENCES tenant_memberships (tenant_id, user_account_id),
    CONSTRAINT chk_settlement_total CHECK (total_minor >= 0),
    CONSTRAINT chk_settlement_state CHECK (state IN ('CREATED', 'PROCESSING', 'COMPLETED', 'FAILED'))
);

CREATE TABLE payouts (
    id uuid PRIMARY KEY,
    tenant_id uuid NOT NULL,
    settlement_batch_id uuid NOT NULL,
    driver_id uuid NOT NULL,
    amount_minor bigint NOT NULL,
    currency varchar(3) NOT NULL,
    state varchar(32) NOT NULL,
    provider_payout_id varchar(255),
    version bigint NOT NULL DEFAULT 0,
    created_at timestamptz NOT NULL,
    updated_at timestamptz NOT NULL,
    CONSTRAINT uq_payout_tenant_id UNIQUE (tenant_id, id),
    CONSTRAINT uq_payout_batch_driver UNIQUE (tenant_id, settlement_batch_id, driver_id),
    CONSTRAINT fk_payout_batch FOREIGN KEY (tenant_id, settlement_batch_id)
        REFERENCES settlement_batches (tenant_id, id),
    CONSTRAINT fk_payout_driver FOREIGN KEY (tenant_id, driver_id)
        REFERENCES driver_profiles (tenant_id, id),
    CONSTRAINT chk_payout_amount CHECK (amount_minor > 0),
    CONSTRAINT chk_payout_state CHECK (state IN ('PENDING', 'PAID', 'FAILED'))
);

CREATE INDEX idx_payments_rider ON payments (tenant_id, rider_account_id, created_at DESC);
CREATE INDEX idx_payment_attempt_pending ON payment_attempts (tenant_id, state, created_at);
CREATE INDEX idx_refunds_payment ON refunds (tenant_id, payment_id, created_at);
CREATE INDEX idx_provider_events_object ON provider_events (payment_account_id, provider_object_id, provider_version);
CREATE INDEX idx_ledger_account ON ledger_entries (tenant_id, account_type, account_id, currency);
CREATE INDEX idx_payout_driver ON payouts (tenant_id, driver_id, created_at DESC);

CREATE FUNCTION reject_payment_identity_mutation() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    IF NEW.tenant_id IS DISTINCT FROM OLD.tenant_id
       OR NEW.ride_id IS DISTINCT FROM OLD.ride_id
       OR NEW.rider_account_id IS DISTINCT FROM OLD.rider_account_id
       OR NEW.amount_minor IS DISTINCT FROM OLD.amount_minor
       OR NEW.currency IS DISTINCT FROM OLD.currency THEN
        RAISE EXCEPTION 'payment tenant, ride, rider, amount, and currency are immutable';
    END IF;
    RETURN NEW;
END;
$$;
CREATE TRIGGER payment_identity_immutable BEFORE UPDATE ON payments
FOR EACH ROW EXECUTE FUNCTION reject_payment_identity_mutation();

CREATE FUNCTION enforce_successful_refund_limit() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE captured bigint;
DECLARE refunded bigint;
BEGIN
    IF NEW.state = 'SUCCEEDED' THEN
        SELECT captured_minor INTO captured FROM payments
        WHERE tenant_id = NEW.tenant_id AND id = NEW.payment_id FOR UPDATE;
        SELECT COALESCE(sum(amount_minor), 0) INTO refunded FROM refunds
        WHERE tenant_id = NEW.tenant_id AND payment_id = NEW.payment_id
          AND state IN ('REQUESTED', 'PENDING', 'SUCCEEDED') AND id <> NEW.id;
        IF refunded + NEW.amount_minor > captured THEN
            RAISE EXCEPTION 'successful refunds exceed captured amount';
        END IF;
    END IF;
    RETURN NEW;
END;
$$;
CREATE TRIGGER refund_limit BEFORE INSERT OR UPDATE ON refunds
FOR EACH ROW EXECUTE FUNCTION enforce_successful_refund_limit();

CREATE FUNCTION enforce_refund_reservation_limit() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE captured bigint;
DECLARE reserved bigint;
BEGIN
    SELECT captured_minor INTO captured FROM payments
    WHERE tenant_id = NEW.tenant_id AND id = NEW.payment_id FOR UPDATE;
    SELECT COALESCE(sum(amount_minor), 0) INTO reserved FROM refunds
    WHERE tenant_id = NEW.tenant_id AND payment_id = NEW.payment_id
      AND state IN ('REQUESTED', 'PENDING', 'SUCCEEDED');
    IF reserved + NEW.amount_minor > captured THEN
        RAISE EXCEPTION 'refund reservations exceed captured amount';
    END IF;
    RETURN NEW;
END;
$$;
CREATE TRIGGER refund_reservation_limit BEFORE INSERT ON refunds
FOR EACH ROW EXECUTE FUNCTION enforce_refund_reservation_limit();

CREATE FUNCTION reject_financial_record_mutation() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    RAISE EXCEPTION '% is append-only', TG_TABLE_NAME;
END;
$$;
CREATE TRIGGER ledger_transactions_immutable BEFORE UPDATE OR DELETE ON ledger_transactions
FOR EACH ROW EXECUTE FUNCTION reject_financial_record_mutation();
CREATE TRIGGER ledger_entries_immutable BEFORE UPDATE OR DELETE ON ledger_entries
FOR EACH ROW EXECUTE FUNCTION reject_financial_record_mutation();
CREATE TRIGGER provider_events_immutable_delete BEFORE DELETE ON provider_events
FOR EACH ROW EXECUTE FUNCTION reject_financial_record_mutation();

CREATE FUNCTION enforce_balanced_ledger_transaction() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE debit_total bigint;
DECLARE credit_total bigint;
DECLARE currency_count integer;
BEGIN
    SELECT COALESCE(sum(debit_minor), 0), COALESCE(sum(credit_minor), 0), count(DISTINCT currency)
      INTO debit_total, credit_total, currency_count
    FROM ledger_entries WHERE tenant_id = NEW.tenant_id AND transaction_id = NEW.transaction_id;
    IF debit_total = 0 OR debit_total <> credit_total OR currency_count <> 1 THEN
        RAISE EXCEPTION 'ledger transaction must be non-zero, single-currency, and balanced';
    END IF;
    RETURN NULL;
END;
$$;
CREATE CONSTRAINT TRIGGER ledger_transaction_balanced
AFTER INSERT ON ledger_entries DEFERRABLE INITIALLY DEFERRED
FOR EACH ROW EXECUTE FUNCTION enforce_balanced_ledger_transaction();
