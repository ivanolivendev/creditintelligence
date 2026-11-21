CREATE TABLE bureau_consultation (
    id UUID PRIMARY KEY,
    proposal_id UUID NOT NULL REFERENCES proposal(id),
    provider VARCHAR(80) NOT NULL,
    environment VARCHAR(20) NOT NULL,
    status VARCHAR(40) NOT NULL,
    response_version VARCHAR(40),
    report_json JSONB NOT NULL,
    report_sha256 CHAR(64) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL
);
CREATE INDEX bureau_consultation_proposal_created_idx
    ON bureau_consultation (proposal_id, created_at DESC);
ALTER TABLE financial_assessment ADD COLUMN bureau_consultation_id UUID REFERENCES bureau_consultation(id);
