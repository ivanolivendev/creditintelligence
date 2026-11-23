export type Credentials = { username: string; password: string };
export type Proposal = {
  id: string;
  applicant: { name: string; syntheticIdentifier: string };
  declaredMonthlyIncome: number;
  currency: string;
  status: string;
  createdAt: string;
};
export type DocumentItem = {
  id: string;
  type: string;
  version: number;
  sha256: string;
  uploadedAt: string;
  status: string;
  period: string | null;
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
  financialAssessment: {
    bureau: BureauReport | null;
  } | null;
};
export type Decision = {
  id: string;
  analysisId: string;
  analystId: string;
  outcome: string;
  reason: string;
  createdAt: string;
};
export type BureauReport = {
  provider: string;
  product: string;
  environment: string;
  status: string;
  referenceId: string | null;
  restrictions: { count: number | null } | null;
  score: { value: number; model: string } | null;
  limitations: string[];
};
export class ApiError extends Error {
  constructor(
    public status: number,
    message: string,
    public code: string | null = null,
    public correlationId: string | null = null,
  ) {
    super(message);
  }
}

function problemMessage(status: number, code: string | null, detail: string) {
  const messages: Record<string, string> = {
    BUREAU_UNAVAILABLE: "O serviço de bureau está indisponível no momento.",
    DOCUMENT_INTELLIGENCE_UNAVAILABLE:
      "O serviço de leitura de documentos está indisponível.",
    INVALID_EXTRACTION_CONTRACT:
      "A leitura dos documentos retornou um formato inválido.",
    CONFLICT:
      "A operação não pode ser concluída com o estado atual da proposta.",
    BAD_REQUEST: "Revise os dados informados e tente novamente.",
  };
  return messages[code ?? ""] ?? detail ?? `Erro HTTP ${status}`;
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
    let code: string | null = null;
    let correlationId = response.headers.get("X-Correlation-ID");
    try {
      const problem = (await response.json()) as {
        detail?: string;
        code?: string;
        correlationId?: string;
      };
      detail = problem.detail || detail;
      code = problem.code || null;
      correlationId = problem.correlationId || correlationId;
    } catch {
      detail = `Erro HTTP ${response.status}`;
    }
    throw new ApiError(
      response.status,
      problemMessage(response.status, code, detail),
      code,
      correlationId,
    );
  }
  return (await response.json()) as T;
}
export const api = {
  createProposal: (c: Credentials, name: string, income: string) =>
    request<Proposal>(c, "/api/v1/proposals", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({
        applicant: { name },
        declaredMonthlyIncome: Number(income),
        currency: "BRL",
      }),
    }),
  listProposals: (c: Credentials) =>
    request<Proposal[]>(c, "/api/v1/proposals"),
  getProposal: (c: Credentials, id: string) =>
    request<Proposal>(c, `/api/v1/proposals/${id}`),
  listDocuments: (c: Credentials, id: string) =>
    request<DocumentItem[]>(c, `/api/v1/proposals/${id}/documents`),
  upload: (
    c: Credentials,
    id: string,
    type: string,
    file: File,
    period?: string,
  ) => {
    const body = new FormData();
    body.append("type", type);
    if (period) body.append("period", period);
    body.append("file", file);
    return request<DocumentItem>(c, `/api/v1/proposals/${id}/documents`, {
      method: "POST",
      body,
    });
  },
  analyze: (c: Credentials, id: string, scenario?: object) =>
    request<Analysis>(c, `/api/v1/proposals/${id}/analyses`, {
      method: "POST",
      headers: {
        "Idempotency-Key": crypto.randomUUID(),
        ...(scenario ? { "Content-Type": "application/json" } : {}),
      },
      ...(scenario ? { body: JSON.stringify(scenario) } : {}),
    }),
  getAnalysis: (c: Credentials, id: string, analysisId: string) =>
    request<Analysis>(c, `/api/v1/proposals/${id}/analyses/${analysisId}`),
  latestBureau: (c: Credentials, id: string) =>
    request<{ id: string | null; report: BureauReport }>(
      c,
      `/api/v1/proposals/${id}/bureau-consultations/latest`,
    ),
  consultBureau: (c: Credentials, id: string, caseName: string) =>
    request<{ consultationId: string; report: BureauReport }>(
      c,
      `/api/v1/proposals/${id}/bureau-consultations`,
      {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ caseName }),
      },
    ),
  decide: (
    c: Credentials,
    id: string,
    a: Analysis,
    outcome: string,
    reason: string,
  ) =>
    request<Decision>(c, `/api/v1/proposals/${id}/decisions`, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({
        analysisId: a.analysisId,
        documentSnapshotHash: a.documentSnapshotHash,
        outcome,
        reason,
      }),
    }),
  listDecisions: (c: Credentials, id: string) =>
    request<Decision[]>(c, `/api/v1/proposals/${id}/decisions`),
};
