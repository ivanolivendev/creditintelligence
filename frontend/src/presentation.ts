import type { Analysis } from "./api";

export const money = (v: number | null | undefined) =>
  v == null
    ? "—"
    : new Intl.NumberFormat("pt-BR", {
        style: "currency",
        currency: "BRL",
      }).format(v);

export const date = (v: string) =>
  new Intl.DateTimeFormat("pt-BR", { dateStyle: "medium" }).format(new Date(v));

export const labels: Record<string, string> = {
  IDENTIFICATION: "Identidade",
  INCOME_PROOF: "Comprovante de renda",
  PAYSLIP: "Holerite",
  BANK_STATEMENT: "Extrato bancário",
};

export const fieldLabels: Record<string, string> = {
  competence: "Competência",
  grossSalary: "Salário bruto",
  monthlyIncome: "Renda mensal",
  netSalary: "Salário líquido",
  payrollDeduction: "Desconto consignado",
  statementPeriod: "Período do extrato",
};

export const findingLabels: Record<string, string> = {
  INCOME_CONSISTENCY: "Renda declarada x documentos",
  INCOME_STABILITY: "Estabilidade da renda",
  OBLIGATION_DEDUPLICATION: "Descontos já considerados",
  PAYMENT_CAPACITY: "Capacidade de pagamento",
  REQUIRED_BANK_STATEMENTS: "Extratos obrigatórios",
  REQUIRED_PAYSLIPS: "Holerites obrigatórios",
  SALARY_RECONCILIATION: "Conciliação dos salários",
};

export const resultLabels: Record<string, string> = {
  PASS: "Conforme",
  WARNING: "Atenção",
  FAIL: "Não conforme",
};

export function humanFindingDetails(f: Analysis["findings"][number]) {
  if (f.code === "INCOME_CONSISTENCY") {
    const values = f.details.match(
      /Declared=([\d.]+).*document=([\d.]+).*difference=([\d.]+)%; tolerance=([\d.]+)/,
    );
    if (values) {
      const declared = Number(values[1]);
      const document = Number(values[2]);
      const difference = declared - document;
      return `A média líquida comprovada é ${money(document)}, ${money(Math.abs(difference))} abaixo da renda declarada. A tolerância da política é ${values[4]}%.`;
    }
  }
  if (f.code === "INCOME_STABILITY")
    return "A variação entre os salários líquidos ficou dentro do limite definido pela política.";
  if (f.code === "OBLIGATION_DEDUPLICATION")
    return "Os descontos consignados já estão refletidos no salário líquido e não foram contados duas vezes.";
  if (f.code === "PAYMENT_CAPACITY") {
    const value = f.details.match(/Maximum new installment=([\d.]+)/)?.[1];
    return value
      ? `A parcela máxima simulada, conforme os documentos, é ${money(Number(value))}.`
      : "A capacidade de pagamento foi calculada a partir da renda documentada.";
  }
  if (f.code === "REQUIRED_BANK_STATEMENTS")
    return "Os três períodos de extrato esperados foram encontrados.";
  if (f.code === "REQUIRED_PAYSLIPS")
    return "Foram encontrados três salários líquidos distintos nos holerites.";
  if (f.code === "SALARY_RECONCILIATION")
    return "Os salários dos holerites foram conciliados com os períodos esperados dentro da tolerância.";
  return f.details;
}

export function displayFactValue(fieldName: string, value: string | null) {
  if (!value) return "Não identificado";
  if (
    ["grossSalary", "monthlyIncome", "netSalary", "payrollDeduction"].includes(
      fieldName,
    )
  ) {
    const numeric = Number(value);
    return Number.isFinite(numeric) ? money(numeric) : value;
  }
  return value;
}
