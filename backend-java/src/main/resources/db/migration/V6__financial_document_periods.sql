ALTER TABLE document_metadata ADD COLUMN period_key VARCHAR(7);
ALTER TABLE document_metadata DROP CONSTRAINT document_version_unique;
CREATE UNIQUE INDEX document_version_by_period_unique
    ON document_metadata (proposal_id, type, COALESCE(period_key, ''), document_version);
ALTER TABLE document_metadata ADD CONSTRAINT financial_period_required CHECK
    (type NOT IN ('PAYSLIP', 'BANK_STATEMENT') OR period_key IS NOT NULL);
