CREATE TABLE document_metadata (
    id UUID PRIMARY KEY,
    proposal_id UUID NOT NULL REFERENCES proposal(id),
    type VARCHAR(40) NOT NULL,
    object_key VARCHAR(255) NOT NULL UNIQUE,
    sha256 CHAR(64) NOT NULL,
    document_version INTEGER NOT NULL,
    uploaded_at TIMESTAMPTZ NOT NULL,
    status VARCHAR(40) NOT NULL,
    CONSTRAINT document_version_positive CHECK (document_version > 0),
    CONSTRAINT document_version_unique UNIQUE (proposal_id, type, document_version)
);

CREATE INDEX document_proposal_idx ON document_metadata(proposal_id);
