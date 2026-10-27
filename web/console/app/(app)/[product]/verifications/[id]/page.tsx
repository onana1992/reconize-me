import type { ReactNode } from "react";
import { notFound } from "next/navigation";
import { StatusBadge } from "@/brand";
import { BackLink } from "../../../../../components/back-link";
import { getLocale, getT } from "../../../../../i18n";
import { consoleApi, type MediaUrl, type Verification } from "../../../../../lib/api";
import { tIntegrationMode } from "../../../../../lib/environment";
import { displayName } from "../../../../../lib/labels";
import { parseProduct } from "../../../../../lib/parse-product";
import { requireMe, sessionCookieHeader } from "../../../../../lib/session";
import {
  canCancelVerification,
  formatUtc,
  signalOutcomeTone,
  tDecision,
  tSignalCode,
  tDocumentCode,
  tDocumentType,
  tSignalOutcome,
  tVerificationStatus,
  verificationTone,
} from "../../../../../lib/status";
import { categoryLabel, featureLabel, featureTone, outcomeLabel, outcomeTone } from "../../aml-labels";
import { AmlCase } from "../../aml-case";
import { VerificationActions } from "./verification-actions";

export const dynamic = "force-dynamic";

type Translate = Awaited<ReturnType<typeof getT>>;
type VerdictTone = "success" | "danger" | "warning" | "info" | "neutral";

export default async function VerificationDetailPage({
  params,
}: {
  params: Promise<{ product: string; id: string }>;
}) {
  const resolved = await params;
  const productId = await parseProduct(Promise.resolve({ product: resolved.product }));
  const id = resolved.id;
  if (productId === "aml") {
    return <AmlCase id={id} />;
  }
  if (productId !== "identity") {
    notFound();
  }
  const cookie = await sessionCookieHeader();
  const [me, t, locale, result] = await Promise.all([
    requireMe(),
    getT(),
    getLocale(),
    consoleApi<Verification>(`/v1/console/verifications/${encodeURIComponent(id)}`, cookie),
  ]);
  if (!result.ok) {
    notFound();
  }
  const verification = result.data;
  const canWrite = me.permissions.includes("VERIFICATION_WRITE");
  const [document, documentBack, selfie] = await Promise.all([
    mediaIfPresent(cookie, id, "document"),
    mediaIfPresent(cookie, id, "document_back"),
    mediaIfPresent(cookie, id, "selfie"),
  ]);

  const applicant = verification.applicant;
  const extracted = verification.extracted_identity;
  const title =
    displayName(applicant?.first_name, applicant?.last_name, applicant?.email ?? "") ||
    t("console.verifications.untitled");
  const reasons = verification.decision_reasons ?? [];
  const signals = [...(verification.signals ?? [])].sort((a, b) => outcomeRank(a.outcome) - outcomeRank(b.outcome));
  const passed = signals.filter((signal) => signal.outcome === "pass").length;
  const verdict = verdictFor(t, verification.status, verification.decision);
  const verdictLines = [
    reasons.length > 0
      ? t("console.verifications.reasonsLine").replace(
          "{reasons}",
          reasons.map((reason) => tSignalCode(t, reason)).join(", "),
        )
      : null,
    signals.length > 0
      ? t("console.verifications.checksLine")
          .replace("{passed}", String(passed))
          .replace("{total}", String(signals.length))
      : null,
  ].filter((line): line is string => line != null);

  const media = [
    document ? { url: document, label: t("console.verifications.documentFront") } : null,
    documentBack ? { url: documentBack, label: t("console.verifications.documentBack") } : null,
    selfie ? { url: selfie, label: t("console.verifications.selfie") } : null,
  ].filter((item): item is { url: string; label: string } => item != null);
  const captureLabels = [
    document ? t("console.verifications.captureFront") : null,
    documentBack ? t("console.verifications.captureBack") : null,
    selfie ? t("console.verifications.captureSelfie") : null,
  ].filter((label): label is string => label != null);
  const duration = formatDuration(verification.created_at, verification.updated_at, t);
  const linkState = linkStateLabel(verification.hosted_url, verification.expires_at, t);

  const names = [
    {
      label: t("console.verifications.fields.firstName"),
      declared: clean(applicant?.first_name),
      found: clean(extracted?.first_name),
    },
    {
      label: t("console.verifications.fields.lastName"),
      declared: clean(applicant?.last_name),
      found: clean(extracted?.last_name),
    },
  ].filter((row) => row.declared || row.found);
  const documentCode = clean(extracted?.document_code);
  const documentType = clean(extracted?.document_type);
  const documentName = documentCode ? tDocumentCode(t, documentCode) : documentType ? tDocumentType(t, documentType) : "";
  const documentFields = [
    { label: t("console.verifications.birthDateLabel"), value: clean(extracted?.birth_date) },
    { label: t("console.verifications.documentTypeLabel"), value: documentName },
    { label: t("console.verifications.issuingCountry"), value: clean(extracted?.document_country) },
    {
      label: t("console.verifications.documentNumberLabel"),
      value: clean(extracted?.document_number),
      mono: true,
    },
  ].filter((row) => row.value);
  const hasIdentity = names.length > 0 || documentFields.length > 0;
  const mode = verification.integration_mode ? tIntegrationMode(t, verification.integration_mode) : null;

  return (
    <main className="rm-idv rm-vcase">
      <BackLink href="/identity">{t("console.verifications.backToList")}</BackLink>

      <header className="rm-vcase-head">
        <div className="rm-vcase-title">
          <p className="rm-eyebrow">{t("console.nav.idv")}</p>
          <div className="rm-vcase-name">
            <h1>{title}</h1>
            <StatusBadge label={tVerificationStatus(t, verification.status)} tone={verificationTone(verification.status)} />
          </div>
          <p className="rm-vcase-meta">
            {applicant?.email ? <span>{applicant.email}</span> : null}
            <span>{t("console.verifications.createdOn").replace("{date}", formatUtc(verification.created_at, locale))}</span>
            {mode ? <span>{t("console.verifications.modeLine").replace("{mode}", mode)}</span> : null}
          </p>
        </div>
        <VerificationActions
          id={verification.id}
          hostedUrl={verification.hosted_url}
          canWrite={canWrite}
          canCancel={canCancelVerification(verification.status)}
          canReview={verification.status === "review"}
        />
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
          <h2>{t("console.verifications.capturesTitle")}</h2>
          {media.length === 0 ? (
            <p className="rm-vcase-empty">{t("console.verifications.noMedia")}</p>
          ) : (
            <div className="rm-vcase-gallery" data-count={media.length}>
              {media.map((item) => (
                <figure key={item.label}>
                  <a href={item.url} target="_blank" rel="noreferrer" title={t("console.verifications.openFull")}>
                    <span className="rm-vcase-stage">
                      <img src={item.url} alt={item.label} />
                    </span>
                  </a>
                  <figcaption>{item.label}</figcaption>
                </figure>
              ))}
            </div>
          )}
        </section>

        <div className="rm-vcase-side">
          <section className="rm-vcase-card">
            <h2>{t("console.verifications.identityTitle")}</h2>
            {!hasIdentity ? (
              <p className="rm-vcase-empty">{t("console.verifications.noIdentity")}</p>
            ) : (
              <>
                {names.length > 0 ? (
                  <div className="rm-vcase-compare">
                    <div className="rm-vcase-compare-head">
                      <span />
                      <span>{t("console.verifications.declaredBy")}</span>
                      <span>{t("console.verifications.readOnDocument")}</span>
                    </div>
                    {names.map((row) => {
                      const different = Boolean(
                        row.declared && row.found && normalize(row.declared) !== normalize(row.found),
                      );
                      return (
                        <div key={row.label} className="rm-vcase-compare-row" data-different={different ? "true" : undefined}>
                          <span>{row.label}</span>
                          <span>
                            {row.declared || (
                              <em className="rm-vcase-missing">{t("console.verifications.notProvided")}</em>
                            )}
                          </span>
                          <span>
                            {row.found || <em className="rm-vcase-missing">{t("console.verifications.notProvided")}</em>}
                            {different ? <strong className="rm-vcase-flag">{t("console.verifications.different")}</strong> : null}
                          </span>
                        </div>
                      );
                    })}
                  </div>
                ) : null}
                {documentFields.length > 0 ? (
                  <dl className="rm-vcase-list">
                    {documentFields.map((row) => (
                      <div key={row.label}>
                        <dt>{row.label}</dt>
                        <dd>{row.mono ? <code>{row.value}</code> : row.value}</dd>
                      </div>
                    ))}
                  </dl>
                ) : null}
              </>
            )}
          </section>

          <section className="rm-vcase-card">
            <h2>{t("console.verifications.checksTitle")}</h2>
            {signals.length === 0 ? (
              <p className="rm-vcase-empty">{t("console.verifications.noChecks")}</p>
            ) : (
              <ul className="rm-vcase-checks">
                {signals.map((signal) => (
                  <li key={signal.code} data-outcome={signal.outcome}>
                    <span className="rm-vcase-check-dot" aria-hidden="true" />
                    <span className="rm-vcase-check-name">
                      {checkName(t, signal.code)}
                      {signal.score == null ? null : (
                        <small>{t("console.verifications.scoreLine").replace("{score}", formatScore(signal.score))}</small>
                      )}
                    </span>
                    <StatusBadge label={tSignalOutcome(t, signal.outcome)} tone={signalOutcomeTone(signal.outcome)} />
                  </li>
                ))}
              </ul>
            )}
          </section>
        </div>
      </div>

      {verification.screening ? (
        <section className="rm-vcase-card">
          <div className="rm-vcase-name">
            <h2>{t("console.verifications.amlScreening")}</h2>
            <StatusBadge
              label={outcomeLabel(t, verification.screening.status === "failed" ? "failed" : verification.screening.outcome ?? "failed")}
              tone={outcomeTone(verification.screening.status === "failed" ? "failed" : verification.screening.outcome ?? "failed")}
            />
          </div>
          {(verification.screening.hits ?? []).length === 0 ? (
            verification.screening.status === "failed" ? (
              <p className="rm-vcase-empty">{t("console.product.amlVerdictFailed")}</p>
            ) : verification.screening.outcome === "clear" ? null : (
              <p className="rm-vcase-empty">{t("console.product.amlNoHits")}</p>
            )
          ) : (
            <ul className="rm-vcase-checks">
              {(verification.screening.hits ?? []).map((hit) => (
                <li key={`${hit.category}-${hit.listed_name}-${hit.score}`}>
                  <span className="rm-vcase-check-dot" aria-hidden="true" />
                  <span className="rm-vcase-check-name">
                    {categoryLabel(t, hit.category)}
                    <small>
                      {t("console.product.amlScore").replace("{score}", Number(hit.score).toFixed(3))}
                      {hit.listed_name ? ` · ${hit.listed_name}` : ""}
                      {hit.matched_name && hit.matched_name !== hit.listed_name ? ` · ${hit.matched_name}` : ""}
                    </small>
                  </span>
                  {hit.features && hit.features.length > 0 ? (
                    <span className="rm-vcase-features">
                      {hit.features.map((feature) => (
                        <StatusBadge key={feature} label={featureLabel(t, feature)} tone={featureTone(feature)} />
                      ))}
                    </span>
                  ) : null}
                </li>
              ))}
            </ul>
          )}
        </section>
      ) : null}

      <section className="rm-vcase-card">
        <h2>{t("console.verifications.sessionDetails")}</h2>
        <div className="rm-vcase-session">
          <dl className="rm-vcase-list">
            <Field label={t("console.verifications.sessionId")}>
              <code title={verification.id}>{verification.id}</code>
            </Field>
            <Field label={t("console.verifications.environment")}>{mode ?? "—"}</Field>
            <Field label={t("console.verifications.decision")}>
              {verification.decision ? tDecision(t, verification.decision) : t("console.verifications.decisionPending")}
            </Field>
            <Field label={t("console.verifications.rulesVersion")}>
              {verification.rules_version?.trim() || t("console.verifications.rulesVersionNone")}
            </Field>
            <Field label={t("console.verifications.policyVersion")}>
              {verification.policy_version != null
                ? String(verification.policy_version)
                : t("console.verifications.policyVersionNone")}
            </Field>
          </dl>
          <dl className="rm-vcase-list">
            <Field label={t("console.verifications.createdAt")}>{formatUtc(verification.created_at, locale)}</Field>
            <Field label={t("console.verifications.lastActivity")}>{formatUtc(verification.updated_at, locale)}</Field>
            <Field label={t("console.verifications.duration")}>{duration}</Field>
            <Field label={t("console.verifications.capturesReceived")}>
              {captureLabels.length > 0 ? captureLabels.join(" · ") : t("console.verifications.capturesNone")}
            </Field>
            <Field label={t("console.verifications.linkState")}>{linkState}</Field>
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

function verdictFor(t: Translate, status: string, decision?: string | null) {
  if (decision === "approved" || status === "approved") {
    return {
      tone: "success" as const,
      title: t("console.verifications.verdictApproved"),
      lead: t("console.verifications.verdictApprovedLead"),
    };
  }
  if (decision === "declined" || status === "declined") {
    return {
      tone: "danger" as const,
      title: t("console.verifications.verdictDeclined"),
      lead: t("console.verifications.verdictDeclinedLead"),
    };
  }
  if (status === "review") {
    return {
      tone: "warning" as const,
      title: t("console.verifications.verdictReview"),
      lead: t("console.verifications.verdictReviewLead"),
    };
  }
  if (status === "expired") {
    return {
      tone: "neutral" as const,
      title: t("console.verifications.verdictClosed"),
      lead: t("console.verifications.verdictExpiredLead"),
    };
  }
  if (status === "cancelled") {
    return {
      tone: "neutral" as const,
      title: t("console.verifications.verdictClosed"),
      lead: t("console.verifications.verdictCancelledLead"),
    };
  }
  if (status === "processing") {
    return {
      tone: "info" as const,
      title: t("console.verifications.verdictProcessing"),
      lead: t("console.verifications.verdictProcessingLead"),
    };
  }
  return {
    tone: "info" as const,
    title: t("console.verifications.verdictPending"),
    lead: t("console.verifications.verdictPendingLead"),
  };
}

function checkName(t: Translate, code: string): string {
  switch (code) {
    case "authenticity_stub_pass":
      return t("console.verifications.checkNames.authenticity");
    case "liveness_pass":
    case "liveness_fail":
      return t("console.verifications.checkNames.liveness");
    case "face_match_pass":
    case "face_match_fail":
      return t("console.verifications.checkNames.faceMatch");
    case "unsupported_document":
      return t("console.verifications.checkNames.documentSupported");
    case "document_expired":
      return t("console.verifications.checkNames.documentValidity");
    case "mrz_unavailable":
      return t("console.verifications.checkNames.mrz");
    default:
      return tSignalCode(t, code);
  }
}

function clean(value?: string | null): string {
  return value?.trim() || "";
}

function normalize(value: string): string {
  return value
    .normalize("NFD")
    .replace(/\p{M}/gu, "")
    .toLowerCase()
    .replace(/\s+/g, " ")
    .trim();
}

function outcomeRank(outcome: string): number {
  if (outcome === "fail") return 0;
  if (outcome === "unavailable") return 1;
  return 2;
}

function formatScore(score: number): string {
  return Number.isInteger(score) ? String(score) : score.toFixed(2);
}

function formatDuration(createdAt: string, updatedAt: string, t: Translate): string {
  const start = new Date(createdAt).getTime();
  const end = new Date(updatedAt).getTime();
  if (Number.isNaN(start) || Number.isNaN(end) || end < start) {
    return "—";
  }
  const minutes = Math.round((end - start) / 60_000);
  if (minutes < 1) {
    return t("console.verifications.durationSeconds");
  }
  if (minutes < 60) {
    return t("console.verifications.durationMinutes").replace("{n}", String(minutes));
  }
  const hours = Math.round(minutes / 60);
  if (hours < 48) {
    return t("console.verifications.durationHours").replace("{n}", String(hours));
  }
  return t("console.verifications.durationDays").replace("{n}", String(Math.round(hours / 24)));
}

function linkStateLabel(hostedUrl: string | null | undefined, expiresAt: string, t: Translate): string {
  if (hostedUrl) {
    return t("console.verifications.linkOpen");
  }
  const expires = new Date(expiresAt).getTime();
  if (!Number.isNaN(expires) && expires <= Date.now()) {
    return t("console.verifications.linkExpired");
  }
  return t("console.verifications.linkClosed");
}

async function mediaIfPresent(cookie: string, id: string, kind: string): Promise<string | null> {
  const result = await consoleApi<MediaUrl>(
    `/v1/console/verifications/${encodeURIComponent(id)}/media/${encodeURIComponent(kind)}`,
    cookie,
  );
  return result.ok ? result.data.url : null;
}
