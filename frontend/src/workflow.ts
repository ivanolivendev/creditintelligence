import type { DocumentItem } from "./api";

export const REQUIRED_DOCUMENT_TYPES = [
  "IDENTIFICATION",
  "INCOME_PROOF",
  "PAYSLIP",
  "BANK_STATEMENT",
] as const;

export type RequiredDocumentType = (typeof REQUIRED_DOCUMENT_TYPES)[number];

export const SANDBOX_BUREAU_CASES = [
  { cpf: "11144477735", caseName: "COMPLETE", label: "Caso completo" },
  { cpf: "52998224725", caseName: "CLEAN", label: "Caso limpo" },
  { cpf: "50250250284", caseName: "UNAVAILABLE", label: "Caso indisponível" },
] as const;

export function normalizeCpf(value: string): string {
  return value.replace(/\D/g, "").slice(0, 11);
}

export function formatCpf(value: string): string {
  const cpf = normalizeCpf(value);
  return cpf
    .replace(/(\d{3})(\d)/, "$1.$2")
    .replace(/(\d{3})(\d)/, "$1.$2")
    .replace(/(\d{3})(\d{1,2})$/, "$1-$2");
}

export function bureauCaseForCpf(
  value: string,
): (typeof SANDBOX_BUREAU_CASES)[number] | null {
  return (
    SANDBOX_BUREAU_CASES.find((item) => item.cpf === normalizeCpf(value)) ??
    null
  );
}

export function inferDocumentType(
  fileName: string,
): RequiredDocumentType | null {
  const name = fileName.toLowerCase();
  if (/ident|identity|rg|documento/.test(name)) return "IDENTIFICATION";
  if (/holerite|payslip|contracheque|salary/.test(name)) return "PAYSLIP";
  if (/extrato|statement|bank|moviment/.test(name)) return "BANK_STATEMENT";
  if (/renda|income|comprovante|proof/.test(name)) return "INCOME_PROOF";
  return null;
}

export function inferPeriod(fileName: string): string | undefined {
  const match = fileName.match(/(20\d{2})[-_](0[1-9]|1[0-2])/);
  return match ? `${match[1]}-${match[2]}` : undefined;
}

export function isReadyForAnalysis(
  documents: Pick<DocumentItem, "type" | "period">[],
): boolean {
  const count = (type: RequiredDocumentType) =>
    documents.filter((document) => document.type === type).length;
  return (
    count("IDENTIFICATION") >= 1 &&
    count("INCOME_PROOF") >= 1 &&
    count("PAYSLIP") >= 3 &&
    count("BANK_STATEMENT") >= 3 &&
    !financialPeriodMismatch(documents).hasMismatch
  );
}

export type FinancialPeriodMismatch = {
  hasMismatch: boolean;
  payslipPeriods: string[];
  bankStatementPeriods: string[];
  missingFromPayslips: string[];
  missingFromBankStatements: string[];
  unperiodedPayslips: number;
  unperiodedBankStatements: number;
  messages: string[];
};

export function financialPeriodMismatch(
  documents: Pick<DocumentItem, "type" | "period">[],
): FinancialPeriodMismatch {
  const payslips = documents.filter((document) => document.type === "PAYSLIP");
  const bankStatements = documents.filter(
    (document) => document.type === "BANK_STATEMENT",
  );
  const periodsFor = (items: typeof payslips) =>
    Array.from(
      new Set(
        items
          .map((document) => document.period?.trim())
          .filter((period): period is string => Boolean(period)),
      ),
    ).sort();
  const payslipPeriods = periodsFor(payslips);
  const bankStatementPeriods = periodsFor(bankStatements);
  const financialSetReadyForComparison =
    payslips.length >= 3 || bankStatements.length >= 3;
  const missingFromPayslips = bankStatementPeriods.filter(
    (period) => !payslipPeriods.includes(period),
  );
  const missingFromBankStatements = payslipPeriods.filter(
    (period) => !bankStatementPeriods.includes(period),
  );
  const unperiodedPayslips = payslips.filter(
    (document) => !document.period?.trim(),
  ).length;
  const unperiodedBankStatements = bankStatements.filter(
    (document) => !document.period?.trim(),
  ).length;
  const hasMismatch =
    financialSetReadyForComparison &&
    (payslipPeriods.length !== 3 ||
      bankStatementPeriods.length !== 3 ||
      unperiodedPayslips > 0 ||
      unperiodedBankStatements > 0 ||
      missingFromPayslips.length > 0 ||
      missingFromBankStatements.length > 0);
  const messages: string[] = [];
  if (financialSetReadyForComparison && payslipPeriods.length !== 3)
    messages.push("Holerites devem cobrir três competências distintas");
  if (financialSetReadyForComparison && bankStatementPeriods.length !== 3)
    messages.push("Extratos devem cobrir três competências distintas");
  if (unperiodedPayslips > 0)
    messages.push(String(unperiodedPayslips) + " holerite(s) sem competência");
  if (unperiodedBankStatements > 0)
    messages.push(
      String(unperiodedBankStatements) + " extrato(s) sem competência",
    );
  if (missingFromPayslips.length > 0)
    messages.push("Holerite(s) ausente(s): " + missingFromPayslips.join(", "));
  if (missingFromBankStatements.length > 0)
    messages.push(
      "Extrato(s) ausente(s): " + missingFromBankStatements.join(", "),
    );

  return {
    hasMismatch,
    payslipPeriods,
    bankStatementPeriods,
    missingFromPayslips,
    missingFromBankStatements,
    unperiodedPayslips,
    unperiodedBankStatements,
    messages,
  };
}

export function explanationProviderCopy(
  provider: string | null | undefined,
  status: string | null | undefined,
) {
  const normalized = provider?.trim().toUpperCase();
  if (normalized === "MOCK") {
    return {
      label: "MOCK",
      description:
        "Saída determinística de demonstração; não é inferência Bedrock.",
    };
  }
  if (normalized === "BEDROCK") {
    return {
      label: "BEDROCK",
      description: "Explicação gerada pelo provedor Bedrock.",
    };
  }
  return {
    label: normalized || "INDISPONÍVEL",
    description:
      status && status !== "COMPLETED"
        ? "Explicação " + status.toLowerCase().replaceAll("_", " ") + "."
        : "A explicação não informou um provedor.",
  };
}

export function documentReadiness(
  documents: Pick<DocumentItem, "type" | "period">[],
) {
  const count = (type: RequiredDocumentType) =>
    documents.filter((document) => document.type === type).length;
  return [
    {
      type: "IDENTIFICATION" as const,
      current: count("IDENTIFICATION"),
      required: 1,
    },
    {
      type: "INCOME_PROOF" as const,
      current: count("INCOME_PROOF"),
      required: 1,
    },
    { type: "PAYSLIP" as const, current: count("PAYSLIP"), required: 3 },
    {
      type: "BANK_STATEMENT" as const,
      current: count("BANK_STATEMENT"),
      required: 3,
    },
  ];
}
