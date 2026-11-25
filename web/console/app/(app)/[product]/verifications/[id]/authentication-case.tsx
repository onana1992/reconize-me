import type { ReactNode } from "react";
import { notFound } from "next/navigation";
import { StatusBadge } from "@/brand";
import { BackLink } from "../../../../../components/back-link";
import { CopyButton } from "../../../../../components/copy-button";
import { getLocale, getT } from "../../../../../i18n";
import { consoleApi, type AuthenticationSession } from "../../../../../lib/api";
import { tIntegrationMode } from "../../../../../lib/environment";
import { sessionCookieHeader } from "../../../../../lib/session";
import {
  authenticationTone,
  formatUtc,
  signalOutcomeTone,
  tAuthenticationStatus,
  tSignalOutcome,
} from "../../../../../lib/status";
type Translate = Awaited<ReturnType<typeof getT>>;
type VerdictTone = "success" | "danger" | "warning" | "info" | "neutral";
type CheckOutcome = "pass" | "fail" | "unavailable" | "review";

type Check = {
  key: string;
  name: string;
  detail: string | null;
  outcome: CheckOutcome;
};

const FOLDED_REASONS = new Set([
  "liveness_pass",
  "liveness_fail",
  "presentation_attack",
  "face_match_pass",
  "face_match_fail",
  "enrollment_inactive",
  "attempt_limit",
  "analyst_approved",
  "analyst_declined",
]);

export async function AuthenticationCase({ id }: { id: string }) {
  const cookie = await sessionCookieHeader();
  const [t, locale, result] = await Promise.all([
    getT(),
    getLocale(),
    consoleApi<AuthenticationSession>(`/v1/console/authentications/${encodeURIComponent(id)}`, cookie),
  ]);
  if (!result.ok) {
    notFound();
  }
  const session = result.data;
  const mode = session.integration_mode ? tIntegrationMode(t, session.integration_mode) : null;
  const title = session.external_id?.trim() || t("console.authentications.untitled");
  const verdict = verdictFor(t, session.status);
  const checks = buildChecks(t, session);
  const passed = checks.filter((check) => check.outcome === "pass").length;
  const reasonLabels = reasonLine(t, session);
  const verdictLines = [
    reasonLabels.length > 0
      ? t("console.authentications.reasonsLine").replace("{reasons}", reasonLabels.join(", "))
      : null,
    checks.length > 0
      ? t("console.authentications.checksLine")
          .replace("{passed}", String(passed))
          .replace("{total}", String(checks.length))
      : null,
  ].filter((line): line is string => line != null);

  const subject = [
    { label: t("console.authentications.reference"), value: session.external_id?.trim() || "", mono: false },
    { label: t("console.authentications.enrollment"), value: session.enrollment_id?.trim() || "", mono: true },
  ].filter((row) => row.value);
  const duration = session.decided_at ? formatDuration(session.created_at, session.decided_at, t) : "—";
  const linkState = linkStateLabel(session.hosted_url, session.expires_at, t);
  const hasActions = Boolean(session.hosted_url);

  return (
    <main className="rm-idv rm-vcase">
      <BackLink href="/authentication">{t("console.authentications.backToList")}</BackLink>

      <header className="rm-vcase-head">
        <div className="rm-vcase-title">
          <p className="rm-eyebrow">{t("console.nav.biometric")}</p>
          <div className="rm-vcase-name">
            <h1>{title}</h1>
            <StatusBadge label={tAuthenticationStatus(t, session.status)} tone={authenticationTone(session.status)} />
          </div>
          <p className="rm-vcase-meta">
            <span>{t("console.authentications.createdOn").replace("{date}", formatUtc(session.created_at, locale))}</span>
            {mode ? <span>{t("console.authentications.modeLine").replace("{mode}", mode)}</span> : null}
          </p>
        </div>
        {hasActions ? (
          <div className="rm-idv-toolbar">
            <div className="rm-actions">
              {session.hosted_url ? (
                <>
                  <a className="rm-button" data-variant="secondary" href={session.hosted_url}>
                    {t("console.authentications.openLink")}
                  </a>
                  <CopyButton value={session.hosted_url} label={t("console.authentications.copyLink")} />
                </>
              ) : null}
            </div>
          </div>
        ) : null}
      </header>

      <section className="rm-vcase-verdict" data-tone={verdict.tone}>
        <div className="rm-vcase-verdict-icon" aria-hidden="true">
          <VerdictIcon tone={verdict.tone} />
        </div>
        <div className="rm-vcase-verdict-copy">
          <h2>{verdict.title}</h2>
          {verdictLines.length > 0 ? verdictLines.map((line) => <p key={line}>{line}</p>) : <p>{verdict.lead}</p>}
        </div>
      </section>

      <div className="rm-vcase-grid">
          <section className="rm-vcase-card">
            <h2>{t("console.authentications.subjectTitle")}</h2>
            {subject.length === 0 ? (
              <p className="rm-vcase-empty">{t("console.authentications.subjectEmpty")}</p>
            ) : (
              <dl className="rm-vcase-list">
                {subject.map((row) => (
                  <div key={row.label}>
                    <dt>{row.label}</dt>
                    <dd>{row.mono ? <code>{row.value}</code> : row.value}</dd>
                  </div>
                ))}
              </dl>
            )}
          </section>

          <section className="rm-vcase-card">
            <h2>{t("console.authentications.checksTitle")}</h2>
            {checks.length === 0 ? (
              <p className="rm-vcase-empty">{t("console.authentications.noChecks")}</p>
            ) : (
              <ul className="rm-vcase-checks">
                {checks.map((check) => (
                  <li key={check.key} data-outcome={check.outcome}>
                    <span className="rm-vcase-check-dot" aria-hidden="true" />
                    <span className="rm-vcase-check-name">
                      {check.name}
                      {check.detail ? <small>{check.detail}</small> : null}
                    </span>
                    <StatusBadge label={tSignalOutcome(t, check.outcome)} tone={signalOutcomeTone(check.outcome)} />
                  </li>
                ))}
              </ul>
            )}
          </section>
      </div>

      <section className="rm-vcase-card">
        <h2>{t("console.authentications.sessionDetails")}</h2>
        <div className="rm-vcase-session">
          <dl className="rm-vcase-list">
            <Field label={t("console.authentications.sessionId")}>
              <code title={session.id}>{session.id}</code>
            </Field>
            <Field label={t("console.authentications.environment")}>{mode ?? "—"}</Field>
            <Field label={t("console.authentications.decision")}>{decisionLabel(t, session.status)}</Field>
            <Field label={t("console.authentications.policy")}>{session.policy_version?.trim() || "—"}</Field>
          </dl>
          <dl className="rm-vcase-list">
            <Field label={t("console.authentications.createdAt")}>{formatUtc(session.created_at, locale)}</Field>
            <Field label={t("console.authentications.lastActivity")}>
              {session.decided_at ? formatUtc(session.decided_at, locale) : "—"}
            </Field>
            <Field label={t("console.authentications.duration")}>{duration}</Field>
            <Field label={t("console.authentications.linkState")}>{linkState}</Field>
            <Field label={t("console.authentications.expires")}>
              {session.expires_at ? formatUtc(session.expires_at, locale) : "—"}
            </Field>
          </dl>
        </div>
      </section>
    </main>
  );
}

function Field({ label, children }: { label: string; children: ReactNode }) {
  return (
    <div>
      <dt>{label}</dt>
      <dd>{children}</dd>
    </div>
  );
}

function VerdictIcon({ tone }: { tone: VerdictTone }) {
  if (tone === "success") {
    return (
      <svg viewBox="0 0 52 52" className="rm-vcase-check">
        <circle cx="26" cy="26" r="24" />
        <path d="m15 27 7.5 7.5L37.5 19" />
      </svg>
    );
  }
  if (tone === "danger") {
    return (
      <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round">
        <path d="M18 6 6 18M6 6l12 12" />
      </svg>
    );
  }
  if (tone === "warning") {
    return (
      <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
        <circle cx="12" cy="12" r="9" />
        <path d="M12 8v5M12 16h.01" />
      </svg>
    );
  }
  if (tone === "info") {
    return <span className="rm-vcase-spinner" />;
  }
  return (
    <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
      <circle cx="12" cy="12" r="9" />
      <path d="M12 7v5l3 2" />
    </svg>
  );
}

function verdictFor(t: Translate, status: string) {
  if (status === "approved") {
    return {
      tone: "success" as const,
      title: t("console.authentications.verdictApproved"),
      lead: t("console.authentications.verdictApprovedLead"),
    };
  }
  if (status === "declined") {
    return {
      tone: "danger" as const,
      title: t("console.authentications.verdictDeclined"),
      lead: t("console.authentications.verdictDeclinedLead"),
    };
  }
  if (status === "failed") {
    return {
      tone: "danger" as const,
      title: t("console.authentications.verdictFailed"),
      lead: t("console.authentications.verdictFailedLead"),
    };
  }
  if (status === "review") {
    return {
      tone: "danger" as const,
      title: t("console.authentications.verdictDeclined"),
      lead: t("console.authentications.verdictDeclinedLead"),
    };
  }
  if (status === "recapture_requested") {
    return {
      tone: "warning" as const,
      title: t("console.authentications.verdictRecapture"),
      lead: t("console.authentications.verdictRecaptureLead"),
    };
  }
  if (status === "expired") {
    return {
      tone: "neutral" as const,
      title: t("console.authentications.verdictClosed"),
      lead: t("console.authentications.verdictExpiredLead"),
    };
  }
  if (status === "cancelled") {
    return {
      tone: "neutral" as const,
      title: t("console.authentications.verdictClosed"),
      lead: t("console.authentications.verdictCancelledLead"),
    };
  }
  if (status === "processing") {
    return {
      tone: "info" as const,
      title: t("console.authentications.verdictProcessing"),
      lead: t("console.authentications.verdictProcessingLead"),
    };
  }
  return {
    tone: "info" as const,
    title: t("console.authentications.verdictPending"),
    lead: t("console.authentications.verdictPendingLead"),
  };
}

function buildChecks(t: Translate, session: AuthenticationSession): Check[] {
  const reasons = session.reasons ?? [];
  const has = (code: string) => reasons.includes(code);
  const checks: Check[] = [];
  const score =
    session.match_score == null
      ? null
      : t("console.authentications.scoreLine").replace("{score}", formatScore(session.match_score));
  const livenessFailed = has("presentation_attack") || has("liveness_fail") || has("attempt_limit");
  const livenessPassed =
    !livenessFailed &&
    (has("liveness_pass") || has("face_match_pass") || has("face_match_fail") || has("enrollment_inactive"));

  if (livenessFailed || livenessPassed) {
    checks.push({
      key: "liveness",
      name: t("console.authentications.checkNames.liveness"),
      detail: null,
      outcome: livenessFailed ? "fail" : "pass",
    });
  }
  if (has("face_match_pass")) {
    checks.push({
      key: "face",
      name: t("console.authentications.checkNames.faceMatch"),
      detail: score,
      outcome: "pass",
    });
  } else if (has("face_match_fail") || (session.match_score != null && decidedStatus(session.status))) {
    checks.push({
      key: "face",
      name: t("console.authentications.checkNames.faceMatch"),
      detail: score,
      outcome: faceOutcome(session.status),
    });
  }
  if (has("enrollment_inactive")) {
    checks.push({
      key: "enrollment",
      name: t("console.authentications.checkNames.enrollment"),
      detail: null,
      outcome: "fail",
    });
  }
  if (has("attempt_limit")) {
    checks.push({
      key: "attempts",
      name: t("console.authentications.checkNames.attempts"),
      detail: null,
      outcome: "fail",
    });
  }
  if (session.failure_reason) {
    checks.push({
      key: "provider",
      name: t("console.authentications.checkNames.provider"),
      detail: tAuthReason(t, session.failure_reason),
      outcome: "unavailable",
    });
  }
  if (has("analyst_approved") || has("analyst_declined")) {
    checks.push({
      key: "analyst",
      name: t("console.authentications.checkNames.analyst"),
      detail: null,
      outcome: has("analyst_approved") ? "pass" : "fail",
    });
  }
  for (const code of reasons) {
    if (FOLDED_REASONS.has(code)) {
      continue;
    }
    checks.push({
      key: code,
      name: tAuthReason(t, code),
      detail: null,
      outcome: unknownOutcome(session.status),
    });
  }
  return checks.sort((a, b) => outcomeRank(a.outcome) - outcomeRank(b.outcome));
}

function reasonLine(t: Translate, session: AuthenticationSession): string[] {
  const reasons = session.reasons ?? [];
  const labels = reasons.map((code) => tAuthReason(t, code));
  if (session.failure_reason && !reasons.includes(session.failure_reason)) {
    labels.push(tAuthReason(t, session.failure_reason));
  }
  return labels;
}

function tAuthReason(t: Translate, code: string): string {
  switch (code) {
    case "liveness_pass":
      return t("console.authentications.reasonCodes.livenessPass");
    case "liveness_fail":
      return t("console.authentications.reasonCodes.livenessFail");
    case "face_match_pass":
      return t("console.authentications.reasonCodes.faceMatchPass");
    case "face_match_fail":
      return t("console.authentications.reasonCodes.faceMatchFail");
    case "presentation_attack":
      return t("console.authentications.reasonCodes.presentationAttack");
    case "enrollment_inactive":
      return t("console.authentications.reasonCodes.enrollmentInactive");
    case "attempt_limit":
      return t("console.authentications.reasonCodes.attemptLimit");
    case "analyst_approved":
      return t("console.authentications.reasonCodes.analystApproved");
    case "analyst_declined":
      return t("console.authentications.reasonCodes.analystDeclined");
    case "provider_unavailable":
      return t("console.authentications.reasonCodes.providerUnavailable");
    case "provider_timeout":
      return t("console.authentications.reasonCodes.providerTimeout");
    case "provider_invalid_response":
      return t("console.authentications.reasonCodes.providerInvalidResponse");
    default:
      return code;
  }
}

function decisionLabel(t: Translate, status: string): string {
  if (decidedStatus(status)) {
    return tAuthenticationStatus(t, status);
  }
  return t("console.authentications.decisionPending");
}

function decidedStatus(status: string): boolean {
  return status === "approved" || status === "declined" || status === "review" || status === "failed";
}

function faceOutcome(status: string): CheckOutcome {
  if (status === "approved") return "pass";
  return "fail";
}

function unknownOutcome(status: string): CheckOutcome {
  if (status === "approved") return "pass";
  if (status === "declined" || status === "failed" || status === "review") return "fail";
  return "unavailable";
}

function outcomeRank(outcome: string): number {
  if (outcome === "fail") return 0;
  if (outcome === "unavailable" || outcome === "review") return 1;
  return 2;
}

function formatScore(score: number): string {
  return score.toFixed(3);
}

function formatDuration(createdAt: string, decidedAt: string, t: Translate): string {
  const start = new Date(createdAt).getTime();
  const end = new Date(decidedAt).getTime();
  if (Number.isNaN(start) || Number.isNaN(end) || end < start) {
    return "—";
  }
  const minutes = Math.round((end - start) / 60_000);
  if (minutes < 1) {
    return t("console.authentications.durationSeconds");
  }
  if (minutes < 60) {
    return t("console.authentications.durationMinutes").replace("{n}", String(minutes));
  }
  const hours = Math.round(minutes / 60);
  if (hours < 48) {
    return t("console.authentications.durationHours").replace("{n}", String(hours));
  }
  return t("console.authentications.durationDays").replace("{n}", String(Math.round(hours / 24)));
}

function linkStateLabel(hostedUrl: string | null | undefined, expiresAt: string | null | undefined, t: Translate): string {
  if (hostedUrl) {
    return t("console.authentications.linkOpen");
  }
  const expires = expiresAt ? new Date(expiresAt).getTime() : Number.NaN;
  if (!Number.isNaN(expires) && expires <= Date.now()) {
    return t("console.authentications.linkExpired");
  }
  return t("console.authentications.linkClosed");
}
