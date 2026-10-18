"use client";

import { Logo, flowThemeStyle } from "@/brand";
import { useT, type MessageKey } from "../i18n/client";
import { FlowBrand } from "./flow-brand";
import { CheckIcon, LockIcon } from "./icons";

export type FlowStep = "consent" | "document" | "face" | "done";

const STEPS: { id: FlowStep; label: MessageKey }[] = [
  { id: "consent", label: "steps.consent" },
  { id: "document", label: "steps.document" },
  { id: "face", label: "steps.face" },
  { id: "done", label: "steps.done" },
];

export function FlowShell({
  step,
  wide = false,
  children,
}: {
  step: FlowStep | null;
  wide?: boolean;
  children: React.ReactNode;
}) {
  return (
    <div className="rm-flow-page" style={flowThemeStyle()}>
      <main className="rm-flow" data-wide={wide ? "true" : undefined}>
        <FlowBrand />
        <section className="rm-flow-card">
          {step ? <Stepper step={step} /> : null}
          <div className="rm-flow-body">{children}</div>
        </section>
        <FlowFooter />
      </main>
    </div>
  );
}

function Stepper({ step }: { step: FlowStep }) {
  const t = useT();
  const current = STEPS.findIndex((candidate) => candidate.id === step);
  const label = t("shell.stepOf")
    .replace("{current}", String(current + 1))
    .replace("{total}", String(STEPS.length));

  return (
    <nav className="rm-stepper" aria-label={label}>
      <ol>
        {STEPS.map((candidate, index) => {
          const state = index < current ? "done" : index === current ? "current" : "todo";
          return (
            <li key={candidate.id} data-state={state} aria-current={state === "current" ? "step" : undefined}>
              <span className="rm-stepper-dot" aria-hidden="true">
                {state === "done" ? <CheckIcon size={12} /> : index + 1}
              </span>
              <span className="rm-stepper-label">{t(candidate.label)}</span>
            </li>
          );
        })}
      </ol>
      <p className="rm-stepper-mobile" aria-hidden="true">
        <span>{label}</span>
        <strong>{t(STEPS[current].label)}</strong>
      </p>
      <span className="rm-stepper-bar" aria-hidden="true">
        <span style={{ width: `${((current + 1) / STEPS.length) * 100}%` }} />
      </span>
    </nav>
  );
}

function FlowFooter() {
  const t = useT();
  return (
    <footer className="rm-flow-footer">
      <div className="rm-flow-privacy">
        <LockIcon size={14} />
        <span>{t("shell.privacy")}</span>
      </div>
      <div className="rm-flow-powered">
        <span>{t("shell.poweredBy")}</span>
        <Logo size={14} tone="mono" />
        <strong>Recogniz-Me</strong>
      </div>
    </footer>
  );
}

export function StateScreen({
  tone,
  icon,
  title,
  lead,
  children,
}: {
  tone: "success" | "neutral" | "danger" | "progress";
  icon?: React.ReactNode;
  title: string;
  lead?: string;
  children?: React.ReactNode;
}) {
  return (
    <div className="rm-state" data-tone={tone}>
      <div className="rm-state-icon" aria-hidden="true">
        {tone === "progress" ? <span className="rm-spinner" /> : null}
        {tone === "success" ? (
          <svg viewBox="0 0 52 52" className="rm-state-check">
            <circle cx="26" cy="26" r="24" />
            <path d="m15 27 7.5 7.5L37.5 19" />
          </svg>
        ) : null}
        {tone === "neutral" || tone === "danger" ? icon : null}
      </div>
      <h1>{title}</h1>
      {lead ? <p className="rm-lead">{lead}</p> : null}
      {children}
    </div>
  );
}

export function Notice({ tone, children }: { tone: "danger" | "info"; children: React.ReactNode }) {
  return (
    <p className="rm-notice" data-tone={tone} role={tone === "danger" ? "alert" : "status"}>
      {children}
    </p>
  );
}
