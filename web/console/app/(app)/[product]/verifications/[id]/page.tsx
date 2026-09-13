import type { ReactNode } from "react";
import Link from "next/link";
import { notFound } from "next/navigation";
import { StatusBadge } from "@kyc/brand";
import { PageHeader } from "../../../../../components/page-header";
import { getLocale, getT } from "../../../../../i18n";
import { consoleApi, type MediaUrl, type Verification } from "../../../../../lib/api";
import { resolveEnvironment, withEnvironment } from "../../../../../lib/environment";
import { displayName } from "../../../../../lib/labels";
import { parseProduct } from "../../../../../lib/parse-product";
import { requireMe, sessionCookieHeader } from "../../../../../lib/session";
import {
  canCancelVerification,
  decisionTone,
  formatUtc,
  signalOutcomeTone,
  tDecision,
  tSandboxScenario,
  tSignalCode,
  tSignalOutcome,
  tVerificationStatus,
  verificationTone,
} from "../../../../../lib/status";
import { VerificationActions } from "./verification-actions";

export const dynamic = "force-dynamic";

const EXTRACTED_KEYS = [
  "first_name",
  "last_name",
  "birth_date",
  "document_type",
  "document_country",
  "document_number",
] as const;

export default async function VerificationDetailPage({
  params,
  searchParams,
}: {
  params: Promise<{ product: string; id: string }>;
  searchParams: Promise<{ env?: string }>;
}) {
  const resolved = await params;
  const productId = await parseProduct(Promise.resolve({ product: resolved.product }));
  const id = resolved.id;
  if (productId !== "identity") {
    notFound();
  }
  const env = resolveEnvironment((await searchParams).env);
  const [me, t, locale, result] = await Promise.all([
    requireMe(),
    getT(),
    getLocale(),
    consoleApi<Verification>(`/v1/console/verifications/${encodeURIComponent(id)}`, await sessionCookieHeader()),
  ]);
  if (!result.ok) {
    notFound();
  }
  const verification = result.data;
  const canWrite = me.permissions.includes("VERIFICATION_WRITE");
  const cookie = await sessionCookieHeader();
  const [document, selfie] = await Promise.all([
    mediaIfPresent(cookie, id, "document"),
    mediaIfPresent(cookie, id, "selfie"),
  ]);
  const title =
    displayName(
      verification.applicant?.first_name,
      verification.applicant?.last_name,
      verification.applicant?.email ?? "",
    ) || t("console.verifications.untitled");
  const extracted = verification.extracted_identity;
  const scenario = sandboxScenario(verification.metadata);
  const reasons = verification.decision_reasons ?? [];
  const signals = verification.signals ?? [];

  return (
    <main className="rm-idv">
      <p className="rm-back">
        <Link href={withEnvironment("/identity", env)}>{t("console.verifications.backToList")}</Link>
      </p>
      <PageHeader
        eyebrow={t("console.nav.idv")}
        title={title}
        lead={formatUtc(verification.created_at, locale)}
        actions={
          <StatusBadge label={tVerificationStatus(t, verification.status)} tone={verificationTone(verification.status)} />
        }
      />
      <VerificationActions
        id={verification.id}
        hostedUrl={verification.hosted_url}
        canWrite={canWrite}
        canCancel={canCancelVerification(verification.status)}
        canReview={verification.status === "review"}
      />
      <div className="rm-idv-fiche">
        <section className="rm-section">
          <h2>{t("console.verifications.session")}</h2>
          <div className="rm-details">
            <Detail label={t("console.verifications.id")}>
              <code>{verification.id}</code>
            </Detail>
            <Detail label={t("console.verifications.statusLabel")}>
              <StatusBadge label={tVerificationStatus(t, verification.status)} tone={verificationTone(verification.status)} />
            </Detail>
            {verification.applicant?.first_name ? (
              <Detail label={t("console.verifications.firstName")}>{verification.applicant.first_name}</Detail>
            ) : null}
            {verification.applicant?.last_name ? (
              <Detail label={t("console.verifications.lastName")}>{verification.applicant.last_name}</Detail>
            ) : null}
            {verification.applicant?.email ? (
              <Detail label={t("console.verifications.email")}>{verification.applicant.email}</Detail>
            ) : null}
            <Detail label={t("console.verifications.createdAt")}>{formatUtc(verification.created_at, locale)}</Detail>
            <Detail label={t("console.verifications.updatedAt")}>{formatUtc(verification.updated_at, locale)}</Detail>
            <Detail label={t("console.verifications.expires")}>{formatUtc(verification.expires_at, locale)}</Detail>
            {scenario ? (
              <Detail label={t("console.verifications.scenario")}>{tSandboxScenario(t, scenario)}</Detail>
            ) : null}
            <Detail label={t("console.verifications.hostedLink")}>
              {verification.hosted_url ? (
                <a className="rm-url" href={verification.hosted_url}>
                  {verification.hosted_url}
                </a>
              ) : (
                t("console.verifications.noLink")
              )}
            </Detail>
          </div>
        </section>
        {verification.decision || reasons.length > 0 ? (
          <section className="rm-section">
            <h2>{t("console.verifications.decision")}</h2>
            <div className="rm-details">
              {verification.decision ? (
                <Detail label={t("console.verifications.decision")}>
                  <StatusBadge label={tDecision(t, verification.decision)} tone={decisionTone(verification.decision)} />
                </Detail>
              ) : null}
              {reasons.length > 0 ? (
                <Detail label={t("console.verifications.reasons")}>
                  <ul className="rm-idv-plain">
                    {reasons.map((reason) => (
                      <li key={reason}>{tSignalCode(t, reason)}</li>
                    ))}
                  </ul>
                </Detail>
              ) : null}
            </div>
          </section>
        ) : null}
        {extracted ? (
          <section className="rm-section">
            <h2>{t("console.verifications.extracted")}</h2>
            <div className="rm-details">
              {EXTRACTED_KEYS.map((key) => {
                const value = extracted[key];
                if (!value) {
                  return null;
                }
                return (
                  <Detail key={key} label={extractedLabel(t, key)}>
                    {value}
                  </Detail>
                );
              })}
            </div>
          </section>
        ) : null}
        {signals.length > 0 ? (
          <section className="rm-section">
            <h2>{t("console.verifications.signals")}</h2>
            <div className="rm-table-wrap">
              <table className="rm-table">
                <thead>
                  <tr>
                    <th>{t("console.verifications.signal")}</th>
                    <th>{t("console.verifications.outcome")}</th>
                    <th>{t("console.verifications.score")}</th>
                  </tr>
                </thead>
                <tbody>
                  {signals.map((signal) => (
                    <tr key={signal.code}>
                      <td>{tSignalCode(t, signal.code)}</td>
                      <td>
                        <StatusBadge label={tSignalOutcome(t, signal.outcome)} tone={signalOutcomeTone(signal.outcome)} />
                      </td>
                      <td>{signal.score == null ? "—" : signal.score}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          </section>
        ) : null}
        {document || selfie ? (
          <section className="rm-section">
            <h2>{t("console.verifications.media")}</h2>
            <div className="rm-idv-media">
              {document ? (
                <figure>
                  <img src={document} alt={t("console.verifications.document")} />
                  <figcaption>{t("console.verifications.document")}</figcaption>
                </figure>
              ) : null}
              {selfie ? (
                <figure>
                  <img src={selfie} alt={t("console.verifications.selfie")} />
                  <figcaption>{t("console.verifications.selfie")}</figcaption>
                </figure>
              ) : null}
            </div>
          </section>
        ) : null}
      </div>
    </main>
  );
}

function Detail({ label, children }: { label: string; children: ReactNode }) {
  return (
    <div>
      <span>{label}</span>
      <strong>{children}</strong>
    </div>
  );
}

function extractedLabel(
  t: Awaited<ReturnType<typeof getT>>,
  key: (typeof EXTRACTED_KEYS)[number],
): string {
  switch (key) {
    case "first_name":
      return t("console.verifications.fields.firstName");
    case "last_name":
      return t("console.verifications.fields.lastName");
    case "birth_date":
      return t("console.verifications.fields.birthDate");
    case "document_type":
      return t("console.verifications.fields.documentType");
    case "document_country":
      return t("console.verifications.fields.documentCountry");
    case "document_number":
      return t("console.verifications.fields.documentNumber");
  }
}

function sandboxScenario(metadata?: Record<string, unknown> | null): string | null {
  const value = metadata?.sandbox_scenario;
  return typeof value === "string" && value.trim() ? value.trim() : null;
}

async function mediaIfPresent(cookie: string, id: string, kind: string): Promise<string | null> {
  const result = await consoleApi<MediaUrl>(
    `/v1/console/verifications/${encodeURIComponent(id)}/media/${encodeURIComponent(kind)}`,
    cookie,
  );
  return result.ok ? result.data.url : null;
}
