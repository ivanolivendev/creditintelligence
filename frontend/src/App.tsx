import { useState, type FormEvent } from "react";
import {
  api,
  type Analysis,
  type BureauSnapshot,
  type Credentials,
  type Decision,
  type Document,
  type Proposal,
} from "./api";

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
  const [file, setFile] = useState<File | null>(null);
  const [documentType, setDocumentType] = useState("INCOME_PROOF");
  const [period, setPeriod] = useState("2026-08");
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
      setDecisions(loadedDecisions);
      setBureauSnapshot(latestBureau);
      setAnalysis(null);
      setNotice(
        "Proposta carregada. Informe um ID de análise para recuperar o dossiê anterior.",
      );
    });
  }

  function upload(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (!credentials || !proposal || !file) return;
    void run("upload", async () => {
      const uploaded = await api.upload(
        credentials,
        proposal.id,
        documentType,
        file,
        documentType === "PAYSLIP" || documentType === "BANK_STATEMENT"
          ? period
          : undefined,
      );
      setDocuments(await api.listDocuments(credentials, proposal.id));
      setAnalysis(null);
      setFile(null);
      setNotice(
        `Documento v${uploaded.version} recebido. Execute nova análise para este snapshot.`,
      );
      const input = document.getElementById(
        "document-file",
      ) as HTMLInputElement | null;
      if (input) input.value = "";
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
      setNotice(
        result.status === "COMPLETED"
          ? "Dossiê persistido e pronto para revisão."
          : `Análise em ${result.status}. Consulte o estado antes de decidir.`,
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
  const latestDocuments = [
    ...new Map(
      documents.map((item) => [
        `${item.type}:${item.period ?? ""}`,
        documents
          .filter(
            (candidate) =>
              candidate.type === item.type && candidate.period === item.period,
          )
          .at(-1)!,
      ]),
    ).values(),
  ];
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
                  {badge(`${documents.length} VERSÕES`)}
                </div>
                <form onSubmit={upload} className="upload-row">
                  <label>
                    Tipo
                    <select
                      value={documentType}
                      onChange={(event) => setDocumentType(event.target.value)}
                      disabled={final}
                    >
                      <option value="INCOME_PROOF">Comprovante de renda</option>
                      <option value="PAYSLIP">Contracheque</option>
                      <option value="BANK_STATEMENT">Extrato bancário</option>
                      <option value="IDENTIFICATION">Identificação</option>
                    </select>
                  </label>
                  {(documentType === "PAYSLIP" ||
                    documentType === "BANK_STATEMENT") && (
                    <label>
                      Competência / mês do extrato
                      <input
                        type="month"
                        value={period}
                        onChange={(event) => setPeriod(event.target.value)}
                        required
                      />
                    </label>
                  )}
                  <label className="file-label">
                    PDF
                    <input
                      id="document-file"
                      type="file"
                      accept="application/pdf,.pdf"
                      onChange={(event) =>
                        setFile(event.target.files?.[0] || null)
                      }
                      disabled={final}
                      required
                    />
                  </label>
                  <button
                    disabled={!!busy || final || !file}
                    className="primary-button"
                    type="submit"
                  >
                    {busy === "upload" ? "Enviando…" : "Enviar documento"}
                  </button>
                </form>
                {documents.length > 0 && (
                  <div className="document-list">
                    {documents.map((document) => (
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
                <div className="action-row">
                  <button
                    disabled={!!busy || final}
                    onClick={analyze}
                    className="primary-button"
                  >
                    {busy === "analysis" ? "Analisando…" : "Executar análise →"}
                  </button>
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
                </div>
                {documents.some(
                  (item) =>
                    item.type === "PAYSLIP" || item.type === "BANK_STATEMENT",
                ) && (
                  <div className="action-row">
                    <label>
                      Caso sintético de bureau
                      <select
                        value={bureauCase}
                        onChange={(event) => setBureauCase(event.target.value)}
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
                    <small>
                      Modo local por padrão. A integração homologada só é usada
                      quando configurada no Compose.
                    </small>
                  </div>
                )}
                {documents.some(
                  (item) =>
                    item.type === "PAYSLIP" || item.type === "BANK_STATEMENT",
                ) && (
                  <div className="form-row">
                    <label>
                      Valor solicitado (BRL, opcional)
                      <input
                        type="number"
                        min="0"
                        step="0.01"
                        value={principal}
                        onChange={(event) => setPrincipal(event.target.value)}
                      />
                    </label>
                    <label>
                      Prazo em meses
                      <input
                        type="number"
                        min="1"
                        max="600"
                        value={termMonths}
                        onChange={(event) => setTermMonths(event.target.value)}
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
                )}
                {analysis && (
                  <div className="dossier">
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
                    {analysis.errorCode && (
                      <div className="message error">
                        Falha: {analysis.errorCode}. Uma falha recuperável pode
                        ser reprocessada.
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
                              <li key={item}>{item}</li>
                            ),
                          )}
                        </ul>
                      </div>
                    )}
                    <div className="dossier-grid">
                      <div>
                        <h3>Fatos extraídos</h3>
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
                      <div>
                        <h3>O que encontramos</h3>
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
