import type { InputHTMLAttributes, ReactNode } from "react";

export function Badge({
  children,
  tone = "neutral",
}: {
  children: ReactNode;
  tone?: string;
}) {
  return <span className={`badge ${tone}`}>{children}</span>;
}

export function Field({
  label,
  ...props
}: { label: string } & InputHTMLAttributes<HTMLInputElement>) {
  return (
    <label className="field">
      <span>{label}</span>
      <input {...props} />
    </label>
  );
}

export function Shell({
  step,
  children,
  onReset,
  onHistory,
  user,
}: {
  step: number;
  children: ReactNode;
  onReset: () => void;
  onHistory: () => void;
  user: string;
}) {
  return (
    <div className="app-shell">
      <aside>
        <div className="brand">
          <span className="brand-mark">CI</span>
          <div>
            <strong>
              credit
              <br />
              intelligence
            </strong>
            <small>analyst workspace</small>
          </div>
        </div>
        <nav>
          <div className="nav-label">Jornada de análise</div>
          {[
            [1, "Nova proposta"],
            [2, "Documentos"],
            [3, "Dossiê"],
          ].map(([n, title]) => (
            <div
              className={`nav-item ${step === n ? "active" : ""}`}
              key={String(n)}
            >
              <i>{n}</i>
              <span>{title}</span>
            </div>
          ))}
        </nav>
        <div className="aside-footer">
          <div className="demo-dot" />
          <div>
            <small>Ambiente</small>
            <strong>Sandbox · IA assistida</strong>
          </div>
          <button onClick={onReset}>Encerrar sessão</button>
        </div>
      </aside>
      <main>
        <header>
          <div className="crumb">
            CREDIT INTELLIGENCE <span>/</span> MESA DO ANALISTA
          </div>
          <div className="profile">
            <span className="avatar">{user.slice(0, 1).toUpperCase()}</span>
            <span>{user}</span>
            <button className="header-history" onClick={onHistory}>
              Histórico
            </button>
            <span className="chevron">⌄</span>
          </div>
        </header>
        {children}
      </main>
    </div>
  );
}

export function StepHeader({
  kicker,
  title,
  description,
}: {
  kicker: string;
  title: string;
  description: string;
}) {
  return (
    <div className="step-header">
      <span className="eyebrow">{kicker}</span>
      <h1>{title}</h1>
      <p>{description}</p>
    </div>
  );
}
