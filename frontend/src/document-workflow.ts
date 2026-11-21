import type { Analysis, Document } from "./api";

export const PAYSLIP = "PAYSLIP";
export const BANK_STATEMENT = "BANK_STATEMENT";

export type UploadDraft = {
  id: string;
  file: File;
  type: string;
  period: string;
  detectedType: string | null;
  detectedPeriod: string | null;
};

export type FinancialCoverage = {
  payslipPeriods: string[];
  statementPeriods: string[];
  matchedPeriods: string[];
  complete: boolean;
};

const normalizeFileName = (fileName: string) =>
  fileName
    .normalize("NFD")
    .replace(/[\u0300-\u036f]/g, "")
    .toLowerCase();

export function inferDocumentMetadata(fileName: string) {
  const normalized = normalizeFileName(fileName);
  const type = /payslip|contracheque|holerite/.test(normalized)
    ? PAYSLIP
    : /statement|extrato/.test(normalized)
      ? BANK_STATEMENT
      : null;
  const yearFirst = normalized.match(/(20\d{2})[-_. ](0[1-9]|1[0-2])/);
  const monthFirst = normalized.match(/(0[1-9]|1[0-2])[-_. ](20\d{2})/);
  const period = yearFirst
    ? `${yearFirst[1]}-${yearFirst[2]}`
    : monthFirst
      ? `${monthFirst[2]}-${monthFirst[1]}`
      : null;
  return { type, period };
}

export function validateUploadDrafts(drafts: UploadDraft[]) {
  const errors = new Map<string, string[]>();
  const slots = new Map<string, string[]>();

  for (const draft of drafts) {
    const problems: string[] = [];
    const financial = draft.type === PAYSLIP || draft.type === BANK_STATEMENT;
    if (!draft.file.name.toLowerCase().endsWith(".pdf")) {
      problems.push("Selecione um arquivo PDF.");
    }
    if (draft.file.size > 10 * 1024 * 1024) {
      problems.push("O arquivo excede o limite de 10 MiB.");
    }
    if (financial && !/^20\d{2}-(0[1-9]|1[0-2])$/.test(draft.period)) {
      problems.push("Informe uma competência válida.");
    }
    if (draft.detectedType && draft.detectedType !== draft.type) {
      problems.push("O tipo escolhido diverge do nome do arquivo.");
    }
    if (
      financial &&
      draft.detectedPeriod &&
      draft.detectedPeriod !== draft.period
    ) {
      problems.push("A competência escolhida diverge do nome do arquivo.");
    }
    const slot = financial ? `${draft.type}:${draft.period}` : draft.type;
    slots.set(slot, [...(slots.get(slot) ?? []), draft.id]);
    errors.set(draft.id, problems);
  }

  for (const ids of slots.values()) {
    if (ids.length < 2) continue;
    for (const id of ids) {
      errors.get(id)?.push("Há outro arquivo para o mesmo tipo e competência.");
    }
  }
  return errors;
}

export function latestDocumentVersions(documents: Document[]) {
  const latest = new Map<string, Document>();
  for (const document of documents) {
    const slot = `${document.type}:${document.period ?? ""}`;
    const current = latest.get(slot);
    if (!current || document.version > current.version)
      latest.set(slot, document);
  }
  return [...latest.values()].sort((left, right) => {
    const periodOrder = (left.period ?? "").localeCompare(right.period ?? "");
    return periodOrder || left.type.localeCompare(right.type);
  });
}

export function summarizeFinancialCoverage(
  documents: Document[],
): FinancialCoverage {
  const current = latestDocumentVersions(documents);
  const payslipPeriods = current
    .filter((document) => document.type === PAYSLIP && document.period)
    .map((document) => document.period!)
    .sort();
  const statementPeriods = current
    .filter((document) => document.type === BANK_STATEMENT && document.period)
    .map((document) => document.period!)
    .sort();
  const statementSet = new Set(statementPeriods);
  const matchedPeriods = payslipPeriods.filter((period) =>
    statementSet.has(period),
  );
  return {
    payslipPeriods,
    statementPeriods,
    matchedPeriods,
    complete:
      payslipPeriods.length >= 3 &&
      statementPeriods.length >= 3 &&
      matchedPeriods.length >= 3,
  };
}

export function analysisFailureMessage(
  analysis: Pick<Analysis, "status" | "errorCode">,
) {
  if (analysis.errorCode === "INVALID_EXTRACTION_RESPONSE") {
    return {
      title: "Os documentos precisam ser corrigidos",
      message:
        "Um PDF não corresponde ao tipo ou à competência informada. Revise a lista, corrija o arquivo indicado e execute uma nova análise.",
      retryable: false,
    };
  }
  if (
    analysis.status === "FAILED_RETRYABLE" ||
    analysis.errorCode === "DOCUMENT_INTELLIGENCE_UNAVAILABLE"
  ) {
    return {
      title: "O serviço de documentos está temporariamente indisponível",
      message:
        "Os arquivos foram preservados. Aguarde um momento e tente executar a análise novamente.",
      retryable: true,
    };
  }
  return {
    title: "Não foi possível concluir a análise",
    message:
      "Revise os documentos antes de tentar novamente. Os detalhes técnicos estão disponíveis abaixo.",
    retryable: analysis.status !== "FAILED_PERMANENT",
  };
}
