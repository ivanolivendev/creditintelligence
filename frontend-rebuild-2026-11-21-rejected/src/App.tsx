import { useMemo, useState, type ChangeEvent, type FormEvent } from "react";
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
  inferDocumentMetadata,
  PAYSLIP,
  summarizeFinancialCoverage,
  validateUploadDrafts,
  type UploadDraft,
} from "./document-workflow";

const date = (value: string) => new Date(value).toLocaleDateString("pt-BR");
const label: Record<string, string> = {
  DRAFT: "Rascunho",
  READY_FOR_ANALYSIS: "Pronta",
  RUNNING: "Em análise",
  COMPLETED: "Concluída",
  PENDING_HUMAN_REVIEW: "Revisão humana",
  APPROVED: "Aprovada",
  REJECTED: "Rejeitada",
  MORE_INFO_REQUIRED: "Mais informações",
  PASS: "Consistente",
  FAIL: "Divergência",
  MISSING: "Ausente",
  AVAILABLE: "Disponível",
  UNAVAILABLE: "Indisponível",
};
const human = (value: string | null | undefined) =>
  value ? (label[value] ?? value.replaceAll("_", " ")) : "—";

function Status({ value }: { value: string }) {
  return (
    <span className={`status status-${value.toLowerCase()}`}>
      {human(value)}
    </span>
  );
}

export default function App() {
  const [credentials, setCredentials] = useState<Credentials | null>(null);
  const [username, setUsername] = useState("analyst");
  const [password, setPassword] = useState("");
  const [proposal, setProposal] = useState<Proposal | null>(null);
  const [documents, setDocuments] = useState<Document[]>([]);
  const [analysis, setAnalysis] = useState<Analysis | null>(null);
  const [bureau, setBureau] = useState<BureauSnapshot | null>(null);
  const [decisions, setDecisions] = useState<Decision[]>([]);
  const [drafts, setDrafts] = useState<UploadDraft[]>([]);
  const [notice, setNotice] = useState("");
  const [error, setError] = useState("");
  const [busy, setBusy] = useState("");
  const [name, setName] = useState("");
  const [identifier, setIdentifier] = useState("DEMO-ANALYST");
  const [income, setIncome] = useState("");
  const [lookup, setLookup] = useState("");
  const [reason, setReason] = useState("");
  const [outcome, setOutcome] = useState("MORE_INFO_REQUIRED");

  const coverage = useMemo(
    () => summarizeFinancialCoverage(documents),
    [documents],
  );
  const validation = useMemo(() => validateUploadDrafts(drafts), [drafts]);
  const latest = decisions.at(-1);
  const canAnalyze = coverage.complete && !!bureau?.report && !busy;

  const run = async (name: string, operation: () => Promise<void>) => {
    setBusy(name);
    setError("");
    setNotice("");
    try {
      await operation();
    } catch (cause) {
      setError(
        cause instanceof Error
          ? cause.message
          : "Não foi possível concluir a operação.",
      );
    } finally {
      setBusy("");
    }
  };
  const refresh = async (loaded: Proposal) => {
    if (!credentials) return;
    const [docs, decs, latestBureau] = await Promise.all([
      api.listDocuments(credentials, loaded.id),
      api.listDecisions(credentials, loaded.id),
      api.latestBureau(credentials, loaded.id).catch(() => null),
    ]);
    setProposal(loaded);
    setDocuments(docs);
    setDecisions(decs);
    setBureau(latestBureau);
  };
  const connect = (event: FormEvent) => {
    event.preventDefault();
    setCredentials({ username, password });
    setNotice(
      "Sessão de demonstração pronta. Os dados permanecem locais e sintéticos.",
    );
  };
  const create = (event: FormEvent) => {
    event.preventDefault();
    if (!credentials) return;
    void run("proposal", async () => {
      const created = await api.createProposal(
        credentials,
        name,
        identifier,
        income,
      );
      await refresh(created);
      setNotice(
        "Proposta criada. Próximo passo: reunir as versões documentais.",
      );
    });
  };
  const load = (event: FormEvent) => {
    event.preventDefault();
    if (!credentials) return;
    void run("load", async () => {
      await refresh(await api.getProposal(credentials, lookup.trim()));
      setNotice("Dossiê carregado.");
    });
  };
  const choose = (event: ChangeEvent<HTMLInputElement>) => {
    const files = [...(event.target.files ?? [])];
    setDrafts(
      files.map((file) => {
        const detected = inferDocumentMetadata(file.name);
        return {
          id: crypto.randomUUID(),
          file,
          type: detected.type ?? PAYSLIP,
          period: detected.period ?? "",
          detectedType: detected.type,
          detectedPeriod: detected.period,
        };
      }),
    );
  };
  const upload = () => {
    if (!credentials || !proposal) return;
    const invalid = [...validation.values()].some((items) => items.length);
    if (invalid) {
      setError("Corrija os arquivos sinalizados antes do envio.");
      return;
    }
    void run("upload", async () => {
      for (const draft of drafts)
        await api.upload(
          credentials,
          proposal.id,
          draft.type,
          draft.file,
          draft.period,
        );
      setDocuments(await api.listDocuments(credentials, proposal.id));
      setDrafts([]);
      setNotice("Documentos versionados e preservados.");
    });
  };
  const analyze = () => {
    if (!credentials || !proposal) return;
    void run("analysis", async () => {
      const result = await api.analyze(credentials, proposal.id);
      setAnalysis(result);
      setNotice(
        result.status === "COMPLETED"
          ? "Análise concluída. O dossiê está pronto para revisão humana."
          : "Análise iniciada; atualize o dossiê quando o processamento terminar.",
      );
    });
  };
  const consult = () => {
    if (!credentials || !proposal) return;
    void run("bureau", async () => {
      await api.consultBureau(credentials, proposal.id, "COMPLETE");
      setBureau(await api.latestBureau(credentials, proposal.id));
      setNotice("Consulta de bureau de demonstração registrada.");
    });
  };
  const decide = (event: FormEvent) => {
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
      setDecisions([...decisions, recorded]);
      await refresh(await api.getProposal(credentials, proposal.id));
      setNotice("Decisão humana registrada com trilha de auditoria.");
    });
  };

  return (
    <div className="app-shell">
      <aside className="sidebar">
        <div className="brand">
          <span className="brand-symbol">CI</span>
          <span>
            <strong>Credit</strong> Intelligence
          </span>
        </div>
        <p className="side-kicker">WORKSPACE DE ANÁLISE</p>
        <nav>
          <a href="#overview" className="active">
            <span>01</span> Visão geral
          </a>
          <a href="#documents">
            <span>02</span> Documentos
          </a>
          <a href="#analysis">
            <span>03</span> Dossiê
          </a>
          <a href="#decision">
            <span>04</span> Decisão
          </a>
        </nav>
        <div className="side-footer">
          <span className="pulse" /> Ambiente local
          <br />
          <small>Dados sintéticos · somente demonstração</small>
        </div>
      </aside>
      <main className="content">
        <header className="topbar">
          <div>
            <span className="breadcrumb">CREDIT INTELLIGENCE / </span>
            <span>
              {proposal ? `PROPOSTA ${proposal.id.slice(0, 8)}` : "NOVA SESSÃO"}
            </span>
          </div>
          <div className="user-chip">
            {credentials ? `● ${credentials.username}` : "Sessão não iniciada"}
          </div>
        </header>
        <section id="overview" className="hero">
          <div>
            <p className="eyebrow">ANÁLISE DOCUMENTAL · CRÉDITO</p>
            <h1>
              Clareza para cada
              <br />
              <em>decisão humana.</em>
            </h1>
            <p className="hero-copy">
              Um espaço de trabalho para reunir evidências, validar regras e
              revisar propostas com rastreabilidade.
            </p>
          </div>
          <div className="hero-orbit" aria-hidden="true">
            <span />
            <span />
            <span />
          </div>
        </section>
        {(notice || error) && (
          <div
            className={`banner ${error ? "banner-error" : "banner-success"}`}
            role={error ? "alert" : "status"}
          >
            {error || notice}
          </div>
        )}
        <section className="workspace-grid">
          <article className="card session-card">
            <div className="card-heading">
              <div>
                <p className="eyebrow">ACESSO</p>
                <h2>Conectar ambiente</h2>
              </div>
              <span className="step">00</span>
            </div>
            <form onSubmit={connect} className="form-grid">
              <label>
                Usuário
                <input
                  value={username}
                  onChange={(e) => setUsername(e.target.value)}
                />
              </label>
              <label>
                Senha
                <input
                  type="password"
                  value={password}
                  onChange={(e) => setPassword(e.target.value)}
                />
              </label>
              <button className="button primary" disabled={!!busy}>
                Entrar no workspace <span>↗</span>
              </button>
            </form>
            <p className="muted">
              HTTP Basic de demonstração · credenciais nunca são persistidas.
            </p>
          </article>
          <article className="card proposal-card">
            <div className="card-heading">
              <div>
                <p className="eyebrow">PROPOSTA</p>
                <h2>Começar uma análise</h2>
              </div>
              <span className="step">01</span>
            </div>
            <form onSubmit={create} className="form-grid">
              <label>
                Nome sintético
                <input
                  required
                  value={name}
                  onChange={(e) => setName(e.target.value)}
                  placeholder="Ex.: Marina Costa"
                />
              </label>
              <label>
                Identificador
                <input
                  required
                  value={identifier}
                  onChange={(e) => setIdentifier(e.target.value)}
                />
              </label>
              <label>
                Renda mensal declarada
                <input
                  required
                  inputMode="decimal"
                  value={income}
                  onChange={(e) => setIncome(e.target.value)}
                  placeholder="8000"
                />
              </label>
              <button
                className="button primary"
                disabled={!credentials || !!busy}
              >
                Criar proposta <span>↗</span>
              </button>
            </form>
            <form onSubmit={load} className="load-row">
              <input
                value={lookup}
                onChange={(e) => setLookup(e.target.value)}
                placeholder="Ou carregar pelo ID da proposta"
              />
              <button className="button subtle">Carregar</button>
            </form>
            {proposal && (
              <div className="proposal-summary">
                <span className="avatar">
                  {proposal.applicant.name.slice(0, 1)}
                </span>
                <div>
                  <strong>{proposal.applicant.name}</strong>
                  <small>
                    {proposal.applicant.syntheticIdentifier} · criada em{" "}
                    {date(proposal.createdAt)}
                  </small>
                </div>
                <Status value={proposal.status} />
              </div>
            )}
          </article>
        </section>

        {proposal && (
          <>
            <section id="documents" className="card section-card">
              <div className="section-title">
                <span className="step">02</span>
                <div>
                  <p className="eyebrow">ENTRADA CONTROLADA</p>
                  <h2>Documentos e cobertura</h2>
                  <p>
                    Arquivos digitais são versionados; cada análise usa um
                    snapshot imutável.
                  </p>
                </div>
              </div>
              <div className="upload-zone">
                <label className="drop-label">
                  <span className="upload-icon">＋</span>
                  <strong>Adicionar PDFs sintéticos</strong>
                  <small>Holerites e extratos · até 10 MiB por arquivo</small>
                  <input
                    type="file"
                    accept="application/pdf"
                    multiple
                    onChange={choose}
                  />
                </label>
                {drafts.length > 0 && (
                  <div className="draft-list">
                    {drafts.map((draft) => (
                      <div className="draft" key={draft.id}>
                        <span className="file-badge">PDF</span>
                        <div>
                          <strong>{draft.file.name}</strong>
                          <small>
                            {validation.get(draft.id)?.join(" · ") ||
                              "Pronto para envio"}
                          </small>
                        </div>
                        <select
                          value={draft.type}
                          onChange={(e) =>
                            setDrafts(
                              drafts.map((item) =>
                                item.id === draft.id
                                  ? { ...item, type: e.target.value }
                                  : item,
                              ),
                            )
                          }
                        >
                          <option value={PAYSLIP}>Holerite</option>
                          <option value={BANK_STATEMENT}>Extrato</option>
                          <option value="OTHER">Outro</option>
                        </select>
                        <input
                          aria-label="Competência"
                          placeholder="AAAA-MM"
                          value={draft.period}
                          onChange={(e) =>
                            setDrafts(
                              drafts.map((item) =>
                                item.id === draft.id
                                  ? { ...item, period: e.target.value }
                                  : item,
                              ),
                            )
                          }
                        />
                      </div>
                    ))}
                    <button
                      className="button primary"
                      onClick={upload}
                      disabled={!credentials || !!busy}
                    >
                      Enviar e versionar
                    </button>
                  </div>
                )}
              </div>
              <div className="coverage">
                <div>
                  <span>Holerites</span>
                  <strong>{coverage.payslipPeriods.length} competências</strong>
                </div>
                <div>
                  <span>Extratos</span>
                  <strong>
                    {coverage.statementPeriods.length} competências
                  </strong>
                </div>
                <div>
                  <span>Correspondências</span>
                  <strong>{coverage.matchedPeriods.length} de 3</strong>
                </div>
                <Status value={coverage.complete ? "COMPLETED" : "DRAFT"} />
              </div>
              <div className="document-table">
                <div className="table-head">
                  <span>TIPO</span>
                  <span>COMPETÊNCIA</span>
                  <span>VERSÃO</span>
                  <span>HASH</span>
                </div>
                {documents.length === 0 ? (
                  <p className="empty">
                    Ainda não há documentos nesta proposta.
                  </p>
                ) : (
                  documents.map((doc) => (
                    <div className="table-row" key={doc.id}>
                      <strong>
                        {doc.type === PAYSLIP
                          ? "Holerite"
                          : doc.type === BANK_STATEMENT
                            ? "Extrato bancário"
                            : doc.type}
                      </strong>
                      <span>{doc.period ?? "—"}</span>
                      <span>v{doc.version}</span>
                      <code>{doc.sha256.slice(0, 12)}…</code>
                    </div>
                  ))
                )}
              </div>
            </section>

            <section id="analysis" className="card section-card">
              <div className="section-title">
                <span className="step">03</span>
                <div>
                  <p className="eyebrow">REVISÃO DO DOSSIÊ</p>
                  <h2>Analisar com contexto</h2>
                  <p>
                    O Java coordena regras, snapshot e estado; a IA apenas
                    explica os achados.
                  </p>
                </div>
              </div>
              <div className="action-strip">
                <div>
                  <strong>Pré-requisito</strong>
                  <span>
                    {coverage.complete
                      ? "Documentos financeiros completos"
                      : "Três períodos pareados de holerite e extrato"}
                  </span>
                </div>
                <button
                  className="button subtle"
                  onClick={consult}
                  disabled={!credentials || !!busy}
                >
                  {busy === "bureau"
                    ? "Consultando…"
                    : bureau
                      ? "Atualizar bureau"
                      : "Consultar bureau"}
                </button>
                <button
                  className="button primary"
                  onClick={analyze}
                  disabled={!canAnalyze}
                >
                  {busy === "analysis" ? "Executando…" : "Executar análise"}
                  <span>↗</span>
                </button>
              </div>
              {bureau?.report && (
                <div className="bureau-note">
                  <span className="signal">◌</span>
                  <div>
                    <strong>Bureau de demonstração conectado</strong>
                    <small>
                      {bureau.report.environment} · {bureau.report.provider} ·
                      referência {bureau.id ?? "não informada"}
                    </small>
                  </div>
                  <Status value={bureau.report.status} />
                </div>
              )}
              {analysis && <Dossier analysis={analysis} />}
            </section>

            <section
              id="decision"
              className="card section-card decision-section"
            >
              <div className="section-title">
                <span className="step">04</span>
                <div>
                  <p className="eyebrow">RESPONSABILIDADE HUMANA</p>
                  <h2>Registrar decisão</h2>
                  <p>
                    A decisão exige uma análise finalizada, justificativa e
                    identidade autenticada.
                  </p>
                </div>
              </div>
              {latest && (
                <div className="decision-history">
                  <Status value={latest.outcome} />
                  <div>
                    <strong>{human(latest.outcome)}</strong>
                    <small>{latest.reason}</small>
                  </div>
                  <time>{date(latest.createdAt)}</time>
                </div>
              )}
              <form onSubmit={decide} className="decision-form">
                <label>
                  Resultado
                  <select
                    value={outcome}
                    onChange={(e) => setOutcome(e.target.value)}
                  >
                    <option value="APPROVED">Aprovar</option>
                    <option value="REJECTED">Rejeitar</option>
                    <option value="MORE_INFO_REQUIRED">
                      Solicitar mais informações
                    </option>
                  </select>
                </label>
                <label>
                  Justificativa
                  <textarea
                    required
                    maxLength={1000}
                    value={reason}
                    onChange={(e) => setReason(e.target.value)}
                    placeholder="Descreva a base da decisão e as evidências revisadas."
                  />
                </label>
                <button
                  className="button primary"
                  disabled={
                    !analysis ||
                    analysis.status !== "COMPLETED" ||
                    !credentials ||
                    !!busy
                  }
                >
                  Registrar decisão <span>↗</span>
                </button>
              </form>
              {decisions.length > 0 && (
                <p className="muted">
                  {decisions.length} decisão(ões) preservada(s) no histórico da
                  proposta.
                </p>
              )}
            </section>
          </>
        )}
        <footer>
          <span>Credit Intelligence · PoC</span>
          <span>Java authority · Python interpretation · human decision</span>
        </footer>
      </main>
    </div>
  );
}

function Dossier({ analysis }: { analysis: Analysis }) {
  return (
    <div className="dossier">
      <div className="dossier-head">
        <div>
          <p className="eyebrow">RESULTADO DA ANÁLISE</p>
          <h3>
            {analysis.status === "COMPLETED"
              ? "Dossiê pronto para revisão"
              : "Processamento em andamento"}
          </h3>
        </div>
        <Status value={analysis.status} />
      </div>
      <div className="metric-grid">
        <div>
          <span>Política</span>
          <strong>{analysis.policyVersion}</strong>
        </div>
        <div>
          <span>Snapshot</span>
          <strong>{analysis.documentSnapshotHash.slice(0, 10)}…</strong>
        </div>
        <div>
          <span>Achados</span>
          <strong>{analysis.findings.length}</strong>
        </div>
        <div>
          <span>IA</span>
          <strong>{human(analysis.aiExplanationStatus)}</strong>
        </div>
      </div>
      <div className="finding-list">
        {analysis.findings.length === 0 ? (
          <p className="empty">Nenhum achado retornado.</p>
        ) : (
          analysis.findings.map((finding) => (
            <article
              className={`finding ${finding.result === "PASS" ? "finding-pass" : "finding-fail"}`}
              key={finding.code}
            >
              <div>
                <strong>{finding.code}</strong>
                <Status value={finding.result} />
              </div>
              <p>{finding.details}</p>
              <small>
                {finding.evidenceRef
                  ? `Evidência: ${finding.evidenceRef}`
                  : "Sem referência de evidência"}
              </small>
            </article>
          ))
        )}
      </div>
      {analysis.aiExplanation.summary && (
        <div className="ai-summary">
          <span>✦</span>
          <div>
            <p className="eyebrow">
              INTERPRETAÇÃO ASSISTIDA ·{" "}
              {analysis.aiExplanation.provider ?? "não informado"}
            </p>
            <p>{analysis.aiExplanation.summary}</p>
            <small>
              Não altera findings determinísticos nem a decisão humana.
            </small>
          </div>
        </div>
      )}
    </div>
  );
}
