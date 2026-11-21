import { useRef, useState, type ChangeEvent, type FormEvent } from "react";
import {
  api,
  type Analysis,
  type BureauSnapshot,
  type Credentials,
  type Decision,
  type Document,
  type Proposal,
} from "./api";
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

const money = (value: number) =>
  new Intl.NumberFormat("pt-BR", { style: "currency", currency: "BRL" }).format(
    value,
  );

const percent = (value: number) =>
  new Intl.NumberFormat("pt-BR", {
    minimumFractionDigits: 2,
    maximumFractionDigits: 2,
  }).format(value) + "%";

const date = (value: string) =>
  new Intl.DateTimeFormat("pt-BR", {
    dateStyle: "short",
    timeStyle: "short",
    timeZone: "America/Sao_Paulo",
  }).format(new Date(value));

const newIdentifier = () =>
  `DEMO-${crypto.randomUUID().replaceAll("-", "").slice(0, 12)}`;

function badge(value: string) {
  const tone =
    value === "COMPLETED" || value === "PASS" || value === "AVAILABLE"
      ? "good"
      : value === "WARNING" ||
          value === "AMBIGUOUS" ||
          value === "MORE_INFO_REQUIRED"
        ? "warn"
        : value === "FAIL" || value === "REJECTED" || value.startsWith("FAILED")
          ? "bad"
          : "neutral";
  const labels: Record<string, string> = {
    COMPLETED: "Concluída",
    PASS: "Conforme",
    AVAILABLE: "Disponível",
    WARNING: "Revisar",
    AMBIGUOUS: "Ambíguo",
    MORE_INFO_REQUIRED: "Pedir informações",
    FAIL: "Pendente",
    REJECTED: "Rejeitada",
    NOT_EVALUATED: "Não avaliada",
    EXTRACTED: "Extraído",
    MISSING: "Não encontrado",
    INVALID: "Inválido",
    UNAVAILABLE: "Indisponível",
    INVALID_OUTPUT: "Resposta inválida",
    READY_FOR_REVIEW: "Pronta para revisão",
    MATCH: "Conciliado",
    NOT_REQUESTED: "Não consultado",
    FAILED_PERMANENT: "Falha permanente",
    FAILED_RETRYABLE: "Falha temporária",
  };
  return (
    <span className={`badge badge-${tone}`}>
      {labels[value] ?? value.replaceAll("_", " ")}
    </span>
  );
}

const documentLabels: Record<string, string> = {
  INCOME_PROOF: "Comprovante de renda",
  BANK_STATEMENT: "Extrato bancário",
  PAYSLIP: "Contracheque",
  IDENTIFICATION: "Documento de identificação",
};

const fieldLabels: Record<string, string> = {
  monthlyIncome: "Renda mensal",
  competence: "Competência",
  grossSalary: "Salário bruto",
  netSalary: "Salário líquido",
  payrollDeduction: "Consignado descontado em folha",
  statementPeriod: "Período do extrato",
};

function providerLabel(provider: string) {
  if (provider === "BEDROCK") return "Amazon Bedrock";
  if (provider === "MOCK") return "simulação local";
  return "explicação auxiliar";
}

function documentLabel(type: string) {
  return documentLabels[type] ?? type.replaceAll("_", " ").toLowerCase();
}

const formatPeriods = (periods: string[]) =>
  periods.length > 0 ? periods.join(", ") : "nenhuma competência";

const limitationLabels: Record<string, string> = {
  "Partial Price simulation excludes CET, IOF, insurance and fees; not a credit offer.":
    "A simulação Price não inclui CET, IOF, seguros ou tarifas e não representa uma oferta de crédito.",
  "Three factual net salary periods are required for verified capacity.":
    "São necessários três salários líquidos comprovados para verificar a capacidade.",
  "Salary credits require human reconciliation.":
    "Os créditos salariais exigem conferência humana.",
  "Known obligations are incomplete or uncertain.":
    "As obrigações conhecidas estão incompletas ou incertas.",
};

function FindingCard({
  finding,
  analysis,
}: {
  finding: Analysis["findings"][number];
  analysis: Analysis;
}) {
  const field = analysis.extractedFields.find(
    (item) => item.fieldName === "monthlyIncome",
  );
  const extractedIncome =
    field?.extractionStatus === "EXTRACTED" && field.value
      ? Number(field.value)
      : null;
  const checkedIncome =
    analysis.financialAssessment?.averageNetIncome ?? extractedIncome;
  const financialIncome = analysis.financialAssessment !== null;
  let title = "Verificação da análise";
  let explanation = finding.details;
  let nextStep = "Revise as evidências e registre sua avaliação humana.";
  let comparison: { declared: number; extracted: number } | null = null;
  let missingDocuments: string[] = [];

  if (finding.code === "INCOME_CONSISTENCY") {
    if (finding.result === "WARNING" && checkedIncome !== null) {
      const difference = Math.abs(
        analysis.declaredMonthlyIncome - checkedIncome,
      );
      const differencePercent =
        checkedIncome > 0 ? (difference / checkedIncome) * 100 : null;
      const percentageNote =
        differencePercent === null
          ? ""
          : ` (${percent(differencePercent)} do valor do comprovante)`;
      title = financialIncome
        ? "A renda líquida média diverge da proposta"
        : "A renda do comprovante diverge da proposta";
      explanation = `A diferença de ${money(difference)}${percentageNote} supera o limite de ${percent(analysis.incomeTolerancePercent)} definido na política ${analysis.policyVersion}.`;
      nextStep =
        "Confira os valores e evidências do período. Registre sua avaliação humana e justifique a decisão.";
      comparison = {
        declared: analysis.declaredMonthlyIncome,
        extracted: checkedIncome,
      };
    } else if (finding.result === "PASS" && checkedIncome !== null) {
      title = financialIncome
        ? "Renda líquida média compatível com a proposta"
        : "Renda compatível com o comprovante";
      explanation = `A diferença está dentro do limite de ${percent(analysis.incomeTolerancePercent)} definido na política ${analysis.policyVersion}.`;
      nextStep = financialIncome
        ? "Confirme as três competências e os depósitos correspondentes."
        : "Confirme se o comprovante corresponde ao período analisado.";
      comparison = {
        declared: analysis.declaredMonthlyIncome,
        extracted: checkedIncome,
      };
    } else {
      title = "Não foi possível conferir a renda";
      explanation = financialIncome
        ? "Não há três salários líquidos factuais para comparar com a renda declarada."
        : field?.extractionStatus === "AMBIGUOUS"
          ? "O comprovante contém valores de renda conflitantes; nenhum foi tratado como fato."
          : "Não encontramos um valor mensal de renda legível e inequívoco no comprovante.";
      nextStep = financialIncome
        ? "Revise as competências e envie contracheques legíveis antes de nova análise."
        : "Envie um comprovante de renda legível que mostre o valor mensal e execute uma nova análise.";
    }
  } else if (finding.code === "REQUIRED_DOCUMENTS") {
    if (finding.result === "PASS") {
      const requiredCount = analysis.requiredDocumentTypes.length;
      title =
        requiredCount === 1
          ? "Documento obrigatório recebido"
          : "Documentos obrigatórios recebidos";
      explanation =
        requiredCount === 1
          ? `O documento exigido pela política ${analysis.policyVersion} está no dossiê.`
          : `Todos os ${requiredCount} tipos de documento exigidos pela política ${analysis.policyVersion} estão no dossiê.`;
      nextStep = "Confira se os arquivos listados pertencem a esta proposta.";
    } else {
      missingDocuments = analysis.requiredDocumentTypes.filter(
        (required) =>
          !analysis.documents.some((item) => item.type === required),
      );
      title =
        missingDocuments.length === 1
          ? "Falta um documento obrigatório"
          : "Faltam documentos obrigatórios";
      explanation = missingDocuments.length
        ? missingDocuments.length === 1
          ? "Envie o item abaixo para completar o conjunto exigido pela política vigente."
          : "Envie os itens abaixo para completar o conjunto exigido pela política vigente."
        : "O conjunto de documentos desta proposta não atende à política vigente.";
      nextStep = "Envie os documentos pendentes e execute uma nova análise.";
    }
  } else if (finding.code === "REQUIRED_PAYSLIPS") {
    title = "Três contracheques por competência";
    explanation =
      finding.result === "PASS"
        ? "Os três salários líquidos foram extraídos com evidências."
        : "Faltam competências ou algum salário líquido está ausente ou ambíguo.";
    nextStep =
      "Confira cada competência e envie um PDF corrigido quando necessário.";
  } else if (finding.code === "REQUIRED_BANK_STATEMENTS") {
    title = "Três períodos de extrato";
    explanation =
      finding.result === "PASS"
        ? "Os três períodos possuem lançamentos para conciliação."
        : "A cobertura dos extratos ainda não permite verificar todos os meses.";
    nextStep =
      finding.result === "PASS"
        ? "Confira os lançamentos e as datas de cada extrato."
        : "Envie os extratos dos meses faltantes com texto selecionável.";
  } else if (finding.code === "SALARY_RECONCILIATION") {
    title = "Conciliação entre folha e depósitos";
    explanation =
      finding.result === "PASS"
        ? "Os créditos identificados nos extratos correspondem aos líquidos dos contracheques dentro da tolerância fictícia."
        : "Uma ou mais competências não correspondem aos créditos identificados; revise as duas fontes.";
    nextStep =
      "Confira os valores e as evidências de folha e extrato em cada mês.";
  } else if (finding.code === "OBLIGATION_DEDUPLICATION") {
    title = "Parcelas conhecidas e descontos em folha";
    explanation =
      finding.result === "PASS"
        ? "Parcelas já descontadas do líquido não foram subtraídas novamente."
        : "As parcelas conhecidas ainda exigem conferência humana.";
    nextStep =
      "Confirme se cada débito é uma obrigação adicional ou um desconto já refletido no líquido.";
  } else if (finding.code === "PAYMENT_CAPACITY") {
    title = "Capacidade teórica de nova prestação";
    explanation =
      finding.result === "PASS"
        ? "O teto foi calculado em Java pela política fictícia e pelas obrigações documentadas."
        : "Dados incompletos impedem apresentar um teto verificável.";
    nextStep = "Revise as limitações antes de qualquer decisão humana.";
  } else if (finding.code === "INCOME_STABILITY") {
    title = "Variação dos salários líquidos";
    explanation =
      finding.result === "PASS"
        ? "A variação observada ficou dentro do limiar da política de demonstração."
        : "A variação excedeu o limiar fictício e exige revisão.";
    nextStep =
      "Confira as três competências e a composição fixa ou variável da remuneração.";
  }

  const delta =
    comparison === null
      ? null
      : Math.abs(comparison.declared - comparison.extracted);
  const evidenceDocument = field
    ? analysis.documents.find((item) => item.documentId === field.documentId)
    : null;

  return (
    <article className={`finding-card finding-${finding.result.toLowerCase()}`}>
      <div className="finding-topline">
        <div>
          <span className="finding-category">
            {finding.code === "INCOME_CONSISTENCY"
              ? "CONFERÊNCIA DE RENDA"
              : finding.code === "REQUIRED_DOCUMENTS"
                ? "DOCUMENTAÇÃO"
                : "VERIFICAÇÃO"}
          </span>
          <h4>{title}</h4>
        </div>
        {badge(finding.result)}
      </div>
      <p>{explanation}</p>

      {comparison && delta !== null && (
        <div className="finding-comparison" aria-label="Comparação de renda">
          <div>
            <small>Informada na proposta</small>
            <strong>{money(comparison.declared)}</strong>
          </div>
          <span aria-hidden="true">→</span>
          <div>
            <small>
              {financialIncome
                ? "Média líquida dos contracheques"
                : "Encontrada no comprovante"}
            </small>
            <strong>{money(comparison.extracted)}</strong>
          </div>
          <div className="comparison-delta">
            <small>Diferença</small>
            <strong>{money(delta)}</strong>
          </div>
        </div>
      )}

      {finding.code === "REQUIRED_DOCUMENTS" &&
        (finding.result === "PASS" ? (
          <ul className="finding-document-list">
            {analysis.requiredDocumentTypes.map((type) => (
              <li key={type}>{documentLabel(type)}</li>
            ))}
          </ul>
        ) : missingDocuments.length > 0 ? (
          <ul className="finding-document-list missing">
            {missingDocuments.map((type) => (
              <li key={type}>{documentLabel(type)}</li>
            ))}
          </ul>
        ) : null)}

      <div className="finding-next-step">
        <strong>Próximo passo</strong>
        <span>{nextStep}</span>
      </div>

      {finding.evidenceRef && financialIncome && (
        <details className="finding-evidence">
          <summary>Referência da evidência</summary>
          <code>{finding.evidenceRef}</code>
        </details>
      )}

      {field?.snippet && finding.code === "INCOME_CONSISTENCY" && (
        <div className="finding-evidence">
          <strong>Origem do valor</strong>
          <span>
            {documentLabel(evidenceDocument?.type ?? "INCOME_PROOF")} · página{" "}
            {field.page}
          </span>
          <blockquote>{field.snippet}</blockquote>
          {finding.evidenceRef && (
            <details>
              <summary>Referência técnica</summary>
              <code>{finding.evidenceRef}</code>
            </details>
          )}
        </div>
      )}
    </article>
  );
}

export default function App() {
  const [username, setUsername] = useState("analyst");
  const [password, setPassword] = useState("");
  const [credentials, setCredentials] = useState<Credentials | null>(null);
  const [name, setName] = useState("");
  const [identifier, setIdentifier] = useState(newIdentifier);
  const [income, setIncome] = useState("");
  const [lookupId, setLookupId] = useState("");
  const [proposal, setProposal] = useState<Proposal | null>(null);
  const [documents, setDocuments] = useState<Document[]>([]);
  const [uploadDrafts, setUploadDrafts] = useState<UploadDraft[]>([]);
  const uploadInput = useRef<HTMLInputElement>(null);
  const [principal, setPrincipal] = useState("");
  const [termMonths, setTermMonths] = useState("36");
  const [monthlyRatePercent, setMonthlyRatePercent] = useState("2");
  const [analysis, setAnalysis] = useState<Analysis | null>(null);
  const [bureauSnapshot, setBureauSnapshot] = useState<BureauSnapshot | null>(
    null,
  );
  const [bureauCase, setBureauCase] = useState("COMPLETE");
  const [decisions, setDecisions] = useState<Decision[]>([]);
  const [outcome, setOutcome] = useState("MORE_INFO_REQUIRED");
  const [reason, setReason] = useState("");
  const [busy, setBusy] = useState("");
  const [notice, setNotice] = useState("");
  const [error, setError] = useState("");

  async function run(label: string, operation: () => Promise<void>) {
    setBusy(label);
    setError("");
    setNotice("");
    try {
      await operation();
    } catch (problem) {
      setError(
        problem instanceof Error ? problem.message : "Falha inesperada.",
      );
    } finally {
      setBusy("");
    }
  }

  function connect(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setCredentials({ username, password });
    setPassword("");
    setNotice(
      "Sessão local pronta. As credenciais ficam apenas na memória desta página.",
    );
  }

  function createProposal(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (!credentials) return;
    void run("create", async () => {
      const created = await api.createProposal(
        credentials,
        name,
        identifier,
        income,
      );
      setProposal(created);
      setLookupId(created.id);
      setDocuments([]);
      setUploadDrafts([]);
      setAnalysis(null);
      setBureauSnapshot(null);
      setDecisions([]);
      setName("");
      setIncome("");
      setIdentifier(newIdentifier());
      setNotice(`Proposta ${created.id} criada.`);
    });
  }

  function loadProposal(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (!credentials) return;
    void run("load", async () => {
      const loaded = await api.getProposal(credentials, lookupId.trim());
      const [loadedDocuments, loadedDecisions, latestBureau] =
        await Promise.all([
          api.listDocuments(credentials, loaded.id),
          api.listDecisions(credentials, loaded.id),
          api.latestBureau(credentials, loaded.id),
        ]);
      setProposal(loaded);
      setDocuments(loadedDocuments);
      setUploadDrafts([]);
      setDecisions(loadedDecisions);
      setBureauSnapshot(latestBureau);
      setAnalysis(null);
      setNotice(
        "Proposta carregada. Informe um ID de análise para recuperar o dossiê anterior.",
      );
    });
  }

  function selectUploadFiles(event: ChangeEvent<HTMLInputElement>) {
    const selected = [...(event.currentTarget.files ?? [])];
    setUploadDrafts(
      selected.map((file) => {
        const detected = inferDocumentMetadata(file.name);
        return {
          id: crypto.randomUUID(),
          file,
          type: detected.type ?? "INCOME_PROOF",
          period: detected.period ?? "",
          detectedType: detected.type,
          detectedPeriod: detected.period,
        };
      }),
    );
    setError("");
    setNotice("");
  }

  function updateUploadDraft(
    id: string,
    update: Partial<Pick<UploadDraft, "type" | "period">>,
  ) {
    setUploadDrafts((current) =>
      current.map((draft) =>
        draft.id === id ? { ...draft, ...update } : draft,
      ),
    );
  }

  function removeUploadDraft(id: string) {
    setUploadDrafts((current) => current.filter((draft) => draft.id !== id));
  }

  function uploadDocuments() {
    if (!credentials || !proposal || uploadDrafts.length === 0) return;
    void run("upload", async () => {
      const validation = validateUploadDrafts(uploadDrafts);
      if ([...validation.values()].some((problems) => problems.length > 0)) {
        throw new Error("Corrija os itens destacados antes de enviar.");
      }
      let uploadedCount = 0;
      try {
        for (const draft of uploadDrafts) {
          await api.upload(
            credentials,
            proposal.id,
            draft.type,
            draft.file,
            draft.type === PAYSLIP || draft.type === BANK_STATEMENT
              ? draft.period
              : undefined,
          );
          uploadedCount += 1;
        }
      } catch (problem) {
        setDocuments(await api.listDocuments(credentials, proposal.id));
        const failedFile = uploadDrafts[uploadedCount]?.file.name;
        throw new Error(
          `Enviamos ${uploadedCount} de ${uploadDrafts.length} arquivos. Não foi possível enviar ${failedFile ?? "o próximo arquivo"}: ${problem instanceof Error ? problem.message : "falha inesperada"}`,
        );
      }
      setDocuments(await api.listDocuments(credentials, proposal.id));
      setAnalysis(null);
      setUploadDrafts([]);
      if (uploadInput.current) uploadInput.current.value = "";
      setNotice(
        `${uploadedCount} documentos recebidos e conferidos. Revise a cobertura antes de analisar.`,
      );
    });
  }

  function analyze() {
    if (!credentials || !proposal) return;
    void run("analysis", async () => {
      const financial = documents.some(
        (item) => item.type === "PAYSLIP" || item.type === "BANK_STATEMENT",
      );
      const result = await api.analyze(
        credentials,
        proposal.id,
        financial
          ? {
              principalRequested: principal ? Number(principal) : null,
              termMonths: Number(termMonths),
              monthlyInterestRate: Number(monthlyRatePercent) / 100,
              currency: "BRL",
              pricingSource: "MANUAL_DEMO",
            }
          : undefined,
      );
      setAnalysis(result);
      const failure = analysisFailureMessage(result);
      setNotice(
        result.status === "COMPLETED"
          ? "Dossiê persistido e pronto para revisão."
          : failure.message,
      );
    });
  }

  function consultBureau() {
    if (!credentials || !proposal) return;
    void run("bureau", async () => {
      await api.consultBureau(credentials, proposal.id, bureauCase);
      setBureauSnapshot(await api.latestBureau(credentials, proposal.id));
      setNotice(
        "Consulta sintética registrada. Execute nova análise para vincular o contexto externo ao dossiê.",
      );
    });
  }

  function loadAnalysis(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (!credentials || !proposal) return;
    const data = new FormData(event.currentTarget);
    const analysisId = String(data.get("analysisId") || "").trim();
    void run("dossier", async () => {
      setAnalysis(await api.getAnalysis(credentials, proposal.id, analysisId));
      setNotice("Dossiê recuperado. Confira o snapshot antes de decidir.");
    });
  }

  function decide(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (!credentials || !proposal || !analysis) return;
    void run("decision", async () => {
      const recorded = await api.decide(
        credentials,
        proposal.id,
        analysis,
        outcome,
        reason,
      );
      setDecisions(await api.listDecisions(credentials, proposal.id));
      setProposal(await api.getProposal(credentials, proposal.id));
      setReason("");
      setNotice(
        `Decisão humana ${recorded.outcome} registrada por ${recorded.analystId}.`,
      );
    });
  }

  const latestDecision = decisions.at(-1);
  const final =
    proposal?.status === "APPROVED" || proposal?.status === "REJECTED";
  const latestDocuments = latestDocumentVersions(documents);
  const financialCoverage = summarizeFinancialCoverage(documents);
  const uploadValidation = validateUploadDrafts(uploadDrafts);
  const uploadHasErrors = [...uploadValidation.values()].some(
    (problems) => problems.length > 0,
  );
  const historicalDocuments = documents.filter(
    (document) => !latestDocuments.some((latest) => latest.id === document.id),
  );
  const hasFinancialDocuments = latestDocuments.some(
    (document) => document.type === PAYSLIP || document.type === BANK_STATEMENT,
  );
  const analysisFailure =
    analysis?.errorCode && analysis.status !== "COMPLETED"
      ? analysisFailureMessage(analysis)
      : null;
  const findingReviewCount =
    analysis?.findings.filter((finding) => finding.result !== "PASS").length ??
    0;
  const findingPassCount =
    analysis?.findings.filter((finding) => finding.result === "PASS").length ??
    0;
  const snapshotCurrent =
    analysis !== null &&
    analysis.documents.length === latestDocuments.length &&
    analysis.documents.every((bound) =>
      latestDocuments.some(
        (item) =>
          item.id === bound.documentId &&
          item.version === bound.version &&
          item.sha256 === bound.sha256,
      ),
    );
  const canDecide =
    analysis?.status === "COMPLETED" &&
    snapshotCurrent &&
    (analysis.financialAssessment === null ||
      analysis.financialAssessment.bureau?.referenceId ===
        (bureauSnapshot?.id ?? null)) &&
    !final &&
    !decisions.some((decision) => decision.analysisId === analysis.analysisId);

  return (
    <div className="app-shell">
      <aside className="sidebar">
        <div className="brand">
          <div className="brand-mark">CI</div>
          <div>
            <strong>Credit Intelligence</strong>
            <small>WORKSPACE / POC</small>
          </div>
        </div>
        <div className="sidebar-section-label">FLUXO DE TRABALHO</div>
        <nav aria-label="Etapas da análise">
          <a href="#proposal">
            <span>01</span> Proposta
          </a>
          <a href="#documents">
            <span>02</span> Documentos
          </a>
          <a href="#analysis">
            <span>03</span> Análise
          </a>
          <a href="#decision">
            <span>04</span> Decisão humana
          </a>
        </nav>
        <div className="sidebar-bottom">
          <span className="live-dot" /> Ambiente local · dados sintéticos
          <br />
          <small>
            {analysis?.aiExplanation.provider
              ? `Explicação: ${providerLabel(analysis.aiExplanation.provider)}`
              : "Explicação auxiliar · revisão humana"}
          </small>
        </div>
      </aside>

      <main className="main-content">
        <header className="topbar">
          <span>MESA DO ANALISTA</span>
          <div>
            {credentials ? (
              <span className="session-pill">● {credentials.username}</span>
            ) : (
              <span className="session-pill muted">Sessão não iniciada</span>
            )}
          </div>
        </header>
        <section className="hero">
          <div className="eyebrow">ANÁLISE DOCUMENTAL · CRÉDITO</div>
          <h1>
            Uma decisão com <em>evidências.</em>
          </h1>
          <p>
            Organize os documentos, confira regras determinísticas e registre a
            avaliação humana. Esta demonstração usa apenas dados sintéticos.
          </p>
          <div className="hero-stats">
            <div>
              <strong>04</strong>
              <span>etapas da jornada</span>
            </div>
            <div>
              <strong>01</strong>
              <span>política versionada</span>
            </div>
            <div>
              <strong>100%</strong>
              <span>decisão humana</span>
            </div>
          </div>
        </section>

        {notice && (
          <div role="status" className="message success">
            {notice}
          </div>
        )}
        {error && (
          <div role="alert" className="message error">
            {error}
          </div>
        )}

        {!credentials ? (
          <section className="panel access-panel">
            <div className="section-heading">
              <span className="step-number">00</span>
              <div>
                <h2>Acesso de demonstração</h2>
                <p>Informe o usuário e a senha definidos no Compose.</p>
              </div>
            </div>
            <form onSubmit={connect} className="form-row">
              <label>
                Usuário
                <input
                  value={username}
                  onChange={(event) => setUsername(event.target.value)}
                  required
                />
              </label>
              <label>
                Senha
                <input
                  type="password"
                  value={password}
                  onChange={(event) => setPassword(event.target.value)}
                  required
                />
              </label>
              <button className="primary-button" type="submit">
                Entrar na mesa →
              </button>
            </form>
            <p className="helper">
              Credenciais guardadas somente na memória desta página; fechar ou
              recarregar encerra a sessão.
            </p>
          </section>
        ) : (
          <>
            <section id="proposal" className="panel">
              <div className="section-heading">
                <span className="step-number">01</span>
                <div>
                  <h2>Proposta</h2>
                  <p>Crie uma proposta fictícia ou retome uma já iniciada.</p>
                </div>
                {proposal && badge(proposal.status)}
              </div>
              <details
                className="proposal-tools"
                open={!proposal}
                key={proposal?.id ?? "new-proposal"}
              >
                <summary>
                  {proposal
                    ? "Criar ou carregar outra proposta"
                    : "Criar ou retomar uma proposta"}
                </summary>
                <div className="two-columns">
                  <form onSubmit={createProposal} className="stack">
                    <h3>Nova proposta de demonstração</h3>
                    <p>Preencha somente com dados fictícios.</p>
                    <label>
                      Nome fictício
                      <input
                        value={name}
                        onChange={(event) => setName(event.target.value)}
                        required
                        maxLength={160}
                        placeholder="Ex.: Alex Demo"
                      />
                    </label>
                    <label>
                      Identificador sintético
                      <input
                        value={identifier}
                        onChange={(event) => setIdentifier(event.target.value)}
                        pattern="DEMO-.*"
                        required
                      />
                    </label>
                    <label>
                      Renda mensal declarada (BRL)
                      <input
                        type="number"
                        min="0"
                        step="0.01"
                        value={income}
                        onChange={(event) => setIncome(event.target.value)}
                        required
                        placeholder="Ex.: 7500.00"
                      />
                    </label>
                    <button
                      disabled={!!busy}
                      className="primary-button"
                      type="submit"
                    >
                      {busy === "create" ? "Criando…" : "Criar proposta →"}
                    </button>
                  </form>
                  <form onSubmit={loadProposal} className="stack load-box">
                    <h3>Retomar proposta</h3>
                    <p>Use o ID salvo em uma sessão anterior.</p>
                    <label>
                      ID da proposta
                      <input
                        value={lookupId}
                        onChange={(event) => setLookupId(event.target.value)}
                        placeholder="UUID da proposta"
                        required
                      />
                    </label>
                    <button
                      disabled={!!busy}
                      className="secondary-button"
                      type="submit"
                    >
                      Carregar proposta
                    </button>
                  </form>
                </div>
              </details>
              {proposal && (
                <div className="summary-strip">
                  <div>
                    <small>SOLICITANTE</small>
                    <strong>{proposal.applicant.name}</strong>
                    <span>{proposal.applicant.syntheticIdentifier}</span>
                  </div>
                  <div>
                    <small>RENDA DECLARADA</small>
                    <strong>{money(proposal.declaredMonthlyIncome)}</strong>
                    <span>mensal · BRL</span>
                  </div>
                  <div>
                    <small>ID DA PROPOSTA</small>
                    <code>{proposal.id}</code>
                    <span>{date(proposal.createdAt)}</span>
                  </div>
                </div>
              )}
            </section>

            {proposal && (
              <section id="documents" className="panel">
                <div className="section-heading">
                  <span className="step-number">02</span>
                  <div>
                    <h2>Documentos</h2>
                    <p>
                      Envie PDF digital sintético com texto selecionável, até 10
                      MiB.
                    </p>
                  </div>
                  {badge(`${latestDocuments.length} DOCUMENTOS ATUAIS`)}
                </div>
                <div className="batch-upload">
                  <div className="upload-intro">
                    <div>
                      <h3>Envie todos os PDFs de uma vez</h3>
                      <p>
                        Identificamos tipo e competência pelo nome do arquivo.
                        Confira a grade antes de enviar.
                      </p>
                    </div>
                    <label className="upload-picker">
                      <span>Selecionar PDFs</span>
                      <input
                        ref={uploadInput}
                        type="file"
                        accept="application/pdf,.pdf"
                        multiple
                        aria-label="PDFs da proposta"
                        onChange={selectUploadFiles}
                        disabled={final || !!busy}
                      />
                    </label>
                  </div>
                  {uploadDrafts.length > 0 && (
                    <div className="upload-review">
                      <div className="review-heading">
                        <div>
                          <strong>
                            Revise {uploadDrafts.length} arquivo
                            {uploadDrafts.length === 1 ? "" : "s"}
                          </strong>
                          <small>
                            Nada será enviado enquanto houver item destacado.
                          </small>
                        </div>
                        <button
                          type="button"
                          className="text-button"
                          onClick={() => {
                            setUploadDrafts([]);
                            if (uploadInput.current)
                              uploadInput.current.value = "";
                          }}
                        >
                          Limpar seleção
                        </button>
                      </div>
                      <div className="upload-queue">
                        {uploadDrafts.map((draft) => {
                          const problems = uploadValidation.get(draft.id) ?? [];
                          const financial =
                            draft.type === PAYSLIP ||
                            draft.type === BANK_STATEMENT;
                          return (
                            <div
                              className={`upload-item ${problems.length > 0 ? "has-error" : ""}`}
                              key={draft.id}
                            >
                              <div className="upload-file-name">
                                <span className="doc-icon">PDF</span>
                                <div>
                                  <strong>{draft.file.name}</strong>
                                  <small>
                                    {(draft.file.size / 1024).toFixed(1)} KiB
                                  </small>
                                </div>
                              </div>
                              <label>
                                Tipo
                                <select
                                  value={draft.type}
                                  onChange={(event) =>
                                    updateUploadDraft(draft.id, {
                                      type: event.target.value,
                                    })
                                  }
                                >
                                  <option value="INCOME_PROOF">
                                    Comprovante de renda
                                  </option>
                                  <option value={PAYSLIP}>Contracheque</option>
                                  <option value={BANK_STATEMENT}>
                                    Extrato bancário
                                  </option>
                                  <option value="IDENTIFICATION">
                                    Identificação
                                  </option>
                                </select>
                              </label>
                              <label>
                                Competência
                                <input
                                  type="month"
                                  value={draft.period}
                                  onChange={(event) =>
                                    updateUploadDraft(draft.id, {
                                      period: event.target.value,
                                    })
                                  }
                                  disabled={!financial}
                                  required={financial}
                                />
                              </label>
                              <button
                                type="button"
                                className="remove-button"
                                aria-label={`Remover ${draft.file.name}`}
                                onClick={() => removeUploadDraft(draft.id)}
                              >
                                Remover
                              </button>
                              {problems.length > 0 ? (
                                <ul className="upload-errors">
                                  {problems.map((problem) => (
                                    <li key={problem}>{problem}</li>
                                  ))}
                                </ul>
                              ) : (
                                <div className="upload-ok">
                                  Pronto para enviar
                                </div>
                              )}
                            </div>
                          );
                        })}
                      </div>
                      <button
                        type="button"
                        className="primary-button batch-submit"
                        onClick={uploadDocuments}
                        disabled={!!busy || final || uploadHasErrors}
                      >
                        {busy === "upload"
                          ? "Enviando e conferindo…"
                          : `Enviar ${uploadDrafts.length} documento${uploadDrafts.length === 1 ? "" : "s"}`}
                      </button>
                    </div>
                  )}
                </div>

                {hasFinancialDocuments && (
                  <div
                    className={`readiness-card ${financialCoverage.complete ? "is-ready" : "is-partial"}`}
                  >
                    <div className="readiness-heading">
                      <div>
                        <span className="eyebrow">COBERTURA DOCUMENTAL</span>
                        <h3>
                          {financialCoverage.complete
                            ? "Pronto para análise completa"
                            : "A análise ainda será parcial"}
                        </h3>
                      </div>
                      <span className="readiness-pill">
                        {financialCoverage.complete ? "Completo" : "Incompleto"}
                      </span>
                    </div>
                    <div className="coverage-grid">
                      <div>
                        <strong>
                          {Math.min(financialCoverage.payslipPeriods.length, 3)}
                          /3
                        </strong>
                        <span>Contracheques</span>
                        <small>
                          {formatPeriods(financialCoverage.payslipPeriods)}
                        </small>
                      </div>
                      <div>
                        <strong>
                          {Math.min(
                            financialCoverage.statementPeriods.length,
                            3,
                          )}
                          /3
                        </strong>
                        <span>Extratos</span>
                        <small>
                          {formatPeriods(financialCoverage.statementPeriods)}
                        </small>
                      </div>
                      <div>
                        <strong>
                          {Math.min(financialCoverage.matchedPeriods.length, 3)}
                          /3
                        </strong>
                        <span>Meses conciliáveis</span>
                        <small>
                          {formatPeriods(financialCoverage.matchedPeriods)}
                        </small>
                      </div>
                    </div>
                    {!financialCoverage.complete && (
                      <p>
                        Você ainda pode executar uma análise parcial, mas renda,
                        conciliação e capacidade poderão ficar como não
                        avaliadas.
                      </p>
                    )}
                  </div>
                )}

                {latestDocuments.length > 0 && (
                  <div className="document-list">
                    <div className="list-heading">
                      <strong>Documentos atuais</strong>
                      <small>Uma linha por tipo e competência</small>
                    </div>
                    {latestDocuments.map((document) => (
                      <div className="document-item" key={document.id}>
                        <div className="doc-icon">PDF</div>
                        <div>
                          <strong>
                            {documentLabel(document.type)}
                            {document.period ? ` · ${document.period}` : ""}
                          </strong>
                          <small>
                            v{document.version} · {date(document.uploadedAt)} ·
                            SHA-256 {document.sha256.slice(0, 12)}…
                          </small>
                        </div>
                        {badge(document.status)}
                      </div>
                    ))}
                    {historicalDocuments.length > 0 && (
                      <details className="history-details">
                        <summary>
                          Ver {historicalDocuments.length} versão
                          {historicalDocuments.length === 1
                            ? " anterior"
                            : "ões anteriores"}
                        </summary>
                        {historicalDocuments.map((document) => (
                          <div
                            className="document-item historical"
                            key={document.id}
                          >
                            <div className="doc-icon">PDF</div>
                            <div>
                              <strong>
                                {documentLabel(document.type)}
                                {document.period ? ` · ${document.period}` : ""}
                              </strong>
                              <small>
                                v{document.version} ·{" "}
                                {date(document.uploadedAt)} · SHA-256{" "}
                                {document.sha256.slice(0, 12)}…
                              </small>
                            </div>
                          </div>
                        ))}
                      </details>
                    )}
                  </div>
                )}
              </section>
            )}

            {proposal && (
              <section id="analysis" className="panel">
                <div className="section-heading">
                  <span className="step-number">03</span>
                  <div>
                    <h2>Análise e dossiê</h2>
                    <p>
                      Extração Python; regras e política aplicadas pelo Java.
                    </p>
                  </div>
                  {analysis && badge(analysis.status)}
                </div>
                <div
                  className={`analysis-start ${financialCoverage.complete ? "is-ready" : "is-partial"}`}
                >
                  <div>
                    <span className="eyebrow">ANTES DE ANALISAR</span>
                    <h3>
                      {financialCoverage.complete
                        ? "Conjunto completo e conciliável"
                        : documents.length > 0
                          ? "Resultado parcial esperado"
                          : "Envie pelo menos um documento"}
                    </h3>
                    <p>
                      {financialCoverage.complete
                        ? `Três competências conferidas: ${formatPeriods(financialCoverage.matchedPeriods.slice(0, 3))}.`
                        : documents.length > 0
                          ? "O sistema preservará campos ausentes como não avaliados; nenhuma informação será inventada."
                          : "A análise fica disponível depois que a proposta recebe documentos."}
                    </p>
                  </div>
                  <button
                    disabled={
                      !!busy ||
                      final ||
                      documents.length === 0 ||
                      uploadDrafts.length > 0
                    }
                    onClick={analyze}
                    className="primary-button"
                  >
                    {busy === "analysis"
                      ? "Analisando…"
                      : financialCoverage.complete
                        ? "Analisar documentos →"
                        : "Executar análise parcial →"}
                  </button>
                </div>

                <details className="advanced-options">
                  <summary>Opções avançadas e dossiês anteriores</summary>
                  <div className="advanced-content">
                    <form onSubmit={loadAnalysis} className="inline-form">
                      <input
                        name="analysisId"
                        placeholder="ID de análise anterior"
                        aria-label="ID de análise anterior"
                        required
                      />
                      <button disabled={!!busy} className="secondary-button">
                        Abrir dossiê
                      </button>
                    </form>
                    {hasFinancialDocuments && (
                      <>
                        <div className="action-row">
                          <label>
                            Caso sintético de bureau
                            <select
                              value={bureauCase}
                              onChange={(event) =>
                                setBureauCase(event.target.value)
                              }
                              disabled={!!busy || final}
                            >
                              <option value="COMPLETE">Completo</option>
                              <option value="CLEAN">
                                Sem apontamentos no cenário
                              </option>
                              <option value="UNAVAILABLE">Indisponível</option>
                            </select>
                          </label>
                          <button
                            type="button"
                            className="secondary-button"
                            onClick={consultBureau}
                            disabled={!!busy || final}
                          >
                            {busy === "bureau"
                              ? "Consultando…"
                              : "Consultar bureau sintético"}
                          </button>
                        </div>
                        <small>
                          Modo local por padrão. A integração homologada só é
                          usada quando configurada no Compose.
                        </small>
                        <div className="form-row">
                          <label>
                            Valor solicitado (BRL, opcional)
                            <input
                              type="number"
                              min="0"
                              step="0.01"
                              value={principal}
                              onChange={(event) =>
                                setPrincipal(event.target.value)
                              }
                            />
                          </label>
                          <label>
                            Prazo em meses
                            <input
                              type="number"
                              min="1"
                              max="600"
                              value={termMonths}
                              onChange={(event) =>
                                setTermMonths(event.target.value)
                              }
                            />
                          </label>
                          <label>
                            Taxa mensal fictícia (%)
                            <input
                              type="number"
                              min="0"
                              max="100"
                              step="0.01"
                              value={monthlyRatePercent}
                              onChange={(event) =>
                                setMonthlyRatePercent(event.target.value)
                              }
                            />
                          </label>
                        </div>
                      </>
                    )}
                  </div>
                </details>
                {analysis && (
                  <div className="dossier">
                    <details className="execution-details">
                      <summary>Detalhes da execução e rastreabilidade</summary>
                      <div className="dossier-meta">
                        <div>
                          <small>ANÁLISE</small>
                          <code>{analysis.analysisId}</code>
                        </div>
                        <div>
                          <small>POLÍTICA</small>
                          <strong>{analysis.policyVersion}</strong>
                        </div>
                        <div>
                          <small>TENTATIVA</small>
                          <strong>{analysis.attempt}</strong>
                        </div>
                        <div>
                          <small>SNAPSHOT</small>
                          <code>
                            {analysis.documentSnapshotHash.slice(0, 14)}…
                          </code>
                        </div>
                      </div>
                    </details>
                    {analysisFailure && (
                      <div className="analysis-failure" role="alert">
                        <div>
                          <strong>{analysisFailure.title}</strong>
                          <p>{analysisFailure.message}</p>
                        </div>
                        <details>
                          <summary>Detalhes técnicos</summary>
                          <code>
                            {analysis.status} · {analysis.errorCode}
                          </code>
                        </details>
                      </div>
                    )}
                    {analysis.financialAssessment && (
                      <div className="panel">
                        <span className="eyebrow">
                          PRÉ-ANÁLISE FINANCEIRA · REGRAS JAVA
                        </span>
                        <h3>Perfil e simulação documental</h3>
                        <p>
                          {badge(analysis.financialAssessment.reviewStatus)} ·
                          Política {analysis.financialAssessment.policyVersion}
                        </p>
                        <div className="dossier-meta">
                          <div>
                            <small>RENDA LÍQUIDA MÉDIA</small>
                            <strong>
                              {analysis.financialAssessment.averageNetIncome ===
                              null
                                ? "Não verificada"
                                : money(
                                    analysis.financialAssessment
                                      .averageNetIncome,
                                  )}
                            </strong>
                          </div>
                          <div>
                            <small>MESES VÁLIDOS</small>
                            <strong>
                              {analysis.financialAssessment.validIncomePeriods}
                              /3
                            </strong>
                          </div>
                          <div>
                            <small>PARCELAS ADICIONAIS</small>
                            <strong>
                              {money(
                                analysis.financialAssessment
                                  .additionalMonthlyPayments,
                              )}
                            </strong>
                          </div>
                          <div>
                            <small>NOVA PRESTAÇÃO TEÓRICA</small>
                            <strong>
                              {analysis.financialAssessment
                                .maxNewInstallment === null
                                ? "Não avaliada"
                                : money(
                                    analysis.financialAssessment
                                      .maxNewInstallment,
                                  )}
                            </strong>
                          </div>
                        </div>
                        <h4>Conciliação salarial por mês</h4>
                        <ul>
                          {analysis.financialAssessment.salaryMatches.map(
                            (match) => (
                              <li key={match.period}>
                                {match.period}: folha {money(match.payslipNet)}{" "}
                                · créditos elegíveis {money(match.bankCredit)} ·
                                diferença {money(match.difference)} ·{" "}
                                {badge(match.status)}
                                <details>
                                  <summary>
                                    Evidências de folha e extrato
                                  </summary>
                                  {match.evidenceRefs.map((ref) => (
                                    <code key={ref}>
                                      {ref}
                                      <br />
                                    </code>
                                  ))}
                                </details>
                              </li>
                            ),
                          )}
                        </ul>
                        <p>
                          Créditos sem classificação salarial:{" "}
                          {analysis.financialAssessment.unknownCredits}.
                          Descontos em folha já refletidos no líquido não são
                          subtraídos novamente.
                        </p>
                        {analysis.financialAssessment.simulation ? (
                          <div className="evidence-card">
                            <strong>Simulação Price parcial</strong>
                            <p>
                              Prazo:{" "}
                              {
                                analysis.financialAssessment.simulation
                                  .termMonths
                              }{" "}
                              meses · taxa mensal:{" "}
                              {percent(
                                analysis.financialAssessment.simulation
                                  .monthlyInterestRate * 100,
                              )}
                              .
                            </p>
                            <p>
                              Principal teórico na capacidade calculada:{" "}
                              {money(
                                analysis.financialAssessment.simulation
                                  .estimatedPrincipalAtCapacity,
                              )}
                              .
                            </p>
                            {analysis.financialAssessment.simulation
                              .requestedPayment !== null && (
                              <p>
                                Prestação do valor solicitado:{" "}
                                {money(
                                  analysis.financialAssessment.simulation
                                    .requestedPayment,
                                )}
                                .
                              </p>
                            )}
                            <small>
                              Sem CET, IOF, seguros ou tarifas. Não é oferta
                              bancária.
                            </small>
                          </div>
                        ) : (
                          <p>
                            Simulação indisponível enquanto renda ou obrigações
                            estiverem incertas.
                          </p>
                        )}
                        <div className="evidence-card">
                          <strong>Contexto externo</strong>
                          <p>
                            {analysis.financialAssessment.bureau?.provider ??
                              "Sem provedor"}{" "}
                            ·{" "}
                            {analysis.financialAssessment.bureau?.environment ??
                              "MOCK"}{" "}
                            ·{" "}
                            {analysis.financialAssessment.bureau?.status ??
                              "NOT_REQUESTED"}
                          </p>
                          {analysis.financialAssessment.bureau?.score && (
                            <p>
                              Score informado:{" "}
                              {analysis.financialAssessment.bureau.score.value}{" "}
                              ({analysis.financialAssessment.bureau.score.model}
                              ).
                            </p>
                          )}
                          <small>
                            {analysis.financialAssessment.bureau
                              ?.environment === "SANDBOX"
                              ? "Resposta de homologação estática. Restrições permanecem desconhecidas quando o produto não fornece contagem canônica."
                              : "Contexto sintético local ou consulta não solicitada; não representa dados reais."}
                          </small>
                        </div>
                        <h4>Limitações</h4>
                        <ul>
                          {analysis.financialAssessment.limitations.map(
                            (item) => (
                              <li key={item}>
                                {limitationLabels[item] ?? item}
                              </li>
                            ),
                          )}
                        </ul>
                      </div>
                    )}
                    <div className="result-sections">
                      <details className="result-details">
                        <summary>
                          <span>Fatos extraídos</span>
                          <strong>
                            {analysis.extractedFields.length} campos
                          </strong>
                        </summary>
                        <div className="result-details-content">
                          {analysis.extractedFields.length ? (
                            analysis.extractedFields.map((field) => (
                              <div
                                className="evidence-card"
                                key={`${field.documentId}-${field.fieldName}`}
                              >
                                <div className="card-head">
                                  <strong>
                                    {fieldLabels[field.fieldName] ??
                                      field.fieldName}
                                  </strong>
                                  {badge(field.extractionStatus)}
                                </div>
                                <div className="fact-value">
                                  {field.value
                                    ? field.currency === "BRL"
                                      ? money(Number(field.value))
                                      : field.value
                                    : "Sem valor factual"}
                                </div>
                                {field.evidenceRef ? (
                                  <div className="fact-source">
                                    <small>
                                      Página {field.page} ·{" "}
                                      {documentLabel(
                                        analysis.documents.find(
                                          (document) =>
                                            document.documentId ===
                                            field.documentId,
                                        )?.type ?? "INCOME_PROOF",
                                      )}
                                    </small>
                                    <details>
                                      <summary>Referência técnica</summary>
                                      <code>{field.evidenceRef}</code>
                                    </details>
                                  </div>
                                ) : (
                                  <small>Sem evidência validada</small>
                                )}
                                {field.snippet && (
                                  <blockquote>{field.snippet}</blockquote>
                                )}
                              </div>
                            ))
                          ) : (
                            <p className="empty-state">
                              Nenhum campo extraído neste snapshot.
                            </p>
                          )}
                        </div>
                      </details>
                      <details
                        className="result-details"
                        open={findingReviewCount > 0}
                      >
                        <summary>
                          <span>Verificações da análise</span>
                          <strong>
                            {findingPassCount} conformes
                            {findingReviewCount > 0
                              ? ` · ${findingReviewCount} para revisar`
                              : ""}
                          </strong>
                        </summary>
                        <div className="result-details-content">
                          <p className="finding-intro">
                            Confira o motivo, os valores e a evidência de cada
                            item. Estes sinais apoiam sua revisão; a decisão
                            continua humana.
                          </p>
                          {analysis.findings.map((finding) => (
                            <FindingCard
                              key={finding.code}
                              finding={finding}
                              analysis={analysis}
                            />
                          ))}
                        </div>
                      </details>
                    </div>
                    <div className="ai-card">
                      <div className="card-head">
                        <div>
                          <span className="eyebrow">
                            INTERPRETAÇÃO AUXILIAR
                          </span>
                          <h3>Explicação de IA</h3>
                        </div>
                        {badge(analysis.aiExplanationStatus)}
                      </div>
                      {analysis.aiExplanationStatus === "AVAILABLE" ? (
                        <>
                          <p>{analysis.aiExplanation.summary}</p>
                          <div className="ai-note">
                            Provedor: {analysis.aiExplanation.provider} · Saída
                            demonstrativa. Fatos, regras e decisão permanecem
                            sob controle humano.
                          </div>
                          {analysis.aiExplanation.limitations.map((item) => (
                            <small key={item}>
                              •{" "}
                              {item.replaceAll(
                                "findings",
                                "resultados das regras",
                              )}
                            </small>
                          ))}
                        </>
                      ) : (
                        <p>
                          Explicação indisponível ou inválida. Os fatos e
                          findings continuam consultáveis.
                        </p>
                      )}
                    </div>
                  </div>
                )}
              </section>
            )}

            {proposal && (
              <section id="decision" className="panel">
                <div className="section-heading">
                  <span className="step-number">04</span>
                  <div>
                    <h2>Decisão humana</h2>
                    <p>
                      O analista registra o resultado para um snapshot
                      explícito.
                    </p>
                  </div>
                  {latestDecision && badge(latestDecision.outcome)}
                </div>
                {latestDecision && (
                  <div className="decision-record">
                    <span>ÚLTIMO REGISTRO</span>
                    <strong>
                      {latestDecision.outcome.replaceAll("_", " ")}
                    </strong>
                    <p>{latestDecision.reason}</p>
                    <small>
                      {latestDecision.analystId} ·{" "}
                      {date(latestDecision.createdAt)} · análise{" "}
                      {latestDecision.analysisId}
                    </small>
                  </div>
                )}
                <form onSubmit={decide} className="decision-form">
                  <label>
                    Resultado
                    <select
                      value={outcome}
                      onChange={(event) => setOutcome(event.target.value)}
                      disabled={!canDecide}
                    >
                      <option value="MORE_INFO_REQUIRED">
                        Solicitar mais informações
                      </option>
                      <option value="APPROVED">Aprovar</option>
                      <option value="REJECTED">Rejeitar</option>
                    </select>
                  </label>
                  <label>
                    Justificativa
                    <textarea
                      value={reason}
                      onChange={(event) => setReason(event.target.value)}
                      placeholder="Registre a avaliação humana e a base da decisão…"
                      minLength={10}
                      maxLength={2000}
                      required
                      disabled={!canDecide}
                    />
                  </label>
                  <button
                    disabled={!!busy || !canDecide}
                    className="primary-button"
                    type="submit"
                  >
                    {busy === "decision" ? "Registrando…" : "Registrar decisão"}
                  </button>
                </form>
                {!canDecide && (
                  <p className="helper">
                    Para decidir, abra uma análise concluída e atual, sem
                    decisão já registrada. Uma decisão final encerra a proposta.
                  </p>
                )}
              </section>
            )}
          </>
        )}
        <footer>
          Credit Intelligence · Prova de conceito · Dados exclusivamente
          sintéticos · Decisão sempre humana
        </footer>
      </main>
    </div>
  );
}
