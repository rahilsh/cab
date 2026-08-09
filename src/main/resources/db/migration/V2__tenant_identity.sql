CREATE TABLE tenants (
    id uuid PRIMARY KEY,
    slug varchar(63) NOT NULL UNIQUE,
    display_name varchar(120) NOT NULL,
    status varchar(32) NOT NULL,
    default_currency varchar(3) NOT NULL,
    timezone varchar(64) NOT NULL,
    created_at timestamptz NOT NULL,
    updated_at timestamptz NOT NULL,
    CONSTRAINT chk_tenant_slug CHECK (slug ~ '^[a-z0-9]+(?:-[a-z0-9]+)*$')
);

CREATE TABLE user_accounts (
    id uuid PRIMARY KEY,
    issuer varchar(255) NOT NULL,
    subject varchar(255) NOT NULL,
    email varchar(320),
    display_name varchar(120),
    status varchar(32) NOT NULL,
    created_at timestamptz NOT NULL,
    updated_at timestamptz NOT NULL,
    CONSTRAINT uq_user_account_identity UNIQUE (issuer, subject)
);

CREATE TABLE tenant_memberships (
    id uuid PRIMARY KEY,
    tenant_id uuid NOT NULL REFERENCES tenants(id),
    user_account_id uuid NOT NULL REFERENCES user_accounts(id),
    status varchar(32) NOT NULL,
    created_at timestamptz NOT NULL,
    updated_at timestamptz NOT NULL,
    CONSTRAINT uq_tenant_membership UNIQUE (tenant_id, user_account_id)
);

CREATE TABLE tenant_membership_roles (
    membership_id uuid NOT NULL REFERENCES tenant_memberships(id) ON DELETE CASCADE,
    role varchar(32) NOT NULL,
    PRIMARY KEY (membership_id, role)
);

CREATE INDEX idx_memberships_account ON tenant_memberships (user_account_id, status);
