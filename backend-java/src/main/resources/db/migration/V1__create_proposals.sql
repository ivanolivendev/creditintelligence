CREATE TABLE applicant (
    id UUID PRIMARY KEY,
    name VARCHAR(160) NOT NULL,
    synthetic_identifier VARCHAR(80) NOT NULL,
    CONSTRAINT applicant_synthetic_identifier_unique UNIQUE (synthetic_identifier)
);

CREATE TABLE proposal (
    id UUID PRIMARY KEY,
    applicant_id UUID NOT NULL REFERENCES applicant(id),
    declared_monthly_income NUMERIC(19, 2) NOT NULL,
    currency CHAR(3) NOT NULL,
    status VARCHAR(40) NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT proposal_income_nonnegative CHECK (declared_monthly_income >= 0)
);
