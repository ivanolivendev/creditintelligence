INSERT INTO policy_version (id, version, effective_from, config_json, text_content, checksum) VALUES (
    '33333333-3333-4333-8333-333333333333', 'CREDIT_POLICY_DEMO_3.4', '2026-11-21T00:00:00Z',
    '{"incomeTolerancePercent":"3.00","requiredDocumentTypes":["PAYSLIP","BANK_STATEMENT"],"commitmentLimitRatio":"0.30","salaryMatchTolerancePercent":"3.00","incomeStabilityWarningRatio":"0.20","simulationTermMonths":36,"simulationMonthlyInterestRate":"0.02"}',
    'CREDIT_POLICY_DEMO_3.4: Financial pre-analysis uses three verified net salary periods, reconciles documented salary credits within 3 percent of net pay, excludes payroll deductions already reflected in net pay, and simulates a 30 percent commitment ceiling. Human review is mandatory.',
    'a061fef313a51f55cf72f5c85cefc1b5a3d25d8d11a9f5f6d56781d9d8e36058'
);
