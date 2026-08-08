CREATE EXTENSION IF NOT EXISTS btree_gist;

CREATE TABLE service_products (
    id uuid PRIMARY KEY,
    tenant_id uuid NOT NULL REFERENCES tenants(id),
    slug varchar(63) NOT NULL,
    name varchar(120) NOT NULL,
    status varchar(32) NOT NULL,
    capacity integer NOT NULL,
    service_class varchar(32) NOT NULL,
    created_at timestamptz NOT NULL,
    updated_at timestamptz NOT NULL,
    CONSTRAINT uq_service_product_tenant_slug UNIQUE (tenant_id, slug),
    CONSTRAINT uq_service_product_tenant_name UNIQUE (tenant_id, name),
    CONSTRAINT uq_service_product_tenant_id UNIQUE (tenant_id, id),
    CONSTRAINT chk_service_product_slug CHECK (slug ~ '^[a-z0-9]+(?:-[a-z0-9]+)*$'),
    CONSTRAINT chk_service_product_status CHECK (status IN ('ACTIVE', 'INACTIVE')),
    CONSTRAINT chk_service_product_capacity CHECK (capacity BETWEEN 1 AND 20)
);

CREATE TABLE pricing_rules (
    id uuid PRIMARY KEY,
    tenant_id uuid NOT NULL,
    product_id uuid NOT NULL,
    version integer NOT NULL,
    effective_from timestamptz NOT NULL,
    effective_to timestamptz,
    base_fare_minor bigint NOT NULL,
    per_km_minor bigint NOT NULL,
    per_minute_minor bigint NOT NULL,
    minimum_fare_minor bigint NOT NULL,
    currency varchar(3) NOT NULL,
    surge_basis_points integer,
    tax_basis_points integer,
    active boolean NOT NULL,
    created_at timestamptz NOT NULL,
    updated_at timestamptz NOT NULL,
    CONSTRAINT uq_pricing_rule_tenant_id UNIQUE (tenant_id, id),
    CONSTRAINT uq_pricing_rule_tenant_product_id UNIQUE (tenant_id, product_id, id),
    CONSTRAINT uq_pricing_rule_product_version UNIQUE (tenant_id, product_id, version),
    CONSTRAINT fk_pricing_rule_product FOREIGN KEY (tenant_id, product_id)
        REFERENCES service_products (tenant_id, id),
    CONSTRAINT chk_pricing_rule_range CHECK (effective_to IS NULL OR effective_to > effective_from),
    CONSTRAINT chk_pricing_rule_amounts CHECK (
        base_fare_minor >= 0 AND per_km_minor >= 0 AND per_minute_minor >= 0
        AND minimum_fare_minor >= 0),
    CONSTRAINT chk_pricing_rule_surge CHECK (
        surge_basis_points IS NULL OR surge_basis_points BETWEEN 0 AND 100000),
    CONSTRAINT chk_pricing_rule_tax CHECK (
        tax_basis_points IS NULL OR tax_basis_points BETWEEN 0 AND 10000),
    CONSTRAINT ex_pricing_rule_active_range EXCLUDE USING gist (
        tenant_id WITH =,
        product_id WITH =,
        tstzrange(effective_from, effective_to, '[)') WITH &&
    ) WHERE (active)
);

CREATE TABLE fare_quotes (
    id uuid PRIMARY KEY,
    tenant_id uuid NOT NULL,
    rider_account_id uuid NOT NULL,
    product_id uuid NOT NULL,
    pricing_rule_id uuid NOT NULL,
    pricing_rule_version integer NOT NULL,
    pickup geometry(Point, 4326) NOT NULL,
    dropoff geometry(Point, 4326) NOT NULL,
    route_distance_meters bigint NOT NULL,
    route_duration_seconds bigint NOT NULL,
    base_rate_minor bigint NOT NULL,
    per_km_rate_minor bigint NOT NULL,
    per_minute_rate_minor bigint NOT NULL,
    minimum_fare_minor bigint NOT NULL,
    surge_basis_points integer NOT NULL,
    tax_basis_points integer NOT NULL,
    base_fare_minor bigint NOT NULL,
    distance_fare_minor bigint NOT NULL,
    time_fare_minor bigint NOT NULL,
    minimum_adjustment_minor bigint NOT NULL,
    subtotal_minor bigint NOT NULL,
    surge_minor bigint NOT NULL,
    tax_minor bigint NOT NULL,
    total_minor bigint NOT NULL,
    currency varchar(3) NOT NULL,
    status varchar(32) NOT NULL,
    expires_at timestamptz NOT NULL,
    request_fingerprint varchar(64) NOT NULL,
    created_at timestamptz NOT NULL,
    updated_at timestamptz NOT NULL,
    version bigint NOT NULL DEFAULT 0,
    CONSTRAINT uq_fare_quote_tenant_id UNIQUE (tenant_id, id),
    CONSTRAINT fk_fare_quote_membership FOREIGN KEY (tenant_id, rider_account_id)
        REFERENCES tenant_memberships (tenant_id, user_account_id),
    CONSTRAINT fk_fare_quote_product FOREIGN KEY (tenant_id, product_id)
        REFERENCES service_products (tenant_id, id),
    CONSTRAINT fk_fare_quote_rule FOREIGN KEY (tenant_id, product_id, pricing_rule_id)
        REFERENCES pricing_rules (tenant_id, product_id, id),
    CONSTRAINT chk_fare_quote_route CHECK (
        route_distance_meters >= 0 AND route_duration_seconds >= 0),
    CONSTRAINT chk_fare_quote_money CHECK (
        base_rate_minor >= 0 AND per_km_rate_minor >= 0 AND per_minute_rate_minor >= 0
        AND minimum_fare_minor >= 0 AND base_fare_minor >= 0 AND distance_fare_minor >= 0
        AND time_fare_minor >= 0 AND minimum_adjustment_minor >= 0 AND subtotal_minor >= 0
        AND surge_minor >= 0 AND tax_minor >= 0 AND total_minor >= 0),
    CONSTRAINT chk_fare_quote_status CHECK (status IN ('ACTIVE', 'CONSUMED', 'EXPIRED')),
    CONSTRAINT chk_fare_quote_expiry CHECK (expires_at > created_at)
);

CREATE INDEX idx_service_products_tenant_status
    ON service_products (tenant_id, status);
CREATE INDEX idx_pricing_rules_tenant_product_effective
    ON pricing_rules (tenant_id, product_id, active, effective_from, effective_to);
CREATE INDEX idx_fare_quotes_tenant_rider_created
    ON fare_quotes (tenant_id, rider_account_id, created_at DESC);
CREATE INDEX idx_fare_quotes_tenant_fingerprint
    ON fare_quotes (tenant_id, rider_account_id, request_fingerprint);
CREATE INDEX idx_fare_quotes_tenant_pickup ON fare_quotes USING gist (tenant_id, pickup);
CREATE INDEX idx_fare_quotes_tenant_dropoff ON fare_quotes USING gist (tenant_id, dropoff);
