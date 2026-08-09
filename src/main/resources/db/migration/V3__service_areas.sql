CREATE TABLE service_areas (
    id uuid PRIMARY KEY,
    tenant_id uuid NOT NULL REFERENCES tenants(id),
    slug varchar(63) NOT NULL,
    name varchar(120) NOT NULL,
    status varchar(32) NOT NULL,
    timezone varchar(64) NOT NULL,
    boundary geometry(Geometry, 4326) NOT NULL,
    created_at timestamptz NOT NULL,
    updated_at timestamptz NOT NULL,
    CONSTRAINT uq_service_area_tenant_slug UNIQUE (tenant_id, slug),
    CONSTRAINT uq_service_area_tenant_name UNIQUE (tenant_id, name),
    CONSTRAINT chk_service_area_slug CHECK (slug ~ '^[a-z0-9]+(?:-[a-z0-9]+)*$'),
    CONSTRAINT chk_service_area_status CHECK (status IN ('ACTIVE', 'INACTIVE')),
    CONSTRAINT chk_service_area_boundary_type
        CHECK (ST_GeometryType(boundary) IN ('ST_Polygon', 'ST_MultiPolygon')),
    CONSTRAINT chk_service_area_boundary_valid CHECK (ST_IsValid(boundary))
);

CREATE INDEX idx_service_areas_tenant_status ON service_areas (tenant_id, status);
CREATE INDEX idx_service_areas_boundary ON service_areas USING gist (boundary);
