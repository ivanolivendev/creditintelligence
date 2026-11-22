CREATE TABLE policy_version (
    id UUID PRIMARY KEY,
    version VARCHAR(40) NOT NULL UNIQUE,
    effective_from TIMESTAMPTZ NOT NULL,
    config_json TEXT NOT NULL,
    text_content TEXT NOT NULL,
    checksum CHAR(64) NOT NULL
);

INSERT INTO policy_version (id, version, effective_from, config_json, text_content, checksum) VALUES (
    '11111111-1111-4111-8111-111111111111',
    'POLICY-1.0',
    '2026-01-01T00:00:00Z',
    '{"incomeTolerancePercent":"5.00","requiredDocumentTypes":["INCOME_PROOF"]}',
    'POLICY-1.0: Monthly income variance above 5.00 percent requires human review.',
    '6dfc0feeb6256b6859a3deade4dd02f8fb856b1f8a4153a16604a8f63df6a398'
);

CREATE TABLE analysis (
    id UUID PRIMARY KEY,
    proposal_id UUID NOT NULL REFERENCES proposal(id),
    status VARCHAR(40) NOT NULL,
    attempt INTEGER NOT NULL,
    lease_token UUID NOT NULL,
    input_fingerprint CHAR(64) NOT NULL,
    document_snapshot_hash CHAR(64) NOT NULL,
    policy_version_id UUID NOT NULL REFERENCES policy_version(id),
    started_at TIMESTAMPTZ NOT NULL,
    completed_at TIMESTAMPTZ,
    error_code VARCHAR(80),
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT analysis_input_unique UNIQUE (proposal_id, input_fingerprint)
);

CREATE INDEX analysis_proposal_started_idx ON analysis(proposal_id, started_at DESC);

CREATE TABLE document_snapshot_item (
    id UUID PRIMARY KEY,
    analysis_id UUID NOT NULL REFERENCES analysis(id),
    document_id UUID NOT NULL REFERENCES document_metadata(id),
    document_version INTEGER NOT NULL,
    sha256 CHAR(64) NOT NULL,
    object_key VARCHAR(255) NOT NULL,
    CONSTRAINT analysis_document_unique UNIQUE (analysis_id, document_id)
);

CREATE TABLE extracted_field (
    id UUID PRIMARY KEY,
    analysis_id UUID NOT NULL REFERENCES analysis(id),
    document_id UUID NOT NULL REFERENCES document_metadata(id),
    document_version INTEGER NOT NULL,
    field_name VARCHAR(80) NOT NULL,
    value_text VARCHAR(160),
    currency CHAR(3),
    extraction_status VARCHAR(30) NOT NULL,
    evidence_ref VARCHAR(160),
    page_number INTEGER,
    snippet VARCHAR(240),
    CONSTRAINT field_unique UNIQUE (analysis_id, document_id, field_name)
);

CREATE TABLE validation_finding (
    id UUID PRIMARY KEY,
    analysis_id UUID NOT NULL REFERENCES analysis(id),
    rule_code VARCHAR(80) NOT NULL,
    rule_version VARCHAR(40) NOT NULL,
    result VARCHAR(30) NOT NULL,
    details VARCHAR(500) NOT NULL,
    evidence_ref VARCHAR(160),
    CONSTRAINT finding_unique UNIQUE (analysis_id, rule_code)
);
