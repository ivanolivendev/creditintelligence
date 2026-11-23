import { describe, expect, it } from "vitest";
import type { Document } from "./api";
import {
  BANK_STATEMENT,
  PAYSLIP,
  analysisFailureMessage,
  inferDocumentMetadata,
  latestDocumentVersions,
  summarizeFinancialCoverage,
  validateUploadDrafts,
  type UploadDraft,
} from "./document-workflow";

const file = (name: string, size = 100) =>
  new File([new Uint8Array(size)], name, { type: "application/pdf" });

const draft = (
  id: string,
  name: string,
  type: string,
  period: string,
): UploadDraft => {
  const inferred = inferDocumentMetadata(name);
  return {
    id,
    file: file(name),
    type,
    period,
    detectedType: inferred.type,
    detectedPeriod: inferred.period,
  };
};

const document = (
  id: string,
  type: string,
  period: string,
  version = 1,
): Document => ({
  id,
  type,
  period,
  version,
  sha256: id.padEnd(64, "0"),
  uploadedAt: "2026-11-20T21:00:00Z",
  status: "RECEIVED",
});

describe("document upload workflow", () => {
  it("infers the six controlled fixture names", () => {
    expect(inferDocumentMetadata("payslip-2026-09-demo.pdf")).toEqual({
      type: PAYSLIP,
      period: "2026-09",
    });
    expect(inferDocumentMetadata("extrato_10-2026.pdf")).toEqual({
      type: BANK_STATEMENT,
      period: "2026-10",
    });
  });

  it("rejects conflicting metadata and duplicate slots", () => {
    const drafts = [
      draft("a", "payslip-2026-08-demo.pdf", PAYSLIP, "2026-09"),
      draft("b", "payslip-2026-09-demo.pdf", PAYSLIP, "2026-09"),
    ];
    const errors = validateUploadDrafts(drafts);
    expect(errors.get("a")).toContain(
      "A competência escolhida diverge do nome do arquivo.",
    );
    expect(errors.get("a")).toContain(
      "Há outro arquivo para o mesmo tipo e competência.",
    );
    expect(errors.get("b")).toContain(
      "Há outro arquivo para o mesmo tipo e competência.",
    );
  });

  it("uses only the latest version of each logical document", () => {
    const latest = latestDocumentVersions([
      document("old", PAYSLIP, "2026-08", 1),
      document("new", PAYSLIP, "2026-08", 2),
      document("statement", BANK_STATEMENT, "2026-08", 1),
    ]);
    expect(latest.map((item) => item.id)).toEqual(["statement", "new"]);
  });

  it("requires three matching payslip and statement periods", () => {
    const documents = ["08", "09", "10"].flatMap((month) => [
      document(`p-${month}`, PAYSLIP, `2026-${month}`),
      document(`s-${month}`, BANK_STATEMENT, `2026-${month}`),
    ]);
    expect(summarizeFinancialCoverage(documents)).toMatchObject({
      matchedPeriods: ["2026-08", "2026-09", "2026-10"],
      complete: true,
    });
  });

  it("does not describe a permanent extraction failure as retryable", () => {
    const failure = analysisFailureMessage({
      status: "FAILED_PERMANENT",
      errorCode: "INVALID_EXTRACTION_RESPONSE",
    });
    expect(failure.retryable).toBe(false);
    expect(failure.message).toContain("não corresponde");
  });
});
