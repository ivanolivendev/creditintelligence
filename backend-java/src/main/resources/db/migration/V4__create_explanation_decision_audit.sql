CREATE TABLE ai_explanation (
    analysis_id UUID PRIMARY KEY REFERENCES analysis(id),
    status VARCHAR(40) NOT NULL,
    provider VARCHAR(40),
    model_id VARCHAR(120),
    prompt_version VARCHAR(40),
    content_json TEXT,
    created_at TIMESTAMPTZ NOT NULL
);

CREATE TABLE analyst_decision (
    id UUID PRIMARY KEY,
    proposal_id UUID NOT NULL REFERENCES proposal(id),
    analysis_id UUID NOT NULL UNIQUE REFERENCES analysis(id),
    analyst_id VARCHAR(120) NOT NULL,
    outcome VARCHAR(40) NOT NULL,
    reason VARCHAR(1000) NOT NULL,
    document_snapshot_hash CHAR(64) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL
);

CREATE UNIQUE INDEX decision_final_per_proposal_idx ON analyst_decision(proposal_id)
    WHERE outcome IN ('APPROVED', 'REJECTED');

CREATE TABLE audit_record (
    id UUID PRIMARY KEY,
    aggregate_type VARCHAR(80) NOT NULL,
    aggregate_id UUID NOT NULL,
    action VARCHAR(80) NOT NULL,
    actor_id VARCHAR(120) NOT NULL,
    correlation_id VARCHAR(64) NOT NULL,
    metadata_json TEXT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL
);

CREATE INDEX audit_aggregate_idx ON audit_record(aggregate_type, aggregate_id, created_at);
