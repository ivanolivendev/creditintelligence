CREATE TABLE analysis_request_key (
    proposal_id UUID NOT NULL REFERENCES proposal(id),
    request_key VARCHAR(120) NOT NULL,
    input_fingerprint CHAR(64) NOT NULL,
    analysis_id UUID NOT NULL REFERENCES analysis(id),
    created_at TIMESTAMPTZ NOT NULL,
    PRIMARY KEY (proposal_id, request_key)
);

CREATE INDEX analysis_request_key_analysis_idx ON analysis_request_key(analysis_id);
