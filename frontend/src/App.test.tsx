import {
  cleanup,
  fireEvent,
  render,
  screen,
  waitFor,
} from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { api } from "./api";
import { App } from "./App";

const credentials = {
  username: "analyst",
  password: "local_demo_only_change_me",
};
const proposal = {
  id: "proposal-1",
  applicant: { name: "Maria Silva", syntheticIdentifier: "11144477735" },
  declaredMonthlyIncome: 8000,
  currency: "BRL",
  status: "CREATED",
  createdAt: "2026-11-22T10:00:00Z",
};

function login() {
  fireEvent.click(screen.getByRole("button", { name: /acessar ambiente/i }));
}

function createProposalMocks() {
  vi.spyOn(api, "createProposal").mockResolvedValue(proposal);
  vi.spyOn(api, "listDocuments").mockResolvedValue([]);
}

describe("analyst journey components", () => {
  beforeEach(() => {
    cleanup();
    vi.restoreAllMocks();
  });

  it("creates a proposal using a synthetic CPF and opens document ingestion", async () => {
    createProposalMocks();
    render(<App />);
    login();

    await userEvent.type(
      screen.getByLabelText("Nome para a demonstração"),
      "Maria Silva",
    );
    await userEvent.type(
      screen.getByLabelText("Renda mensal declarada"),
      "8000",
    );
    await userEvent.type(
      screen.getByLabelText("CPF para análise"),
      "11144477735",
    );
    await userEvent.click(
      screen.getByRole("button", { name: /criar proposta/i }),
    );

    await screen.findByText("Solte tudo de uma vez.");
    expect(api.createProposal).toHaveBeenCalledWith(
      credentials,
      "Maria Silva",
      "8000",
    );
  });

  it("shows the backend error when proposal creation fails", async () => {
    vi.spyOn(api, "createProposal").mockRejectedValue(
      new Error("Serviço indisponível"),
    );
    render(<App />);
    login();
    await userEvent.type(
      screen.getByLabelText("Nome para a demonstração"),
      "Maria",
    );
    await userEvent.type(
      screen.getByLabelText("Renda mensal declarada"),
      "8000",
    );
    await userEvent.type(
      screen.getByLabelText("CPF para análise"),
      "11144477735",
    );
    await userEvent.click(
      screen.getByRole("button", { name: /criar proposta/i }),
    );

    expect(await screen.findByRole("alert")).toHaveTextContent(
      "Serviço indisponível",
    );
  });

  it("uploads a PDF and reflects it in the linked document list", async () => {
    createProposalMocks();
    vi.spyOn(api, "upload").mockResolvedValue({
      id: "doc-1",
      type: "PAYSLIP",
      version: 1,
      sha256: "abcdef1234567890",
      uploadedAt: "2026-11-22T10:00:00Z",
      status: "ACTIVE",
      period: "2026-08",
    });
    render(<App />);
    login();
    await userEvent.type(
      screen.getByLabelText("Nome para a demonstração"),
      "Maria",
    );
    await userEvent.type(
      screen.getByLabelText("Renda mensal declarada"),
      "8000",
    );
    await userEvent.type(
      screen.getByLabelText("CPF para análise"),
      "11144477735",
    );
    await userEvent.click(
      screen.getByRole("button", { name: /criar proposta/i }),
    );
    await screen.findByText("Solte tudo de uma vez.");

    const file = new File(["synthetic pdf"], "payslip-2026-08.pdf", {
      type: "application/pdf",
    });
    fireEvent.change(screen.getByLabelText(/solte todos os pdfs/i), {
      target: { files: [file] },
    });
    expect(screen.getByText("payslip-2026-08.pdf")).toBeInTheDocument();
    await userEvent.click(
      screen.getByRole("button", { name: /enviar 1 arquivo/i }),
    );

    await waitFor(() => expect(api.upload).toHaveBeenCalled());
    expect(
      await screen.findByText("1 documentos vinculados"),
    ).toBeInTheDocument();
  });

  it("renders the dossier and allows a human decision after analysis", async () => {
    createProposalMocks();
    const documents = [
      {
        id: "doc-id",
        type: "IDENTIFICATION",
        version: 1,
        sha256: "id",
        uploadedAt: "2026-11-22",
        status: "ACTIVE",
        period: null,
      },
      {
        id: "doc-income",
        type: "INCOME_PROOF",
        version: 1,
        sha256: "income",
        uploadedAt: "2026-11-22",
        status: "ACTIVE",
        period: null,
      },
    ];
    vi.spyOn(api, "listDocuments").mockResolvedValue(documents);
    vi.spyOn(api, "analyze").mockResolvedValue({
      analysisId: "analysis-1",
      proposalId: proposal.id,
      status: "COMPLETED",
      attempt: 1,
      documentSnapshotHash: "snapshot-1234567890",
      policyVersion: "v1",
      policyChecksum: "checksum",
      declaredMonthlyIncome: 8000,
      currency: "BRL",
      incomeTolerancePercent: 10,
      requiredDocumentTypes: [],
      documents: [],
      extractedFields: [],
      findings: [
        { code: "INCOME_OK", result: "PASS", details: "Ok", evidenceRef: null },
      ],
      aiExplanationStatus: "AVAILABLE",
      aiExplanation: {
        provider: "BEDROCK",
        summary: "Renda consistente.",
        limitations: [],
      },
      errorCode: null,
      financialAssessment: null,
    });
    vi.spyOn(api, "decide").mockResolvedValue({
      id: "decision-1",
      analysisId: "analysis-1",
      analystId: "analyst",
      outcome: "APPROVED",
      reason: "Evidências suficientes para a decisão.",
      createdAt: "2026-11-22T10:00:00Z",
    });
    render(<App />);
    login();
    await userEvent.type(
      screen.getByLabelText("Nome para a demonstração"),
      "Maria",
    );
    await userEvent.type(
      screen.getByLabelText("Renda mensal declarada"),
      "8000",
    );
    await userEvent.type(
      screen.getByLabelText("CPF para análise"),
      "11144477735",
    );
    await userEvent.click(
      screen.getByRole("button", { name: /criar proposta/i }),
    );
    await screen.findByText("Solte tudo de uma vez.");
    await userEvent.click(
      screen.getByRole("button", { name: /continuar e ver pendências/i }),
    );
    await screen.findByText("2 documentos prontos para análise");
    await userEvent.click(
      screen.getByRole("button", { name: /analisar documentos/i }),
    );

    expect(await screen.findByText("Renda consistente.")).toBeInTheDocument();
    await userEvent.type(
      screen.getByLabelText("Justificativa da decisão"),
      "Evidências suficientes para a decisão.",
    );
    await userEvent.click(
      screen.getByRole("button", { name: /confirmar decisão/i }),
    );
    expect(
      await screen.findByText("Evidências suficientes para a decisão."),
    ).toBeInTheDocument();
    expect(api.decide).toHaveBeenCalled();
  });
});
