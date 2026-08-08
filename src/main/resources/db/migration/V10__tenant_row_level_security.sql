-- Tenant selection and membership authorization are control-plane operations. The tenants,
-- user_accounts, tenant_memberships, and tenant_membership_roles tables intentionally remain
-- outside RLS so they can be queried before app.tenant_id has been established.

-- Provider callbacks arrive without an authenticated tenant. This global routing index reveals
-- only the tenant UUID for an unguessable payment account UUID; all account data remains under RLS.
-- Backfill before FORCE RLS also supports upgrades containing existing payment accounts.
CREATE TABLE payment_account_routes (
    payment_account_id uuid PRIMARY KEY REFERENCES payment_accounts(id) ON DELETE CASCADE,
    routed_tenant uuid NOT NULL REFERENCES tenants(id)
);

INSERT INTO payment_account_routes (payment_account_id, routed_tenant)
SELECT id, tenant_id FROM payment_accounts;

CREATE FUNCTION register_payment_account_route() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    INSERT INTO payment_account_routes (payment_account_id, routed_tenant)
    VALUES (NEW.id, NEW.tenant_id);
    RETURN NEW;
END;
$$;

CREATE TRIGGER payment_account_route_insert
AFTER INSERT ON payment_accounts
FOR EACH ROW EXECUTE FUNCTION register_payment_account_route();

DO $$
DECLARE
    tenant_table text;
BEGIN
    FOREACH tenant_table IN ARRAY ARRAY[
        'service_areas',
        'rider_profiles',
        'driver_profiles',
        'driver_documents',
        'vehicles',
        'driver_shifts',
        'service_products',
        'pricing_rules',
        'fare_quotes',
        'idempotency_records',
        'outbox_events',
        'inbox_receipts',
        'audit_events',
        'driver_location_checkpoints',
        'rides',
        'ride_status_history',
        'dispatch_attempts',
        'driver_offers',
        'payment_accounts',
        'payments',
        'payment_attempts',
        'refunds',
        'provider_events',
        'ledger_transactions',
        'ledger_entries',
        'settlement_batches',
        'payouts',
        'notification_preferences',
        'notification_deliveries',
        'notification_attempts',
        'ratings',
        'support_cases',
        'support_messages',
        'support_assignments',
        'support_case_state_history',
        'safety_incidents',
        'safety_evidence',
        'safety_incident_actions',
        'webhook_subscriptions',
        'webhook_deliveries',
        'webhook_attempts'
    ] LOOP
        EXECUTE format('ALTER TABLE %I ENABLE ROW LEVEL SECURITY', tenant_table);
        EXECUTE format('ALTER TABLE %I FORCE ROW LEVEL SECURITY', tenant_table);
        EXECUTE format(
            'CREATE POLICY tenant_isolation ON %I USING '
            || '(tenant_id = nullif(current_setting(''app.tenant_id'', true), '''')::uuid) '
            || 'WITH CHECK '
            || '(tenant_id = nullif(current_setting(''app.tenant_id'', true), '''')::uuid)',
            tenant_table);
    END LOOP;
END
$$;
