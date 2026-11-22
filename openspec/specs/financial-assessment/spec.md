# financial-assessment Specification

## Purpose

Provide a reproducible financial pre-analysis of synthetic documentary evidence for an analyst, without issuing an automated credit decision.

## Requirements

### Requirement: Consolidate valid income periods
The system SHALL calculate a reference monthly net income from three distinct, factual payslip periods and expose the count, minimum, maximum, variability, currency, and evidence. Fewer valid periods SHALL remain provisional or not evaluated.

#### Scenario: Three valid periods
- **WHEN** three distinct payslips contain BRL 7300.00, 8500.00, and 7400.00 net income
- **THEN** the reference income is BRL 7733.33 with three evidence-linked periods

#### Scenario: Missing period
- **WHEN** one of the three payslips has missing or ambiguous net income
- **THEN** the income is not marked verified and the missing fact is not inferred

### Requirement: Reconcile salary and obligations
The system SHALL match eligible salary credits to payslip net amounts by period and tolerance, without reusing a transaction. It SHALL distinguish unknown credits and obligations already included in net pay from additional payments.

#### Scenario: Unrelated credit
- **WHEN** a bank statement contains an unclassified credit in addition to an eligible salary credit
- **THEN** the unclassified credit does not increase reference income

#### Scenario: Payroll deduction
- **WHEN** a payroll deduction is already included in the net salary and appears in bank evidence
- **THEN** the deduction is not subtracted again from capacity and uncertainty requests review

### Requirement: Calculate capacity and Price simulation
Java SHALL calculate capacity and a partial Price simulation from a versioned demo policy and validated scenario using decimal arithmetic, explicit BRL currency, and final HALF_UP rounding. It SHALL NOT return an approval or binding offer.

#### Scenario: Demonstration capacity
- **WHEN** reference income is BRL 8000.00, additional installments BRL 1500.00, and the policy ratio is 0.35
- **THEN** the total ceiling is BRL 2800.00 and new-installment capacity is BRL 1300.00

#### Scenario: Zero interest
- **WHEN** the new-installment capacity is BRL 1300.00, term is 36 months, and monthly rate is zero
- **THEN** the simulated principal is BRL 46800.00 and the result is labeled partial

#### Scenario: Invalid scenario
- **WHEN** term is zero or the interest rate is negative
- **THEN** the scenario is rejected without a financial result
