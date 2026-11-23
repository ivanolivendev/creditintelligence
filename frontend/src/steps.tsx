import { useCallback, useEffect, useRef, useState } from "react";
import {
  api,
  Analysis,
  BureauReport,
  Credentials,
  Decision,
  DocumentItem,
  Proposal,
} from "./api";
import {
  inferDocumentType,
  inferPeriod,
  bureauCaseForCpf,
  documentReadiness,
  explanationProviderCopy,
  financialPeriodMismatch,
  formatCpf,
  isReadyForAnalysis,
  normalizeCpf,
  REQUIRED_DOCUMENT_TYPES,
  RequiredDocumentType,
  SANDBOX_BUREAU_CASES,
} from "./workflow";
import {
  date,
  displayFactValue,
  fieldLabels,
  findingLabels,
  humanFindingDetails,
  labels,
  money,
  resultLabels,
} from "./presentation";
import { Badge, Field, StepHeader } from "./components";

export function ExecutiveBI({ analysis }: { analysis: Analysis }) {
  const salaryFields = analysis.extractedFields
    .filter((field) => field.fieldName === "netSalary" && field.value)
    .sort((left, right) => {
      const leftPeriod =
        analysis.documents.find((item) => item.documentId === left.documentId)
          ?.period || "";
      const rightPeriod =
        analysis.documents.find((item) => item.documentId === right.documentId)
          ?.period || "";
      return leftPeriod.localeCompare(rightPeriod);
    });
  const salaries = salaryFields
    .map((field) => Number(field.value))
    .filter((value) => Number.isFinite(value));
  const average = salaries.length
    ? salaries.reduce((sum, value) => sum + value, 0) / salaries.length
    : null;
  const difference = average
    ? ((average - analysis.declaredMonthlyIncome) / average) * 100
    : null;
  const warnings = analysis.findings.filter(
    (finding) => finding.result !== "PASS",
  );
  const evidenceCount = analysis.extractedFields.filter(
    (field) => field.evidenceRef,
  ).length;
  const maxSalary = Math.max(...salaries, 1);
  const conclusion = warnings.length ? "Revisão necessária" : "Sem alertas";
  const conclusionText = warnings.length
    ? "Existem pontos objetivos para conferir antes da decisão."
    : "Os critérios determinísticos foram atendidos pelos documentos enviados.";

  return (
    <section className="executive-bi">
      <div className="bi-heading">
        <div>
          <span className="section-kicker">VISÃO EXECUTIVA</span>
          <h3>O que os documentos mostram</h3>
          <p>{conclusionText}</p>
        </div>
        <div
          className={`bi-conclusion ${warnings.length ? "attention" : "ok"}`}
        >
          <span>{warnings.length ? "ATENÇÃO" : "CONFORME"}</span>
          <strong>{conclusion}</strong>
        </div>
      </div>
      <div className="bi-kpis">
        <div className="bi-kpi">
          <small>RENDA DECLARADA</small>
          <strong>{money(analysis.declaredMonthlyIncome)}</strong>
          <span>informada na proposta</span>
        </div>
        <div className="bi-kpi highlight">
          <small>MÉDIA COMPROVADA</small>
          <strong>{money(average)}</strong>
          <span>{salaries.length} competência(s) encontradas</span>
        </div>
        <div className="bi-kpi">
          <small>DIFERENÇA</small>
          <strong>
            {difference == null
              ? "—"
              : `${difference >= 0 ? "+" : ""}${difference.toFixed(1)}%`}
          </strong>
          <span>diferença para a média comprovada</span>
        </div>
        <div className="bi-kpi">
          <small>EVIDÊNCIAS</small>
          <strong>{evidenceCount}</strong>
          <span>itens com origem rastreável</span>
        </div>
      </div>
      <div className="bi-panels">
        <div className="bi-chart-panel">
          <div className="bi-panel-title">
            <div>
              <strong>Evolução da renda líquida</strong>
              <span>Valores extraídos dos holerites</span>
            </div>
            <Badge tone="neutral">BRL</Badge>
          </div>
          {salaries.length ? (
            <div
              className="salary-chart"
              aria-label="Evolução da renda líquida"
            >
              {salaryFields.map((field, index) => {
                const value = Number(field.value);
                const document = analysis.documents.find(
                  (item) => item.documentId === field.documentId,
                );
                const period = document?.period || `Mês ${index + 1}`;
                return (
                  <div
                    className="salary-bar-column"
                    key={`${field.documentId}-${index}`}
                  >
                    <strong>{money(value)}</strong>
                    <div className="salary-bar-track">
                      <div
                        className="salary-bar"
                        style={{
                          height: `${Math.max((value / maxSalary) * 100, 12)}%`,
                        }}
                      />
                    </div>
                    <span>{period}</span>
                  </div>
                );
              })}
            </div>
          ) : (
            <div className="chart-empty">
              Não há salários líquidos com evidência para montar o gráfico.
            </div>
          )}
        </div>
        <div className="bi-insight-panel">
          <div className="bi-panel-title">
            <div>
              <strong>Pontos para decisão</strong>
              <span>Resumo acionável para o analista</span>
            </div>
          </div>
          {warnings.length ? (
            <div className="bi-alert-list">
              {warnings.map((finding) => (
                <div className="bi-alert" key={finding.code}>
                  <span>!</span>
                  <div>
                    <strong>
                      {findingLabels[finding.code] || finding.code}
                    </strong>
                    <p>{humanFindingDetails(finding)}</p>
                  </div>
                </div>
              ))}
            </div>
          ) : (
            <div className="bi-clear-state">
              Nenhum ponto de atenção foi encontrado nas regras executadas.
            </div>
          )}
        </div>
      </div>
    </section>
  );
}
export function HistoryStep({
  c,
  onBack,
}: {
  c: Credentials;
  onBack: () => void;
}) {
  const [rows, setRows] = useState<
    Array<{ proposal: Proposal; decisions: Decision[] }>
  >([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");
  useEffect(() => {
    api
      .listProposals(c)
      .then(async (proposals) => {
        const enriched = await Promise.all(
          proposals.map(async (proposal) => ({
            proposal,
            decisions: await api.listDecisions(c, proposal.id),
          })),
        );
        setRows(enriched);
      })
      .catch((err) =>
        setError(
          err instanceof Error
            ? err.message
            : "Não foi possível carregar o histórico.",
        ),
      )
      .finally(() => setLoading(false));
  }, [c]);
  return (
    <section className="content">
      <StepHeader
        kicker="HISTÓRICO"
        title="Acompanhe as propostas."
        description="Consulte o que foi analisado, quando aconteceu e qual decisão humana foi registrada."
      />
      {loading && <div className="empty-state">Carregando histórico…</div>}
      {error && (
        <div className="error page-error" role="alert">
          {error}
        </div>
      )}
      {!loading && !error && rows.length === 0 && (
        <div className="empty-state">
          Nenhuma proposta foi registrada neste ambiente.
        </div>
      )}
      {!loading && !error && rows.length > 0 && (
        <div className="history-table">
          {rows.map(({ proposal, decisions }) => {
            const decision = decisions.at(-1);
            return (
              <article className="history-card" key={proposal.id}>
                <div>
                  <span className="section-kicker">PROPOSTA</span>
                  <h3>{proposal.applicant.name}</h3>
                  <small>
                    {date(proposal.createdAt)} · {proposal.id.slice(0, 8)}
                  </small>
                </div>
                <div className="history-card-kpi">
                  <small>RENDA DECLARADA</small>
                  <strong>{money(proposal.declaredMonthlyIncome)}</strong>
                </div>
                <div className="history-card-decision">
                  <small>DECISÃO HUMANA</small>
                  {decision ? (
                    <>
                      <Badge tone="success">{decision.outcome}</Badge>
                      <span>{decision.reason}</span>
                    </>
                  ) : (
                    <Badge tone="warning">Ainda não registrada</Badge>
                  )}
                </div>
              </article>
            );
          })}
        </div>
      )}
      <div className="reset-link">
        <button onClick={onBack}>Voltar para nova proposta</button>
      </div>
    </section>
  );
}
export function Login({ onLogin }: { onLogin: (c: Credentials) => void }) {
  const [u, setU] = useState("analyst"),
    [p, setP] = useState("local_demo_only_change_me"),
    [busy, setBusy] = useState(false),
    [error, setError] = useState("");
  return (
    <div className="login-page">
      <div className="login-art">
        <div className="art-grid" />
        <div className="art-copy">
          <span className="eyebrow">CREDIT INTELLIGENCE</span>
          <h1>Decisões mais claras começam com evidências.</h1>
          <p>
            Um espaço de trabalho para organizar documentos sintéticos, validar
            achados e registrar a decisão humana.
          </p>
          <span className="art-line" />
        </div>
      </div>
      <div className="login-card">
        <span className="eyebrow">ACESSO DE DEMONSTRAÇÃO</span>
        <h2>Entrar na mesa</h2>
        <p className="muted">
          Use a identidade de analista configurada no ambiente local.
        </p>
        <form
          onSubmit={(e) => {
            e.preventDefault();
            setBusy(true);
            setError("");
            onLogin({ username: u, password: p });
            setBusy(false);
          }}
        >
          <Field
            label="Usuário"
            value={u}
            onChange={(e) => setU(e.target.value)}
            autoComplete="username"
          />
          <Field
            label="Senha"
            type="password"
            value={p}
            onChange={(e) => setP(e.target.value)}
            autoComplete="current-password"
          />
          {error && (
            <div className="error" role="alert">
              {error}
            </div>
          )}
          <button className="primary wide" disabled={busy}>
            {busy ? "Entrando…" : "Acessar ambiente"}
            <span>↗</span>
          </button>
        </form>
        <div className="login-note">
          <span>●</span> Dados exclusivamente sintéticos
          <br />
          <span>●</span> IA via Amazon Bedrock · DeepSeek
        </div>
      </div>
    </div>
  );
}
export function ProposalStep({
  c,
  onCreated,
}: {
  c: Credentials;
  onCreated: (p: Proposal, cpf: string) => void;
}) {
  const [name, setName] = useState(""),
    [cpf, setCpf] = useState(""),
    [income, setIncome] = useState(""),
    [busy, setBusy] = useState(false),
    [error, setError] = useState("");
  return (
    <section className="content narrow">
      <StepHeader
        kicker="01 / CONTEXTO"
        title="Comece pela proposta."
        description="Registre o contexto declarado pelo solicitante. A partir dele, a mesa organiza as fontes que serão revisadas."
      />
      <div className="form-panel">
        <div className="panel-heading">
          <div>
            <span className="section-kicker">IDENTIFICAÇÃO SINTÉTICA</span>
            <h3>Quem está sendo analisado?</h3>
          </div>
          <Badge tone="info">Somente demonstração</Badge>
        </div>
        <form
          onSubmit={async (e) => {
            e.preventDefault();
            setBusy(true);
            setError("");
            try {
              if (!bureauCaseForCpf(cpf)) {
                throw new Error(
                  "Use um dos CPFs sintéticos disponíveis no sandbox.",
                );
              }
              onCreated(
                await api.createProposal(c, name, income),
                normalizeCpf(cpf),
              );
            } catch (err) {
              setError(
                err instanceof Error
                  ? err.message
                  : "Não foi possível criar a proposta.",
              );
            } finally {
              setBusy(false);
            }
          }}
        >
          <div className="form-grid">
            <Field
              label="Nome para a demonstração"
              placeholder="Ex.: Maria Silva"
              value={name}
              onChange={(e) => setName(e.target.value)}
              required
            />
            <Field
              label="Renda mensal declarada"
              placeholder="0,00"
              type="number"
              min="0"
              step="0.01"
              value={income}
              onChange={(e) => setIncome(e.target.value)}
              required
            />
            <Field
              label="CPF para análise"
              placeholder="000.000.000-00"
              value={formatCpf(cpf)}
              onChange={(e) => setCpf(normalizeCpf(e.target.value))}
              inputMode="numeric"
              maxLength={14}
              list="sandbox-cpfs"
              title="Use apenas um CPF sintético publicado pelo sandbox."
              required
            />
          </div>
          <datalist id="sandbox-cpfs">
            {SANDBOX_BUREAU_CASES.map((item) => (
              <option key={item.cpf} value={formatCpf(item.cpf)}>
                {item.label}
              </option>
            ))}
          </datalist>
          <div className="sandbox-tip">
            <strong>CPFs sintéticos para teste</strong>
            <span>Não use CPF real.</span>
            {SANDBOX_BUREAU_CASES.map((item) => (
              <button
                type="button"
                key={item.cpf}
                onClick={() => setCpf(item.cpf)}
              >
                {formatCpf(item.cpf)} · {item.label}
              </button>
            ))}
          </div>
          {error && (
            <div className="error" role="alert">
              {error}
            </div>
          )}
          <div className="form-actions">
            <span className="muted small">
              Moeda de referência: BRL · valores não representam oferta de
              crédito.
            </span>
            <button className="primary" disabled={busy}>
              {busy ? "Registrando…" : "Criar proposta"}
              <span>→</span>
            </button>
          </div>
        </form>
      </div>
      <div className="principle">
        <span className="principle-mark">✦</span>
        <div>
          <strong>A decisão continua humana.</strong>
          <p>
            O sistema organiza evidências e regras determinísticas. Nenhuma
            análise aqui aprova ou rejeita automaticamente.
          </p>
        </div>
      </div>
    </section>
  );
}
export function DocumentsStep({
  c,
  p,
  onReady,
}: {
  c: Credentials;
  p: Proposal;
  onReady: (docs: DocumentItem[], autoAnalyze?: boolean) => void;
}) {
  type QueuedFile = {
    file: File;
    type: RequiredDocumentType | "";
    period?: string;
    status: "pending" | "uploading" | "error";
    error?: string;
  };
  const [docs, setDocs] = useState<DocumentItem[]>([]),
    [queue, setQueue] = useState<QueuedFile[]>([]),
    [dragging, setDragging] = useState(false),
    [busy, setBusy] = useState(false),
    [error, setError] = useState(""),
    [progress, setProgress] = useState({ done: 0, total: 0 });
  const [loadingDocs, setLoadingDocs] = useState(true);
  useEffect(() => {
    api
      .listDocuments(c, p.id)
      .then(setDocs)
      .catch((err) =>
        setError(
          err instanceof Error
            ? err.message
            : "Não foi possível carregar os documentos.",
        ),
      )
      .finally(() => setLoadingDocs(false));
  }, [c, p.id]);
  const required = REQUIRED_DOCUMENT_TYPES;
  const ready = isReadyForAnalysis(docs);
  const periodMismatch = financialPeriodMismatch(docs);
  const readiness = documentReadiness(docs);
  const completeRequirements = readiness.filter(
    (item) => item.current >= item.required,
  ).length;
  const addFiles = (files: FileList | File[]) => {
    const next = Array.from(files)
      .filter(
        (file) =>
          file.type === "application/pdf" ||
          file.name.toLowerCase().endsWith(".pdf"),
      )
      .filter(
        (file) =>
          !queue.some(
            (item) =>
              item.file.name === file.name && item.file.size === file.size,
          ),
      )
      .map((file) => ({
        file,
        type: (inferDocumentType(file.name) ?? "") as RequiredDocumentType | "",
        period: inferPeriod(file.name),
        status: "pending" as const,
      }));
    if (next.length) setQueue((current) => [...current, ...next]);
  };
  return (
    <section className="content">
      <StepHeader
        kicker="02 / FONTES"
        title="Solte tudo de uma vez."
        description="Adicione os PDFs da proposta em lote. Nós identificamos o tipo pelo nome do arquivo, avisamos o que não ficou claro e mostramos o que ainda falta antes da análise."
      />
      <div className="workspace-grid single-flow">
        <div className="upload-panel">
          <div className="panel-heading">
            <div>
              <span className="section-kicker">INGESTÃO EM LOTE</span>
              <h3>Arraste os documentos</h3>
            </div>
            <span className="upload-icon">↥</span>
          </div>
          <form
            onSubmit={async (e) => {
              e.preventDefault();
              if (!queue.length) return;
              if (queue.some((item) => !item.type)) {
                setError(
                  "Identifique o tipo de cada arquivo marcado como Revisar antes de enviar.",
                );
                return;
              }
              setBusy(true);
              setError("");
              const pending = queue.filter(
                (item) => item.status !== "uploading",
              );
              const failed: QueuedFile[] = [];
              setProgress({ done: 0, total: pending.length });
              try {
                const uploaded: DocumentItem[] = [];
                for (const item of pending) {
                  setQueue((current) =>
                    current.map((entry) =>
                      entry.file === item.file
                        ? { ...entry, status: "uploading", error: undefined }
                        : entry,
                    ),
                  );
                  try {
                    uploaded.push(
                      await api.upload(
                        c,
                        p.id,
                        item.type,
                        item.file,
                        item.period,
                      ),
                    );
                  } catch (err) {
                    failed.push({
                      ...item,
                      status: "error",
                      error:
                        err instanceof Error
                          ? err.message
                          : "Não foi possível enviar este arquivo.",
                    });
                  } finally {
                    setProgress((current) => ({
                      ...current,
                      done: current.done + 1,
                    }));
                  }
                }
                const nextDocs = uploaded.reduce(
                  (current, d) => [
                    ...current.filter(
                      (x) => !(x.type === d.type && x.period === d.period),
                    ),
                    d,
                  ],
                  docs,
                );
                setDocs(nextDocs);
                setQueue(failed);
                if (failed.length) {
                  setError(
                    `${failed.length} arquivo${failed.length > 1 ? "s" : ""} não foi${failed.length > 1 ? "ram" : ""} enviado${failed.length > 1 ? "s" : ""}. Corrija ou tente novamente.`,
                  );
                } else if (isReadyForAnalysis(nextDocs)) {
                  onReady(nextDocs, true);
                }
              } finally {
                setBusy(false);
              }
            }}
          >
            <label
              className={`dropzone ${dragging ? "dragging" : ""}`}
              onDragEnter={(e) => {
                e.preventDefault();
                setDragging(true);
              }}
              onDragOver={(e) => e.preventDefault()}
              onDragLeave={() => setDragging(false)}
              onDrop={(e) => {
                e.preventDefault();
                setDragging(false);
                addFiles(e.dataTransfer.files);
              }}
            >
              <input
                type="file"
                accept="application/pdf,.pdf"
                multiple
                onChange={(e) => {
                  if (e.target.files) addFiles(e.target.files);
                  e.currentTarget.value = "";
                }}
              />
              <span className="drop-mark">↥</span>
              <strong>Solte todos os PDFs aqui</strong>
              <small>
                ou clique para procurar · PDF digital · máximo 10 MB por arquivo
              </small>
            </label>
            {queue.length > 0 && (
              <div className="queue-list">
                <div className="queue-title">
                  <strong>
                    {queue.length} arquivo{queue.length > 1 ? "s" : ""} na fila
                  </strong>
                  <button type="button" onClick={() => setQueue([])}>
                    Limpar
                  </button>
                </div>
                {busy && progress.total > 0 && (
                  <div className="upload-progress" aria-live="polite">
                    <div className="upload-progress-label">
                      <span>Enviando arquivos</span>
                      <strong>
                        {progress.done}/{progress.total}
                      </strong>
                    </div>
                    <div className="progress-track">
                      <span
                        style={{
                          width: `${(progress.done / progress.total) * 100}%`,
                        }}
                      />
                    </div>
                  </div>
                )}
                {queue.map((item, index) => (
                  <div
                    className="queue-row"
                    key={`${item.file.name}-${item.file.size}`}
                  >
                    <span className="queue-file">PDF</span>
                    <div>
                      <strong>{item.file.name}</strong>
                      <small>
                        {item.file.size > 1024 * 1024
                          ? `${(item.file.size / 1024 / 1024).toFixed(1)} MB`
                          : `${Math.ceil(item.file.size / 1024)} KB`}
                      </small>
                      {item.status === "error" && (
                        <small className="queue-error" role="alert">
                          {item.error ?? "Falha no envio"}
                        </small>
                      )}
                    </div>
                    <span className={`queue-status ${item.status}`}>
                      {item.status === "uploading"
                        ? "Enviando…"
                        : item.status === "error"
                          ? "Tentar novamente"
                          : "Pendente"}
                    </span>
                    <select
                      aria-label={`Tipo de ${item.file.name}`}
                      value={item.type}
                      onChange={(e) =>
                        setQueue((current) =>
                          current.map((entry, i) =>
                            i === index
                              ? {
                                  ...entry,
                                  type: e.target.value as RequiredDocumentType,
                                }
                              : entry,
                          ),
                        )
                      }
                    >
                      <option value="">
                        {item.type ? labels[item.type] : "Revisar tipo"}
                      </option>
                      {required.map((type) => (
                        <option key={type} value={type}>
                          {labels[type]}
                        </option>
                      ))}
                    </select>
                    {(item.type === "PAYSLIP" ||
                      item.type === "BANK_STATEMENT") && (
                      <input
                        aria-label={`Competência de ${item.file.name}`}
                        type="month"
                        value={item.period ?? ""}
                        onChange={(e) =>
                          setQueue((current) =>
                            current.map((entry, i) =>
                              i === index
                                ? { ...entry, period: e.target.value }
                                : entry,
                            ),
                          )
                        }
                      />
                    )}
                  </div>
                ))}
              </div>
            )}
            {error && (
              <div className="error" role="alert">
                {error}
              </div>
            )}
            <button className="secondary wide" disabled={!queue.length || busy}>
              {busy
                ? "Enviando lote…"
                : `Enviar ${queue.length ? `${queue.length} arquivo${queue.length > 1 ? "s" : ""}` : "documentos"}`}
              <span>↗</span>
            </button>
          </form>
        </div>
        <div className="sources-panel">
          <div className="panel-heading">
            <div>
              <span className="section-kicker">CONJUNTO ATUAL</span>
              <h3>{docs.length} documentos vinculados</h3>
            </div>
            <Badge tone={ready ? "success" : "warning"}>
              {ready
                ? "Pronto para analisar"
                : `${completeRequirements}/4 requisitos`}
            </Badge>
          </div>
          <div className="readiness-list" aria-label="Requisitos da análise">
            {readiness.map((item) => (
              <div className="readiness-row" key={item.type}>
                <span
                  className={`source-status ${item.current >= item.required ? "ok" : "pending"}`}
                >
                  {item.current >= item.required ? "✓" : "·"}
                </span>
                <span>{labels[item.type]}</span>
                <strong>
                  {Math.min(item.current, item.required)}/{item.required}
                </strong>
              </div>
            ))}
          </div>
          <div className="source-list">
            {required.map((t) => {
              const d = docs.find((x) => x.type === t);
              return (
                <div
                  className={`source-row ${d ? "attached" : "missing"}`}
                  key={t}
                >
                  <span className="source-status">{d ? "✓" : "○"}</span>
                  <div>
                    <strong>{labels[t]}</strong>
                    <small>
                      {d
                        ? `${d.period ? d.period + " · " : ""}versão ${d.version} · ${d.sha256.slice(0, 10)}…`
                        : "Ainda não adicionada"}
                    </small>
                  </div>
                  {d && <Badge tone="success">{d.period ?? "Vinculado"}</Badge>}
                </div>
              );
            })}
          </div>
          <div className="source-footnote">
            {loadingDocs
              ? "Carregando documentos vinculados…"
              : periodMismatch.hasMismatch
                ? "Corrija as competências antes da análise: " +
                  periodMismatch.messages.join(" · ") +
                  "."
                : ready
                  ? "Todos os documentos obrigatórios estão prontos para a análise."
                  : "A análise será liberada quando identidade, comprovante, três holerites e três extratos estiverem vinculados."}
          </div>
        </div>
      </div>
      <div className="bottom-bar">
        <span>
          <b>{completeRequirements}/4</b> requisitos completos
        </span>
        <button
          className="primary"
          disabled={!docs.length}
          onClick={() => onReady(docs, false)}
        >
          {ready ? "Continuar para análise" : "Continuar e ver pendências"}{" "}
          <span>→</span>
        </button>
      </div>
    </section>
  );
}
export function DossierStep({
  c,
  p,
  onReset,
  cpf,
  autoAnalyze,
}: {
  c: Credentials;
  p: Proposal;
  onReset: () => void;
  cpf: string;
  autoAnalyze: boolean;
}) {
  const [a, setA] = useState<Analysis | null>(null),
    [docs, setDocs] = useState<DocumentItem[]>([]),
    [bureau, setBureau] = useState<BureauReport | null>(null),
    [decisions, setDecisions] = useState<Decision[]>([]),
    [busy, setBusy] = useState(false),
    [error, setError] = useState(""),
    [outcome, setOutcome] = useState("APPROVED"),
    [reason, setReason] = useState("");
  const autoRunStarted = useRef(false);
  useEffect(() => {
    api
      .listDocuments(c, p.id)
      .then(setDocs)
      .catch((err) =>
        setError(
          err instanceof Error
            ? err.message
            : "Não foi possível carregar os documentos.",
        ),
      );
  }, [c, p.id]);
  const financialDocs = docs.some(
    (doc) => doc.type === "PAYSLIP" || doc.type === "BANK_STATEMENT",
  );
  const needsBureau = financialDocs && bureau?.status !== "AVAILABLE";
  const run = useCallback(async () => {
    setBusy(true);
    setError("");
    try {
      if (needsBureau) {
        const bureauCase = bureauCaseForCpf(cpf);
        if (!bureauCase)
          throw new Error("CPF sintético não encontrado no sandbox.");
        const result = await api.consultBureau(c, p.id, bureauCase.caseName);
        setBureau(result.report);
        if (result.report.status !== "AVAILABLE") {
          throw new Error(
            "A consulta ao bureau não ficou disponível; a análise não foi executada.",
          );
        }
      }
      setA(
        await api.analyze(c, p.id, {
          principalRequested: null,
          termMonths: 36,
          monthlyInterestRate: 0.02,
          currency: "BRL",
          pricingSource: "MANUAL_DEMO",
        }),
      );
    } catch (err) {
      setError(err instanceof Error ? err.message : "Análise não concluída.");
    } finally {
      setBusy(false);
    }
  }, [c, cpf, needsBureau, p.id]);
  const decide = async () => {
    if (!a || !reason.trim()) return;
    setBusy(true);
    try {
      const d = await api.decide(c, p.id, a, outcome, reason);
      setDecisions((x) => [d, ...x]);
      setReason("");
    } catch (err) {
      setError(err instanceof Error ? err.message : "Decisão não registrada.");
    } finally {
      setBusy(false);
    }
  };
  const fieldGroups = a
    ? Array.from(
        a.extractedFields.reduce((groups, field) => {
          const group = groups.get(field.fieldName) || [];
          group.push(field);
          groups.set(field.fieldName, group);
          return groups;
        }, new Map<string, Analysis["extractedFields"]>()),
      )
    : [];
  useEffect(() => {
    if (
      autoAnalyze &&
      docs.length &&
      !a &&
      !busy &&
      !autoRunStarted.current &&
      isReadyForAnalysis(docs)
    ) {
      autoRunStarted.current = true;
      const timer = window.setTimeout(() => void run(), 0);
      return () => window.clearTimeout(timer);
    }
  }, [autoAnalyze, docs, a, busy, run]);
  return (
    <section className="content">
      <StepHeader
        kicker="03 / DOSSIÊ"
        title="Entenda antes de decidir."
        description="Começamos pela conclusão e pelos pontos de atenção. As evidências técnicas ficam disponíveis para conferência e auditoria."
      />
      {!a ? (
        <div className="analysis-launch">
          <div className="launch-orbit">✦</div>
          <div>
            <span className="section-kicker">PRÓXIMO PASSO</span>
            <h2>{docs.length} documentos prontos para análise</h2>
            <p>
              Ao continuar, o sistema consulta o bureau quando necessário,
              congela os documentos atuais, extrai evidências e aplica a
              política vigente. Você verá o BI automaticamente ao final.
            </p>
            {bureau && (
              <div className="bureau-result">
                <Badge tone="success">Bureau {bureau.environment}</Badge>
                <strong>
                  {bureau.status === "AVAILABLE"
                    ? "Consulta disponível"
                    : bureau.status}
                </strong>
                <small>
                  {bureau.score
                    ? `Score demo ${bureau.score.value} · ${bureau.score.model}`
                    : "Score desconhecido"}
                </small>
              </div>
            )}
          </div>
          <button className="primary" onClick={run} disabled={busy}>
            {busy ? "Consultando e analisando…" : "Analisar documentos"}
            <span>→</span>
          </button>
        </div>
      ) : (
        <>
          <div className="dossier-top">
            <div>
              <span className="section-kicker">
                PROPOSTA {p.id.slice(0, 8).toUpperCase()}
              </span>
              <h2>{p.applicant.name}</h2>
              <p className="muted">
                Criada em {date(p.createdAt)} · análise rastreável
              </p>
            </div>
            <Badge tone={a.status === "COMPLETED" ? "success" : "warning"}>
              {a.status}
            </Badge>
            <div className="metric">
              <small>Renda declarada</small>
              <strong>{money(a.declaredMonthlyIncome)}</strong>
            </div>
            <div className="metric">
              <small>Política</small>
              <strong>{a.policyVersion}</strong>
            </div>
          </div>
          <ExecutiveBI analysis={a} />
          {a.financialAssessment?.bureau && (
            <section className="bureau-insight">
              <div className="bureau-insight-main">
                <span className="section-kicker">CONTEXTO DO BUREAU</span>
                <h3>Score de crédito</h3>
                <strong className="bureau-score">
                  {a.financialAssessment.bureau.score?.value ?? "Não retornado"}
                </strong>
                <span>
                  {a.financialAssessment.bureau.score?.model ||
                    "Modelo não informado"}
                  {" · "}
                  {a.financialAssessment.bureau.status === "AVAILABLE"
                    ? "consulta disponível"
                    : a.financialAssessment.bureau.status}
                </span>
              </div>
              <div className="bureau-insight-note">
                <strong>Como interpretar</strong>
                <p>
                  Considere este score como um sinal complementar de risco. Ele
                  não substitui a renda comprovada, as obrigações identificadas
                  ou a avaliação da capacidade de pagamento.
                </p>
                <small>
                  Fonte: {a.financialAssessment.bureau.provider} · ambiente{" "}
                  {a.financialAssessment.bureau.environment}
                </small>
              </div>
            </section>
          )}
          <div className="dossier-sections">
            <section className="review-section findings-section">
              <div className="section-title section-title-large">
                <div>
                  <span>O QUE PRECISA SER CONFERIDO</span>
                  <h3>Regras da análise</h3>
                </div>
                <small>{a.findings.length} verificações</small>
              </div>
              <div className="finding-list">
                {a.findings.map((f) => (
                  <article
                    className={`finding-card ${f.result.toLowerCase()}`}
                    key={f.code}
                  >
                    <div className="finding-icon">
                      {f.result === "PASS" ? "✓" : "!"}
                    </div>
                    <div className="finding-content">
                      <div className="finding-head">
                        <strong>
                          {findingLabels[f.code] || f.code.replaceAll("_", " ")}
                        </strong>
                        <Badge
                          tone={f.result === "PASS" ? "success" : "warning"}
                        >
                          {resultLabels[f.result] || f.result}
                        </Badge>
                      </div>
                      <p>{humanFindingDetails(f)}</p>
                      <details>
                        <summary>Ver evidência técnica</summary>
                        <small>
                          {f.evidenceRef ||
                            "Esta regra não possui referência documental direta."}
                        </small>
                        <code>{f.details}</code>
                      </details>
                    </div>
                  </article>
                ))}
                {a.findings.length === 0 && (
                  <p className="muted">Nenhuma regra retornada.</p>
                )}
              </div>
            </section>
            <section className="review-section evidence-section">
              <div className="section-title section-title-large">
                <div>
                  <span>ORIGEM DOS NÚMEROS</span>
                  <h3>Documentos e evidências</h3>
                </div>
                <small>
                  {a.extractedFields.filter((f) => f.value).length} fatos
                  encontrados
                </small>
              </div>
              <div className="fact-groups">
                {fieldGroups
                  .filter(([, fields]) => fields.some((field) => field.value))
                  .map(([fieldName, fields]) => (
                    <div className="fact-group" key={fieldName}>
                      <div className="fact-group-heading">
                        <strong>
                          {fieldLabels[fieldName] ||
                            fieldName.replaceAll("_", " ")}
                        </strong>
                        <span>
                          {fields.filter((field) => field.value).length}{" "}
                          registros
                        </span>
                      </div>
                      {fields
                        .filter((field) => field.value)
                        .map((field, index) => {
                          const document = a.documents.find(
                            (item) => item.documentId === field.documentId,
                          );
                          return (
                            <div
                              className="fact-row"
                              key={`${field.documentId}-${index}`}
                            >
                              <strong>
                                {displayFactValue(field.fieldName, field.value)}
                              </strong>
                              <span>
                                {document?.period || "Período não identificado"}
                              </span>
                              <small>
                                {field.evidenceRef
                                  ? `Página ${field.page ?? "—"}`
                                  : "Sem evidência"}
                              </small>
                            </div>
                          );
                        })}
                    </div>
                  ))}
              </div>
              {fieldGroups.some(([, fields]) =>
                fields.some((field) => !field.value),
              ) && (
                <div className="missing-facts">
                  <strong>Informação não encontrada</strong>
                  <span>
                    {fieldGroups
                      .filter(([, fields]) =>
                        fields.some((field) => !field.value),
                      )
                      .map(([fieldName]) => fieldLabels[fieldName] || fieldName)
                      .join(", ")}
                  </span>
                </div>
              )}
            </section>
          </div>
          <div className="explanation">
            {(() => {
              const provider = explanationProviderCopy(
                a.aiExplanation?.provider,
                a.aiExplanationStatus,
              );
              return (
                <>
                  <div>
                    <span className="section-kicker">
                      LEITURA ASSISTIDA · {provider.label}
                    </span>
                    <h3>O que merece atenção</h3>
                  </div>
                  <div>
                    <small>{provider.description}</small>
                    <p>
                      {a.aiExplanation?.summary ||
                        "A explicação não está disponível; prossiga pela evidência e pelos achados determinísticos."}
                    </p>
                    {a.aiExplanation?.limitations?.map((x, i) => (
                      <small key={i}>Nota: {x}</small>
                    ))}
                  </div>
                </>
              );
            })()}
          </div>
          <div className="decision-panel">
            <div>
              <span className="section-kicker">ATO HUMANO</span>
              <h3>Registrar decisão</h3>
              <p>
                Esta ação será vinculada à análise e ao snapshot{" "}
                <code>{a.documentSnapshotHash.slice(0, 16)}…</code>.
              </p>
            </div>
            <div className="decision-form">
              <select
                aria-label="Resultado da decisão"
                value={outcome}
                onChange={(e) => setOutcome(e.target.value)}
              >
                <option value="APPROVED">Aprovar</option>
                <option value="REJECTED">Rejeitar</option>
                <option value="MORE_INFO_REQUIRED">
                  Solicitar informações
                </option>
              </select>
              <textarea
                aria-label="Justificativa da decisão"
                value={reason}
                onChange={(e) => setReason(e.target.value)}
                placeholder="Justifique a decisão com base no dossiê…"
                maxLength={1000}
              />
              <button
                className="primary"
                onClick={decide}
                disabled={!reason.trim() || busy}
              >
                {busy ? "Registrando…" : "Confirmar decisão"}
                <span>↗</span>
              </button>
            </div>
          </div>
          {decisions.length > 0 && (
            <div className="history">
              <div className="section-title">
                <span>HISTÓRICO DE DECISÕES</span>
              </div>
              {decisions.map((d) => (
                <div className="history-row" key={d.id}>
                  <Badge tone="success">{d.outcome}</Badge>
                  <span>{d.reason}</span>
                  <small>{date(d.createdAt)}</small>
                </div>
              ))}
            </div>
          )}
        </>
      )}
      {error && (
        <div className="error page-error" role="alert">
          {error}
        </div>
      )}
      <div className="reset-link">
        <button onClick={onReset}>Começar outra proposta</button>
      </div>
    </section>
  );
}
