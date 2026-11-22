export type Credentials = { username: string; password: string };

export type Proposal = {
  id: string;
  applicant: { name: string; syntheticIdentifier: string };
  declaredMonthlyIncome: number;
  currency: string;
  status: string;
  createdAt: string;
};

export type Document = {
  id: string;
  type: string;
  version: number;
  sha256: string;
  uploadedAt: string;
  status: string;
  period: string | null;
};

export type FinancialAssessment = {
  currency: string;
  policyVersion: string;
  ruleVersion: string;
  reviewStatus: string;
  validIncomePeriods: number;
  averageNetIncome: number | null;
  minimumNetIncome: number | null;
  maximumNetIncome: number | null;
  variabilityRatio: number | null;
  salaryMatches: Array<{
    period: string;
    payslipNet: number;
    bankCredit: number;
    difference: number;
    status: string;
    evidenceRefs: string[];
  }>;
  unknownCredits: number;
  payrollDeductionsIncludedInNet: number;
  additionalMonthlyPayments: number;
  obligationStatus: string;
  maxTotalInstallments: number | null;
  maxNewInstallment: number | null;
  simulation: {
    termMonths: number;
    monthlyInterestRate: number;
    requestedPrincipal: number | null;
    requestedPayment: number | null;
    estimatedPrincipalAtCapacity: number;
    pricingSource: string;
    limitation: string;
  } | null;
  limitations: string[];
  bureau: {
    provider: string;
    product: string;
    environment: string;
    status: string;
    referenceId: string | null;
    restrictions: { count: number | null } | null;
    score: { value: number; model: string } | null;
    limitations: string[];
  } | null;
};

export type Analysis = {
  analysisId: string;
  proposalId: string;
  status: string;
  attempt: number;
  documentSnapshotHash: string;
  policyVersion: string;
  policyChecksum: string;
  declaredMonthlyIncome: number;
  currency: string;
  incomeTolerancePercent: number;
  requiredDocumentTypes: string[];
  documents: Array<{
    documentId: string;
    type: string;
    version: number;
    sha256: string;
    period: string | null;
  }>;
  extractedFields: Array<{
    documentId: string;
    version: number;
    fieldName: string;
    value: string | null;
    currency: string | null;
    extractionStatus: string;
    evidenceRef: string | null;
    page: number | null;
    snippet: string | null;
  }>;
  findings: Array<{
    code: string;
    result: string;
    details: string;
    evidenceRef: string | null;
  }>;
  aiExplanationStatus: string;
  aiExplanation: {
    provider: string | null;
    summary: string | null;
    limitations: string[];
  };
  errorCode: string | null;
  financialAssessment: FinancialAssessment | null;
};

export type Decision = {
  id: string;
  analysisId: string;
  analystId: string;
  outcome: string;
  reason: string;
  createdAt: string;
};

export type BureauSnapshot = {
  id: string | null;
  report: FinancialAssessment["bureau"];
};

export class ApiError extends Error {
  constructor(
    public status: number,
    message: string,
  ) {
    super(message);
  }
}

async function request<T>(
  credentials: Credentials,
  path: string,
  init: RequestInit = {},
): Promise<T> {
  const headers = new Headers(init.headers);
  headers.set(
    "Authorization",
    `Basic ${btoa(`${credentials.username}:${credentials.password}`)}`,
  );
  headers.set("X-Correlation-ID", crypto.randomUUID());
  const response = await fetch(path, { ...init, headers });
  if (!response.ok) {
    let detail = `Erro HTTP ${response.status}`;
    try {
      const problem = (await response.json()) as { detail?: string };
      detail = problem.detail || detail;
    } catch {
      // Keep the HTTP status when the server did not return Problem Details.
    }
    throw new ApiError(response.status, detail);
  }
  return (await response.json()) as T;
}

export const api = {
  createProposal(
    credentials: Credentials,
    name: string,
    identifier: string,
    income: string,
  ) {
    return request<Proposal>(credentials, "/api/v1/proposals", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({
        applicant: { name, syntheticIdentifier: identifier },
        declaredMonthlyIncome: Number(income),
        currency: "BRL",
      }),
    });
  },
  getProposal(credentials: Credentials, id: string) {
    return request<Proposal>(credentials, `/api/v1/proposals/${id}`);
  },
  listDocuments(credentials: Credentials, id: string) {
    return request<Document[]>(
      credentials,
      `/api/v1/proposals/${id}/documents`,
    );
  },
  upload(
    credentials: Credentials,
    id: string,
    type: string,
    file: File,
    period?: string,
  ) {
    const body = new FormData();
    body.append("type", type);
    if (period) body.append("period", period);
    body.append("file", file);
    return request<Document>(credentials, `/api/v1/proposals/${id}/documents`, {
      method: "POST",
      body,
    });
  },
  analyze(
    credentials: Credentials,
    id: string,
    scenario?: {
      principalRequested: number | null;
      termMonths: number;
      monthlyInterestRate: number;
      currency: "BRL";
      pricingSource: "MANUAL_DEMO";
    },
  ) {
    return request<Analysis>(credentials, `/api/v1/proposals/${id}/analyses`, {
      method: "POST",
      headers: {
        "Idempotency-Key": crypto.randomUUID(),
        ...(scenario ? { "Content-Type": "application/json" } : {}),
      },
      ...(scenario ? { body: JSON.stringify(scenario) } : {}),
    });
  },
  getAnalysis(credentials: Credentials, id: string, analysisId: string) {
    return request<Analysis>(
      credentials,
      `/api/v1/proposals/${id}/analyses/${analysisId}`,
    );
  },
  latestBureau(credentials: Credentials, id: string) {
    return request<BureauSnapshot>(
      credentials,
      `/api/v1/proposals/${id}/bureau-consultations/latest`,
    );
  },
  consultBureau(credentials: Credentials, id: string, caseName: string) {
    return request<{
      consultationId: string;
      report: FinancialAssessment["bureau"];
    }>(credentials, `/api/v1/proposals/${id}/bureau-consultations`, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ caseName }),
    });
  },
  decide(
    credentials: Credentials,
    id: string,
    analysis: Analysis,
    outcome: string,
    reason: string,
  ) {
    return request<Decision>(credentials, `/api/v1/proposals/${id}/decisions`, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({
        analysisId: analysis.analysisId,
        documentSnapshotHash: analysis.documentSnapshotHash,
        outcome,
        reason,
      }),
    });
  },
  listDecisions(credentials: Credentials, id: string) {
    return request<Decision[]>(
      credentials,
      `/api/v1/proposals/${id}/decisions`,
    );
  },
};
