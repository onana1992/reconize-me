import type { ReactNode } from "react";
import { notFound } from "next/navigation";
import { StatusBadge } from "@/brand";
import { BackLink } from "../../../components/back-link";
import { getLocale, getT } from "../../../i18n";
import { consoleApi, type Screening } from "../../../lib/api";
import { countryLabel } from "../../../lib/countries";
import { requireMe, sessionCookieHeader } from "../../../lib/session";
import { formatUtc } from "../../../lib/status";
import {
  categoryLabel,
  dispositionLabel,
  dispositionTone,
  featureLabel,
  featureTone,
  outcomeLabel,
  outcomeTone,
  reasonLabel,
} from "./aml-labels";
import { AmlQualifyForm } from "./aml-qualify";

export async function AmlCase({ id }: { id: string }) {
  const cookie = await sessionCookieHeader();
  const [me, t, locale, result] = await Promise.all([
    requireMe(),
    getT(),
    getLocale(),
    consoleApi<Screening>(`/v1/console/screenings/${encodeURIComponent(id)}`, cookie),
  ]);
  if (!result.ok) {
    notFound();
  }
  const screening = result.data;
  const canWrite = me.permissions.includes("VERIFICATION_WRITE");
  const name =
    [screening.subject?.first_name, screening.subject?.middle_name, screening.subject?.last_name]
      .filter(Boolean)
      .join(" ") || t("console.verifications.untitled");
  const outcome = screening.status === "failed" ? "failed" : screening.outcome ?? screening.status;
  const verdict = verdictCopy(t, outcome, screening.failure_reason);
  const hits = screening.hits ?? [];
  const birth = screening.subject?.date_of_birth;

  return (
    <main className="rm-idv rm-vcase">
      <BackLink href="/aml">{t("console.product.amlBack")}</BackLink>
      <header className="rm-vcase-head">
        <div className="rm-vcase-title">
          <p className="rm-eyebrow">{t("console.nav.aml")}</p>
          <div className="rm-vcase-name">
            <h1>{name}</h1>
            <StatusBadge label={outcomeLabel(t, outcome)} tone={outcomeTone(outcome)} />
          </div>
          <p className="rm-vcase-meta">
            <span>{t("console.verifications.createdOn").replace("{date}", formatUtc(screening.created_at, locale))}</span>
            {screening.engine ? <span>{screening.engine}</span> : null}
            {screening.policy_version ? <span>{screening.policy_version}</span> : null}
          </p>
        </div>
      </header>

      <section className="rm-vcase-verdict" data-tone={verdict.tone}>
        <div className="rm-vcase-verdict-copy">
          <h2>{verdict.title}</h2>
          <p>{verdict.lead}</p>
        </div>
      </section>

      <div className="rm-vcase-grid">
        <section className="rm-vcase-card">
          <h2>{t("console.product.amlSubjectTitle")}</h2>
          <dl className="rm-vcase-list">
            <Field label={t("console.verifications.fields.firstName")}>{screening.subject?.first_name || "—"}</Field>
            <Field label={t("console.verifications.fields.lastName")}>{screening.subject?.last_name || "—"}</Field>
            {birth ? <Field label={t("console.product.amlBirth")}>{birth}</Field> : null}
            <Field label={t("console.product.amlNationality")}>
              {countryLabel(locale, screening.subject?.nationality) || "—"}
            </Field>
          </dl>
        </section>

        <section className="rm-vcase-card">
          <h2>{t("console.product.amlHitsTitle")}</h2>
          {hits.length > 0 ? <p className="rm-hint">{t("console.product.amlLeadingHit")}</p> : null}
          {hits.length === 0 ? (
            <p className="rm-vcase-empty">{t("console.product.amlNoHits")}</p>
          ) : (
            <ul className="rm-vcase-checks">
              {hits.map((hit) => (
                <li key={hit.id} data-outcome={hit.disposition === "unreviewed" ? "unavailable" : "pass"}>
                  <span className="rm-vcase-check-dot" aria-hidden="true" />
                  <span className="rm-vcase-check-name">
                    {categoryLabel(t, hit.category)}
                    <small>
                      {t("console.product.amlScore").replace("{score}", Number(hit.score).toFixed(3))}
                      {hit.listed_name ? ` · ${hit.listed_name}` : ""}
                    </small>
                  </span>
                  <StatusBadge label={dispositionLabel(t, hit.disposition)} tone={dispositionTone(hit.disposition)} />
                </li>
              ))}
            </ul>
          )}
        </section>
      </div>

      {hits.map((hit) => (
        <section key={hit.id} className="rm-vcase-card">
          <h2>{categoryLabel(t, hit.category)}</h2>
          <dl className="rm-vcase-list">
            <Field label={t("console.product.amlListed")}>{hit.listed_name || "—"}</Field>
            <Field label={t("console.product.amlOutcome")}>{dispositionLabel(t, hit.disposition)}</Field>
            {hit.reason ? <Field label={t("console.product.amlReason")}>{reasonLabel(t, hit.reason)}</Field> : null}
          </dl>
          {hit.prior_disposition ? (
            <p className="rm-hint">
              {t("console.product.amlPrior").replace("{value}", dispositionLabel(t, hit.prior_disposition))}
            </p>
          ) : null}
          {hit.features && hit.features.length > 0 ? (
            <p className="rm-vcase-features">
              {hit.features.map((feature) => (
                <StatusBadge key={feature} label={featureLabel(t, feature)} tone={featureTone(feature)} />
              ))}
            </p>
          ) : null}
          {canWrite && hit.disposition === "unreviewed" ? (
            <AmlQualifyForm screeningId={screening.id} hitId={hit.id} />
          ) : null}
        </section>
      ))}

      <section className="rm-vcase-card">
        <h2>{t("console.product.amlProviderResponse")}</h2>
        <pre className="rm-vcase-json">{apiResponse(screening)}</pre>
      </section>

      <section className="rm-vcase-card">
        <h2>{t("console.product.amlSessionTitle")}</h2>
        <dl className="rm-vcase-list">
          <Field label={t("console.verifications.sessionId")}>
            <code title={screening.id}>{screening.id}</code>
          </Field>
          <Field label={t("console.product.amlEngine")}>{screening.engine ?? "—"}</Field>
          <Field label={t("console.product.amlPolicyVersion")}>{screening.policy_version ?? "—"}</Field>
          {screening.external_id ? <Field label={t("console.verifications.externalId")}>{screening.external_id}</Field> : null}
          {screening.failure_reason ? <Field label={t("console.product.amlOutcomeFailed")}>{screening.failure_reason}</Field> : null}
        </dl>
      </section>
    </main>
  );
}

function apiResponse(screening: Screening): string {
  const { provider_response: _provider, ...response } = screening;
  return JSON.stringify(response, null, 2);
}

function Field({ label, children }: { label: string; children: ReactNode }) {
  return (
    <div>
      <dt>{label}</dt>
      <dd>{children}</dd>
    </div>
  );
}

function verdictCopy(
  t: Awaited<ReturnType<typeof getT>>,
  outcome: string,
  failure?: string | null,
): { title: string; lead: string; tone: "success" | "warning" | "danger" | "neutral" } {
  if (outcome === "clear") {
    return { title: t("console.product.amlVerdictClear"), lead: t("console.product.amlNoHits"), tone: "success" };
  }
  if (outcome === "potential_match") {
    return {
      title: t("console.product.amlVerdictPotential"),
      lead: t("console.product.amlHitsTitle"),
      tone: "warning",
    };
  }
  if (outcome === "confirmed_match") {
    return {
      title: t("console.product.amlVerdictConfirmed"),
      lead: t("console.product.amlTruePositive"),
      tone: "danger",
    };
  }
  return {
    title: t("console.product.amlVerdictFailed"),
    lead: failure || t("console.product.amlOutcomeFailed"),
    tone: "danger",
  };
}

