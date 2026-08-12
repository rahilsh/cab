CREATE TABLE tenant_membership_invitations (
    id uuid PRIMARY KEY,
    tenant_id uuid NOT NULL REFERENCES tenants(id),
    email varchar(320) NOT NULL,
    token_hash varchar(64) NOT NULL UNIQUE,
    status varchar(32) NOT NULL,
    invited_by_account_id uuid NOT NULL REFERENCES user_accounts(id),
    accepted_by_account_id uuid REFERENCES user_accounts(id),
    expires_at timestamptz NOT NULL,
    accepted_at timestamptz,
    created_at timestamptz NOT NULL,
    updated_at timestamptz NOT NULL,
    CONSTRAINT chk_membership_invitation_status
        CHECK (status IN ('INVITED', 'ACCEPTED', 'REVOKED')),
    CONSTRAINT chk_membership_invitation_token_hash CHECK (length(token_hash) = 64)
);

CREATE TABLE tenant_membership_invitation_roles (
    invitation_id uuid NOT NULL REFERENCES tenant_membership_invitations(id) ON DELETE CASCADE,
    role varchar(32) NOT NULL,
    PRIMARY KEY (invitation_id, role)
);

CREATE INDEX idx_membership_invitations_tenant
    ON tenant_membership_invitations (tenant_id, status, created_at);
CREATE INDEX idx_membership_invitations_email
    ON tenant_membership_invitations (lower(email), status);
