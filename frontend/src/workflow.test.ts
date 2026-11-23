import { describe, expect, it } from "vitest";
import {
  explanationProviderCopy,
  financialPeriodMismatch,
  isReadyForAnalysis,
} from "./workflow";

describe("document readiness", () => {
  it("requires the complete documentary set", () => {
    expect(isReadyForAnalysis([{ type: "IDENTIFICATION", period: null }])).toBe(
      false,
    );
    expect(
      isReadyForAnalysis([
        { type: "IDENTIFICATION", period: null },
        { type: "INCOME_PROOF", period: null },
        { type: "PAYSLIP", period: "2026-01" },
        { type: "PAYSLIP", period: "2026-02" },
        { type: "PAYSLIP", period: "2026-03" },
        { type: "BANK_STATEMENT", period: "2026-01" },
        { type: "BANK_STATEMENT", period: "2026-02" },
        { type: "BANK_STATEMENT", period: "2026-03" },
      ]),
    ).toBe(true);
  });

  it("does not mark unperioded financial documents ready", () => {
    expect(
      isReadyForAnalysis([
        { type: "IDENTIFICATION", period: null },
        { type: "INCOME_PROOF", period: null },
        { type: "PAYSLIP", period: null },
        { type: "PAYSLIP", period: null },
        { type: "PAYSLIP", period: null },
        { type: "BANK_STATEMENT", period: null },
        { type: "BANK_STATEMENT", period: null },
        { type: "BANK_STATEMENT", period: null },
      ]),
    ).toBe(false);
  });

  it("rejects financial documents with mismatched periods and explains the fix", () => {
    const documents = [
      { type: "IDENTIFICATION", period: null },
      { type: "INCOME_PROOF", period: null },
      { type: "PAYSLIP", period: "2026-01" },
      { type: "PAYSLIP", period: "2026-02" },
      { type: "PAYSLIP", period: "2026-03" },
      { type: "BANK_STATEMENT", period: "2026-01" },
      { type: "BANK_STATEMENT", period: "2026-02" },
      { type: "BANK_STATEMENT", period: "2026-04" },
    ];
    const mismatch = financialPeriodMismatch(documents);

    expect(isReadyForAnalysis(documents)).toBe(false);
    expect(mismatch.hasMismatch).toBe(true);
    expect(mismatch.missingFromBankStatements).toEqual(["2026-03"]);
    expect(mismatch.missingFromPayslips).toEqual(["2026-04"]);
    expect(mismatch.messages).toEqual([
      "Holerite(s) ausente(s): 2026-04",
      "Extrato(s) ausente(s): 2026-03",
    ]);
  });

  it("does not require periods from non-financial documents", () => {
    expect(
      isReadyForAnalysis([
        { type: "IDENTIFICATION", period: null },
        { type: "INCOME_PROOF", period: null },
        { type: "PAYSLIP", period: "2026-01" },
        { type: "PAYSLIP", period: "2026-02" },
        { type: "PAYSLIP", period: "2026-03" },
        { type: "BANK_STATEMENT", period: "2026-01" },
        { type: "BANK_STATEMENT", period: "2026-02" },
        { type: "BANK_STATEMENT", period: "2026-03" },
      ]),
    ).toBe(true);
  });

  it("rejects duplicate slots that provide fewer than three distinct periods", () => {
    const documents = [
      { type: "IDENTIFICATION", period: null },
      { type: "INCOME_PROOF", period: null },
      { type: "PAYSLIP", period: "2026-01" },
      { type: "PAYSLIP", period: "2026-01" },
      { type: "PAYSLIP", period: "2026-02" },
      { type: "BANK_STATEMENT", period: "2026-01" },
      { type: "BANK_STATEMENT", period: "2026-01" },
      { type: "BANK_STATEMENT", period: "2026-02" },
    ];

    expect(isReadyForAnalysis(documents)).toBe(false);
    expect(financialPeriodMismatch(documents).hasMismatch).toBe(true);
  });

  it("labels persisted explanation providers accurately", () => {
    expect(explanationProviderCopy("MOCK", "COMPLETED")).toEqual({
      label: "MOCK",
      description:
        "Saída determinística de demonstração; não é inferência Bedrock.",
    });
    expect(explanationProviderCopy("BEDROCK", "COMPLETED").label).toBe(
      "BEDROCK",
    );
  });
});
