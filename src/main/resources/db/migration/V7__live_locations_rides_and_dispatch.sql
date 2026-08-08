CREATE TABLE driver_location_checkpoints (
    id uuid PRIMARY KEY,
    tenant_id uuid NOT NULL,
    shift_id uuid NOT NULL,
    point geometry(Point, 4326) NOT NULL,
    recorded_at timestamptz NOT NULL,
    sequence bigint NOT NULL,
    created_at timestamptz NOT NULL,
    CONSTRAINT uq_driver_location_tenant_id UNIQUE (tenant_id, id),
    CONSTRAINT uq_driver_location_sequence UNIQUE (tenant_id, shift_id, sequence),
    CONSTRAINT fk_driver_location_shift FOREIGN KEY (tenant_id, shift_id)
        REFERENCES driver_shifts (tenant_id, id),
    CONSTRAINT chk_driver_location_sequence CHECK (sequence >= 0)
);

CREATE TABLE rides (
    id uuid PRIMARY KEY,
    tenant_id uuid NOT NULL,
    rider_account_id uuid NOT NULL,
    quote_id uuid NOT NULL,
    product_id uuid NOT NULL,
    pickup geometry(Point, 4326) NOT NULL,
    dropoff geometry(Point, 4326) NOT NULL,
    fare_minor bigint NOT NULL,
    currency varchar(3) NOT NULL,
    driver_id uuid,
    vehicle_id uuid,
    driver_shift_id uuid,
    status varchar(32) NOT NULL,
    cancellation_actor varchar(32),
    cancellation_reason varchar(500),
    requested_at timestamptz NOT NULL,
    assigned_at timestamptz,
    arrived_at timestamptz,
    started_at timestamptz,
    completed_at timestamptz,
    cancelled_at timestamptz,
    updated_at timestamptz NOT NULL,
    version bigint NOT NULL DEFAULT 0,
    CONSTRAINT uq_ride_tenant_id UNIQUE (tenant_id, id),
    CONSTRAINT uq_ride_quote UNIQUE (tenant_id, quote_id),
    CONSTRAINT fk_ride_rider FOREIGN KEY (tenant_id, rider_account_id)
        REFERENCES tenant_memberships (tenant_id, user_account_id),
    CONSTRAINT fk_ride_quote FOREIGN KEY (tenant_id, quote_id)
        REFERENCES fare_quotes (tenant_id, id),
    CONSTRAINT fk_ride_product FOREIGN KEY (tenant_id, product_id)
        REFERENCES service_products (tenant_id, id),
    CONSTRAINT fk_ride_driver FOREIGN KEY (tenant_id, driver_id)
        REFERENCES driver_profiles (tenant_id, id),
    CONSTRAINT fk_ride_vehicle FOREIGN KEY (tenant_id, vehicle_id)
        REFERENCES vehicles (tenant_id, id),
    CONSTRAINT fk_ride_shift FOREIGN KEY (tenant_id, driver_shift_id)
        REFERENCES driver_shifts (tenant_id, id),
    CONSTRAINT chk_ride_fare CHECK (fare_minor >= 0),
    CONSTRAINT chk_ride_status CHECK (status IN (
        'REQUESTED', 'MATCHING', 'DRIVER_ASSIGNED', 'DRIVER_ARRIVING', 'DRIVER_ARRIVED',
        'IN_PROGRESS', 'COMPLETED', 'CANCELLED', 'NO_DRIVER')),
    CONSTRAINT chk_ride_assignment CHECK (
        (driver_id IS NULL AND vehicle_id IS NULL AND driver_shift_id IS NULL)
        OR (driver_id IS NOT NULL AND vehicle_id IS NOT NULL AND driver_shift_id IS NOT NULL)),
    CONSTRAINT chk_ride_cancellation CHECK (
        (status = 'CANCELLED' AND cancellation_actor IS NOT NULL AND cancellation_reason IS NOT NULL
            AND cancelled_at IS NOT NULL)
        OR (status <> 'CANCELLED' AND cancellation_actor IS NULL AND cancellation_reason IS NULL
            AND cancelled_at IS NULL)),
    CONSTRAINT chk_ride_cancellation_actor CHECK (
        cancellation_actor IS NULL OR cancellation_actor IN ('RIDER', 'DRIVER', 'ADMIN'))
);

CREATE TABLE ride_status_history (
    id uuid PRIMARY KEY,
    tenant_id uuid NOT NULL,
    ride_id uuid NOT NULL,
    from_status varchar(32),
    to_status varchar(32) NOT NULL,
    actor_account_id uuid NOT NULL,
    reason varchar(500),
    occurred_at timestamptz NOT NULL,
    CONSTRAINT uq_ride_history_tenant_id UNIQUE (tenant_id, id),
    CONSTRAINT fk_ride_history_ride FOREIGN KEY (tenant_id, ride_id)
        REFERENCES rides (tenant_id, id),
    CONSTRAINT fk_ride_history_actor FOREIGN KEY (tenant_id, actor_account_id)
        REFERENCES tenant_memberships (tenant_id, user_account_id)
);

CREATE TABLE dispatch_attempts (
    id uuid PRIMARY KEY,
    tenant_id uuid NOT NULL,
    ride_id uuid NOT NULL,
    status varchar(32) NOT NULL,
    search_radius_meters integer NOT NULL,
    candidate_limit integer NOT NULL,
    candidate_count integer NOT NULL,
    started_at timestamptz NOT NULL,
    completed_at timestamptz,
    updated_at timestamptz NOT NULL,
    version bigint NOT NULL DEFAULT 0,
    CONSTRAINT uq_dispatch_attempt_tenant_id UNIQUE (tenant_id, id),
    CONSTRAINT fk_dispatch_attempt_ride FOREIGN KEY (tenant_id, ride_id)
        REFERENCES rides (tenant_id, id),
    CONSTRAINT chk_dispatch_attempt_status CHECK (
        status IN ('SEARCHING', 'OFFERED', 'ASSIGNED', 'EXHAUSTED', 'CANCELLED')),
    CONSTRAINT chk_dispatch_attempt_search CHECK (
        search_radius_meters > 0 AND candidate_limit > 0 AND candidate_count >= 0)
);

CREATE TABLE driver_offers (
    id uuid PRIMARY KEY,
    tenant_id uuid NOT NULL,
    attempt_id uuid NOT NULL,
    ride_id uuid NOT NULL,
    shift_id uuid NOT NULL,
    driver_id uuid NOT NULL,
    vehicle_id uuid NOT NULL,
    status varchar(32) NOT NULL,
    expires_at timestamptz NOT NULL,
    responded_at timestamptz,
    created_at timestamptz NOT NULL,
    updated_at timestamptz NOT NULL,
    version bigint NOT NULL DEFAULT 0,
    CONSTRAINT uq_driver_offer_tenant_id UNIQUE (tenant_id, id),
    CONSTRAINT uq_driver_offer_ride_driver UNIQUE (tenant_id, ride_id, driver_id),
    CONSTRAINT fk_driver_offer_attempt FOREIGN KEY (tenant_id, attempt_id)
        REFERENCES dispatch_attempts (tenant_id, id),
    CONSTRAINT fk_driver_offer_ride FOREIGN KEY (tenant_id, ride_id)
        REFERENCES rides (tenant_id, id),
    CONSTRAINT fk_driver_offer_shift FOREIGN KEY (tenant_id, shift_id)
        REFERENCES driver_shifts (tenant_id, id),
    CONSTRAINT fk_driver_offer_driver FOREIGN KEY (tenant_id, driver_id)
        REFERENCES driver_profiles (tenant_id, id),
    CONSTRAINT fk_driver_offer_vehicle FOREIGN KEY (tenant_id, vehicle_id)
        REFERENCES vehicles (tenant_id, id),
    CONSTRAINT chk_driver_offer_status CHECK (
        status IN ('PENDING', 'ACCEPTED', 'REJECTED', 'EXPIRED')),
    CONSTRAINT chk_driver_offer_expiry CHECK (expires_at > created_at)
);

CREATE INDEX idx_driver_location_tenant_shift_time
    ON driver_location_checkpoints (tenant_id, shift_id, recorded_at DESC);
CREATE INDEX idx_driver_location_point ON driver_location_checkpoints USING gist (point);
CREATE INDEX idx_rides_tenant_rider_created
    ON rides (tenant_id, rider_account_id, requested_at DESC);
CREATE INDEX idx_rides_tenant_status ON rides (tenant_id, status, requested_at);
CREATE INDEX idx_ride_history_tenant_ride ON ride_status_history (tenant_id, ride_id, occurred_at, id);
CREATE INDEX idx_dispatch_attempt_ride ON dispatch_attempts (tenant_id, ride_id, started_at DESC);
CREATE INDEX idx_driver_offer_driver_status
    ON driver_offers (tenant_id, driver_id, status, expires_at);

CREATE UNIQUE INDEX uq_active_dispatch_attempt
    ON dispatch_attempts (tenant_id, ride_id)
    WHERE status IN ('SEARCHING', 'OFFERED');
CREATE UNIQUE INDEX uq_accepted_offer_per_ride
    ON driver_offers (tenant_id, ride_id)
    WHERE status = 'ACCEPTED';
CREATE UNIQUE INDEX uq_active_ride_per_shift
    ON rides (tenant_id, driver_shift_id)
    WHERE driver_shift_id IS NOT NULL
      AND status IN ('DRIVER_ASSIGNED', 'DRIVER_ARRIVING', 'DRIVER_ARRIVED', 'IN_PROGRESS');
CREATE UNIQUE INDEX uq_active_ride_per_rider
    ON rides (tenant_id, rider_account_id)
    WHERE status IN ('REQUESTED', 'MATCHING', 'DRIVER_ASSIGNED', 'DRIVER_ARRIVING',
                     'DRIVER_ARRIVED', 'IN_PROGRESS');

CREATE FUNCTION reject_ride_status_history_mutation() RETURNS trigger
LANGUAGE plpgsql AS $$
BEGIN
    RAISE EXCEPTION 'ride_status_history is append-only';
END;
$$;

CREATE TRIGGER ride_status_history_immutable
BEFORE UPDATE OR DELETE ON ride_status_history
FOR EACH ROW EXECUTE FUNCTION reject_ride_status_history_mutation();

CREATE FUNCTION reject_ride_snapshot_mutation() RETURNS trigger
LANGUAGE plpgsql AS $$
BEGIN
    IF NEW.rider_account_id IS DISTINCT FROM OLD.rider_account_id
       OR NEW.quote_id IS DISTINCT FROM OLD.quote_id
       OR NEW.product_id IS DISTINCT FROM OLD.product_id
       OR NEW.pickup IS DISTINCT FROM OLD.pickup
       OR NEW.dropoff IS DISTINCT FROM OLD.dropoff
       OR NEW.fare_minor IS DISTINCT FROM OLD.fare_minor
       OR NEW.currency IS DISTINCT FROM OLD.currency
       OR NEW.requested_at IS DISTINCT FROM OLD.requested_at THEN
        RAISE EXCEPTION 'ride quote snapshot is immutable';
    END IF;
    RETURN NEW;
END;
$$;

CREATE TRIGGER ride_snapshot_immutable
BEFORE UPDATE ON rides
FOR EACH ROW EXECUTE FUNCTION reject_ride_snapshot_mutation();
