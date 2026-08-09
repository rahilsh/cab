ALTER TABLE payouts DROP CONSTRAINT chk_payout_state;
ALTER TABLE payouts ADD CONSTRAINT chk_payout_state CHECK (state IN (
    'PENDING', 'PAID', 'FAILED', 'RELEASED', 'RECONCILIATION_REQUIRED'));

-- V14-V17 released failed payouts immediately; preserve that ledger-backed terminal state.
UPDATE payouts payout
SET state = 'RELEASED'
WHERE payout.state = 'FAILED'
  AND EXISTS (
      SELECT 1 FROM ledger_transactions ledger_transaction
      WHERE ledger_transaction.tenant_id = payout.tenant_id
        AND ledger_transaction.source_type = 'PAYOUT_RELEASE'
        AND ledger_transaction.source_id = payout.id);

ALTER TABLE refunds DROP CONSTRAINT chk_refund_state;
ALTER TABLE refunds ADD CONSTRAINT chk_refund_state CHECK (state IN (
    'REQUESTED', 'PENDING', 'SUCCEEDED', 'FAILED', 'RECONCILIATION_REQUIRED'));

ALTER TABLE refunds DROP CONSTRAINT chk_refund_split;
ALTER TABLE refunds ADD CONSTRAINT chk_refund_split CHECK (
    (driver_share_minor IS NULL AND platform_share_minor IS NULL)
    OR (driver_share_minor >= 0 AND platform_share_minor >= 0
        AND driver_share_minor + platform_share_minor = amount_minor));

ALTER TABLE ledger_entries DROP CONSTRAINT chk_ledger_account_type;
ALTER TABLE ledger_entries ADD CONSTRAINT chk_ledger_account_type CHECK (account_type IN (
    'PROVIDER_RECEIVABLE', 'DRIVER_PAYABLE', 'PLATFORM_REVENUE',
    'PAYOUT_CLEARING', 'REFUND_CLEARING'));

-- Allocate and reserve refunds that were still pending when this safety model was deployed.
DO $$
DECLARE
    payment_row record;
    refund_row record;
    committed_amount bigint;
    committed_driver bigint;
    allocated_driver bigint;
    transaction_id uuid;
    ride_driver_id uuid;
BEGIN
    FOR payment_row IN
        SELECT id, tenant_id, captured_minor, driver_share_minor
        FROM payments
        WHERE state = 'CAPTURED' AND driver_share_minor IS NOT NULL
        ORDER BY tenant_id, id
    LOOP
        SELECT COALESCE(sum(amount_minor), 0), COALESCE(sum(driver_share_minor), 0)
        INTO committed_amount, committed_driver
        FROM refunds
        WHERE tenant_id = payment_row.tenant_id AND payment_id = payment_row.id
          AND state = 'SUCCEEDED';
        FOR refund_row IN
            SELECT id, amount_minor, driver_share_minor, platform_share_minor,
                   currency, state, created_at
            FROM refunds
            WHERE tenant_id = payment_row.tenant_id AND payment_id = payment_row.id
              AND state IN ('REQUESTED', 'PENDING')
            ORDER BY created_at, id
        LOOP
            committed_amount := committed_amount + refund_row.amount_minor;
            IF refund_row.driver_share_minor IS NULL THEN
                allocated_driver := trunc(
                    (payment_row.driver_share_minor::numeric * committed_amount)
                    / payment_row.captured_minor)::bigint - committed_driver;
                UPDATE refunds
                SET driver_share_minor = allocated_driver,
                    platform_share_minor = refund_row.amount_minor - allocated_driver
                WHERE tenant_id = payment_row.tenant_id AND id = refund_row.id;
            ELSE
                allocated_driver := refund_row.driver_share_minor;
            END IF;
            committed_driver := committed_driver + allocated_driver;

            IF refund_row.state IN ('REQUESTED', 'PENDING') THEN
                transaction_id := gen_random_uuid();
                INSERT INTO ledger_transactions
                    (id, tenant_id, source_type, source_id, description, created_at)
                VALUES (transaction_id, payment_row.tenant_id, 'REFUND_RESERVE', refund_row.id,
                        'Refund reserved during V18 migration', now())
                ON CONFLICT (tenant_id, source_type, source_id) DO NOTHING;
                IF FOUND THEN
                    SELECT r.driver_id INTO ride_driver_id
                    FROM payments p JOIN rides r
                      ON r.tenant_id = p.tenant_id AND r.id = p.ride_id
                    WHERE p.tenant_id = payment_row.tenant_id AND p.id = payment_row.id;
                    IF allocated_driver > 0 THEN
                        INSERT INTO ledger_entries
                            (id, tenant_id, transaction_id, account_type, account_id,
                             debit_minor, credit_minor, currency, created_at)
                        VALUES (gen_random_uuid(), payment_row.tenant_id, transaction_id,
                                'DRIVER_PAYABLE', ride_driver_id, allocated_driver, 0,
                                refund_row.currency, now());
                    END IF;
                    IF refund_row.amount_minor - allocated_driver > 0 THEN
                        INSERT INTO ledger_entries
                            (id, tenant_id, transaction_id, account_type, account_id,
                             debit_minor, credit_minor, currency, created_at)
                        VALUES (gen_random_uuid(), payment_row.tenant_id, transaction_id,
                                'PLATFORM_REVENUE', NULL,
                                refund_row.amount_minor - allocated_driver, 0,
                                refund_row.currency, now());
                    END IF;
                    INSERT INTO ledger_entries
                        (id, tenant_id, transaction_id, account_type, account_id,
                         debit_minor, credit_minor, currency, created_at)
                    VALUES (gen_random_uuid(), payment_row.tenant_id, transaction_id,
                            'REFUND_CLEARING', NULL, 0, refund_row.amount_minor,
                            refund_row.currency, now());
                END IF;
            END IF;
        END LOOP;
    END LOOP;
END;
$$;

CREATE OR REPLACE FUNCTION enforce_successful_refund_limit() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE captured bigint;
DECLARE refunded bigint;
BEGIN
    IF NEW.state = 'SUCCEEDED' THEN
        SELECT captured_minor INTO captured FROM payments
        WHERE tenant_id = NEW.tenant_id AND id = NEW.payment_id FOR UPDATE;
        SELECT COALESCE(sum(amount_minor), 0) INTO refunded FROM refunds
        WHERE tenant_id = NEW.tenant_id AND payment_id = NEW.payment_id
          AND state IN ('REQUESTED', 'PENDING', 'SUCCEEDED', 'RECONCILIATION_REQUIRED')
          AND id <> NEW.id;
        IF refunded + NEW.amount_minor > captured THEN
            RAISE EXCEPTION 'successful refunds exceed captured amount';
        END IF;
    END IF;
    RETURN NEW;
END;
$$;

CREATE OR REPLACE FUNCTION enforce_refund_reservation_limit() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE captured bigint;
DECLARE reserved bigint;
BEGIN
    SELECT captured_minor INTO captured FROM payments
    WHERE tenant_id = NEW.tenant_id AND id = NEW.payment_id FOR UPDATE;
    SELECT COALESCE(sum(amount_minor), 0) INTO reserved FROM refunds
    WHERE tenant_id = NEW.tenant_id AND payment_id = NEW.payment_id
      AND state IN ('REQUESTED', 'PENDING', 'SUCCEEDED', 'RECONCILIATION_REQUIRED');
    IF reserved + NEW.amount_minor > captured THEN
        RAISE EXCEPTION 'refund reservations exceed captured amount';
    END IF;
    RETURN NEW;
END;
$$;
