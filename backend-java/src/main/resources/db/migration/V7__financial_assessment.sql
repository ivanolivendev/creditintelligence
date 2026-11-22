INSERT INTO policy_version (id, version, effective_from, config_json, text_content, checksum) VALUES (
    '22222222-2222-4222-8222-222222222222', 'CREDIT_POLICY_DEMO_2.0', '2026-11-20T00:00:00Z',
    '{"incomeTolerancePercent":"5.00","requiredDocumentTypes":["PAYSLIP","BANK_STATEMENT"],"commitmentLimitRatio":"0.35","salaryMatchToleranceAbsolute":"100.00","incomeStabilityWarningRatio":"0.20","simulationTermMonths":36,"simulationMonthlyInterestRate":"0.02"}',
    'CREDIT_POLICY_DEMO_2.0: Financial pre-analysis uses three verified net salary periods, reconciles documented salary credits within BRL 100.00, excludes payroll deductions already reflected in net pay, and simulates a 35 percent commitment ceiling. Human review is mandatory.',
    '27f4d934812e4acf847bc0b30fb32964f277188a811978575299b2e9a99af787'
);

CREATE TABLE financial_assessment (
    analysis_id UUID PRIMARY KEY REFERENCES analysis(id),
    policy_version_id UUID NOT NULL REFERENCES policy_version(id),
    rule_version VARCHAR(40) NOT NULL,
    profile_json JSONB NOT NULL,
    result_json JSONB NOT NULL,
    created_at TIMESTAMPTZ NOT NULL
);
