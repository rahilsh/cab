ALTER TABLE driver_documents
    ADD COLUMN verified_by_account_id uuid,
    ADD COLUMN rejection_reason varchar(500),
    ADD CONSTRAINT fk_driver_document_verifier
        FOREIGN KEY (tenant_id, verified_by_account_id)
        REFERENCES tenant_memberships (tenant_id, user_account_id),
    ADD CONSTRAINT chk_driver_document_type CHECK (
        document_type IN ('DRIVING_LICENSE', 'IDENTITY_DOCUMENT', 'VEHICLE_INSURANCE', 'OTHER')
    ) NOT VALID,
    ADD CONSTRAINT chk_driver_document_review CHECK (
        (verification_status = 'PENDING'
            AND verified_by_account_id IS NULL
            AND verified_at IS NULL
            AND rejection_reason IS NULL)
        OR (verification_status = 'VERIFIED'
            AND verified_by_account_id IS NOT NULL
            AND verified_at IS NOT NULL
            AND rejection_reason IS NULL)
        OR (verification_status = 'REJECTED'
            AND verified_by_account_id IS NOT NULL
            AND verified_at IS NULL
            AND rejection_reason IS NOT NULL)
    ) NOT VALID;

CREATE TABLE driver_document_verification_history (
    id uuid PRIMARY KEY,
    tenant_id uuid NOT NULL,
    document_id uuid NOT NULL,
    from_status varchar(32),
    to_status varchar(32) NOT NULL,
    actor_account_id uuid NOT NULL,
    reason varchar(500),
    occurred_at timestamptz NOT NULL,
    CONSTRAINT uq_driver_document_history_tenant_id UNIQUE (tenant_id, id),
    CONSTRAINT fk_driver_document_history_document
        FOREIGN KEY (tenant_id, document_id)
        REFERENCES driver_documents (tenant_id, id) ON DELETE CASCADE,
    CONSTRAINT fk_driver_document_history_actor
        FOREIGN KEY (tenant_id, actor_account_id)
        REFERENCES tenant_memberships (tenant_id, user_account_id),
    CONSTRAINT chk_driver_document_history_status CHECK (
        (from_status IS NULL OR from_status IN ('PENDING', 'VERIFIED', 'REJECTED'))
        AND to_status IN ('PENDING', 'VERIFIED', 'REJECTED')),
    CONSTRAINT chk_driver_document_history_reason CHECK (
        (to_status = 'REJECTED' AND reason IS NOT NULL)
        OR (to_status <> 'REJECTED' AND reason IS NULL))
);

CREATE INDEX idx_driver_document_history_document
    ON driver_document_verification_history (tenant_id, document_id, occurred_at, id);

CREATE FUNCTION reject_driver_document_history_mutation() RETURNS trigger
LANGUAGE plpgsql AS $$
BEGIN
    RAISE EXCEPTION 'driver_document_verification_history is append-only';
END;
$$;

CREATE TRIGGER driver_document_history_immutable
BEFORE UPDATE OR DELETE ON driver_document_verification_history
FOR EACH ROW EXECUTE FUNCTION reject_driver_document_history_mutation();

ALTER TABLE driver_document_verification_history ENABLE ROW LEVEL SECURITY;
ALTER TABLE driver_document_verification_history FORCE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation ON driver_document_verification_history
    USING (tenant_id = nullif(current_setting('app.tenant_id', true), '')::uuid)
    WITH CHECK (tenant_id = nullif(current_setting('app.tenant_id', true), '')::uuid);
