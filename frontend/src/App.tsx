import { useState } from "react";
import { Credentials, Proposal } from "./api";
import { Shell } from "./components";
import {
  DossierStep,
  DocumentsStep,
  HistoryStep,
  Login,
  ProposalStep,
} from "./steps";
export function App() {
  const [c, setC] = useState<Credentials | null>(null),
    [p, setP] = useState<Proposal | null>(null),
    [bureauCpf, setBureauCpf] = useState(""),
    [autoAnalyze, setAutoAnalyze] = useState(false),
    [step, setStep] = useState(1),
    [showHistory, setShowHistory] = useState(false);
  if (!c) return <Login onLogin={setC} />;
  return (
    <Shell
      step={step}
      user={c.username}
      onReset={() => {
        setC(null);
        setP(null);
        setBureauCpf("");
        setAutoAnalyze(false);
        setStep(1);
        setShowHistory(false);
      }}
      onHistory={() => {
        setP(null);
        setShowHistory(true);
        setStep(1);
      }}
    >
      {showHistory ? (
        <HistoryStep
          c={c}
          onBack={() => {
            setShowHistory(false);
            setStep(1);
          }}
        />
      ) : !p ? (
        <ProposalStep
          c={c}
          onCreated={(x, cpf) => {
            setP(x);
            setBureauCpf(cpf);
            setStep(2);
          }}
        />
      ) : step === 2 ? (
        <DocumentsStep
          c={c}
          p={p}
          onReady={(_docs, shouldAutoAnalyze = false) => {
            setAutoAnalyze(shouldAutoAnalyze);
            setStep(3);
          }}
        />
      ) : (
        <DossierStep
          c={c}
          p={p}
          cpf={bureauCpf}
          autoAnalyze={autoAnalyze}
          onReset={() => {
            setP(null);
            setAutoAnalyze(false);
            setShowHistory(false);
            setStep(1);
          }}
        />
      )}
    </Shell>
  );
}
