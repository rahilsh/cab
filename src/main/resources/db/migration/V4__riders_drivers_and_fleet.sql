CREATE TABLE rider_profiles (
    id uuid PRIMARY KEY,
    tenant_id uuid NOT NULL,
    account_id uuid NOT NULL,
    display_name varchar(120) NOT NULL,
    phone_number varchar(32),
    created_at timestamptz NOT NULL,
    updated_at timestamptz NOT NULL,
    CONSTRAINT uq_rider_profile_tenant_account UNIQUE (tenant_id, account_id),
    CONSTRAINT uq_rider_profile_tenant_id UNIQUE (tenant_id, id),
    CONSTRAINT fk_rider_profile_membership
        FOREIGN KEY (tenant_id, account_id)
        REFERENCES tenant_memberships (tenant_id, user_account_id)
);

CREATE TABLE driver_profiles (
    id uuid PRIMARY KEY,
    tenant_id uuid NOT NULL,
    account_id uuid NOT NULL,
    legal_name varchar(120) NOT NULL,
    phone_number varchar(32),
    status varchar(32) NOT NULL,
    created_at timestamptz NOT NULL,
    updated_at timestamptz NOT NULL,
    CONSTRAINT uq_driver_profile_tenant_account UNIQUE (tenant_id, account_id),
    CONSTRAINT uq_driver_profile_tenant_id UNIQUE (tenant_id, id),
    CONSTRAINT fk_driver_profile_membership
        FOREIGN KEY (tenant_id, account_id)
        REFERENCES tenant_memberships (tenant_id, user_account_id),
    CONSTRAINT chk_driver_profile_status
        CHECK (status IN ('PENDING', 'APPROVED', 'SUSPENDED'))
);

CREATE TABLE driver_documents (
    id uuid PRIMARY KEY,
    tenant_id uuid NOT NULL,
    driver_id uuid NOT NULL,
    document_type varchar(64) NOT NULL,
    document_reference varchar(255) NOT NULL,
    object_key varchar(512),
    expires_on date,
    verification_status varchar(32) NOT NULL,
    verified_at timestamptz,
    created_at timestamptz NOT NULL,
    updated_at timestamptz NOT NULL,
    CONSTRAINT uq_driver_document_tenant_id UNIQUE (tenant_id, id),
    CONSTRAINT fk_driver_document_profile
        FOREIGN KEY (tenant_id, driver_id)
        REFERENCES driver_profiles (tenant_id, id) ON DELETE CASCADE,
    CONSTRAINT chk_driver_document_verification
        CHECK (verification_status IN ('PENDING', 'VERIFIED', 'REJECTED'))
);

CREATE TABLE vehicles (
    id uuid PRIMARY KEY,
    tenant_id uuid NOT NULL REFERENCES tenants(id),
    registration varchar(32) NOT NULL,
    service_class varchar(32) NOT NULL,
    capacity integer NOT NULL,
    status varchar(32) NOT NULL,
    version bigint NOT NULL DEFAULT 0,
    created_at timestamptz NOT NULL,
    updated_at timestamptz NOT NULL,
    CONSTRAINT uq_vehicle_tenant_registration UNIQUE (tenant_id, registration),
    CONSTRAINT uq_vehicle_tenant_id UNIQUE (tenant_id, id),
    CONSTRAINT chk_vehicle_capacity CHECK (capacity BETWEEN 1 AND 20),
    CONSTRAINT chk_vehicle_status CHECK (status IN ('ACTIVE', 'INACTIVE'))
);

CREATE TABLE driver_shifts (
    id uuid PRIMARY KEY,
    tenant_id uuid NOT NULL,
    driver_id uuid NOT NULL,
    vehicle_id uuid NOT NULL,
    status varchar(32) NOT NULL,
    version bigint NOT NULL DEFAULT 0,
    created_at timestamptz NOT NULL,
    updated_at timestamptz NOT NULL,
    available_at timestamptz,
    closed_at timestamptz,
    CONSTRAINT uq_driver_shift_tenant_id UNIQUE (tenant_id, id),
    CONSTRAINT fk_driver_shift_driver
        FOREIGN KEY (tenant_id, driver_id)
        REFERENCES driver_profiles (tenant_id, id),
    CONSTRAINT fk_driver_shift_vehicle
        FOREIGN KEY (tenant_id, vehicle_id)
        REFERENCES vehicles (tenant_id, id),
    CONSTRAINT chk_driver_shift_status
        CHECK (status IN ('OFFLINE', 'AVAILABLE', 'RESERVED', 'ON_TRIP', 'CLOSED'))
);

CREATE INDEX idx_rider_profiles_tenant ON rider_profiles (tenant_id);
CREATE INDEX idx_driver_profiles_tenant_status ON driver_profiles (tenant_id, status);
CREATE INDEX idx_driver_documents_tenant_driver ON driver_documents (tenant_id, driver_id);
CREATE INDEX idx_vehicles_tenant_status ON vehicles (tenant_id, status);
CREATE INDEX idx_driver_shifts_tenant_status ON driver_shifts (tenant_id, status);

CREATE UNIQUE INDEX uq_driver_open_shift
    ON driver_shifts (tenant_id, driver_id)
    WHERE status <> 'CLOSED';

CREATE UNIQUE INDEX uq_vehicle_open_shift
    ON driver_shifts (tenant_id, vehicle_id)
    WHERE status <> 'CLOSED';
