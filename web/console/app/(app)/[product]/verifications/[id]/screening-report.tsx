import type { ReactNode } from "react";
import { StatusBadge } from "@/brand";
import type { getT } from "../../../../../i18n";
import { countryLabel } from "../../../../../lib/countries";
import type { Verification } from "../../../../../lib/api";
import {
  categoryLabel,
  featureLabel,
  featureTone,
  outcomeLabel,
  outcomeTone,
} from "../../aml-labels";

type Translate = Awaited<ReturnType<typeof getT>>;
type Screening = NonNullable<Verification["screening"]>;
type Hit = NonNullable<Screening["hits"]>[number];

export function IdentityScreeningReport({
  screening,
  extracted,
  t,
  locale,
}: {
  screening: Screening;
  extracted?: Verification["extracted_identity"];
  t: Translate;
  locale: string;
}) {
  const outcome = screening.status === "failed" ? "failed" : screening.outcome ?? "failed";
  const verdict = verdictCopy(t, outcome, screening.failure_reason);
  const hits = screening.hits ?? [];
  const subject = screening.subject;
  const first = subject?.first_name || extracted?.first_name;
  const last = subject?.last_name || extracted?.last_name;
  const birth = subject?.date_of_birth || extracted?.birth_date;
  const nationality = subject?.nationality || extracted?.document_country;

  return (
    <section className="rm-vcase-card">
      <div className="rm-vcase-name">
        <h2>{t("console.verifications.amlScreening")}</h2>
        <StatusBadge label={outcomeLabel(t, outcome)} tone={outcomeTone(outcome)} />
      </div>
      <p className="rm-hint">{verdict.lead}</p>

      <div className="rm-vcase-grid">
        <div>
          <h3>{t("console.product.amlSubjectTitle")}</h3>
          <dl className="rm-vcase-list">
            <Field label={t("console.verifications.fields.firstName")}>{first || "—"}</Field>
            <Field label={t("console.verifications.fields.lastName")}>{last || "—"}</Field>
            {birth ? <Field label={t("console.product.amlBirth")}>{birth}</Field> : null}
            <Field label={t("console.product.amlNationality")}>{countryLabel(locale, nationality) || "—"}</Field>
            {subject?.id_number ? <Field label={t("console.verifications.documentNumberLabel")}>{subject.id_number}</Field> : null}
          </dl>
        </div>

        <div>
          <h3>{t("console.product.amlHitsTitle")}</h3>
          {hits.length > 0 ? <p className="rm-hint">{t("console.product.amlLeadingHit")}</p> : null}
          {hits.length === 0 ? (
            <p className="rm-vcase-empty">
              {outcome === "failed" ? t("console.product.amlVerdictFailed") : t("console.product.amlNoHits")}
            </p>
          ) : (
            <ul className="rm-vcase-checks">
              {hits.map((hit) => (
                <li key={hitKey(hit)}>
                  <span className="rm-vcase-check-dot" aria-hidden="true" />
                  <span className="rm-vcase-check-name">
                    {categoryLabel(t, hit.category)}
                    <small>
                      {t("console.product.amlScore").replace("{score}", Number(hit.score).toFixed(3))}
                      {hit.listed_name ? ` · ${hit.listed_name}` : ""}
                    </small>
                  </span>
                </li>
              ))}
            </ul>
          )}
        </div>
      </div>

      {hits.map((hit) => (
        <div key={hitKey(hit)}>
          <h3>{categoryLabel(t, hit.category)}</h3>
          <dl className="rm-vcase-list">
            <Field label={t("console.product.amlListed")}>{hit.listed_name || "—"}</Field>
            <Field label={t("console.product.amlMatched")}>{hit.matched_name || "—"}</Field>
            <Field label={t("console.product.amlScore").replace("{score}", "").trim()}>
              {Number(hit.score).toFixed(3)}
            </Field>
            {join(hit.aliases) ? <Field label={t("console.product.amlAliases")}>{join(hit.aliases)}</Field> : null}
            {join(hit.birth_dates) ? <Field label={t("console.product.amlBirthDates")}>{join(hit.birth_dates)}</Field> : null}
            {join(hit.countries) ? <Field label={t("console.product.amlCountries")}>{join(hit.countries)}</Field> : null}
            {join(hit.datasets) ? <Field label={t("console.product.amlDatasets")}>{join(hit.datasets)}</Field> : null}
          </dl>
          {hit.features && hit.features.length > 0 ? (
            <p className="rm-vcase-features">
              {hit.features.map((feature) => (
                <StatusBadge key={feature} label={featureLabel(t, feature)} tone={featureTone(feature)} />
              ))}
            </p>
          ) : null}
        </div>
      ))}

      <h3>{t("console.product.amlProviderResponse")}</h3>
      <pre className="rm-vcase-json">{JSON.stringify(screening, null, 2)}</pre>
    </section>
  );
}

function hitKey(hit: Hit) {
  return `${hit.entity_id ?? ""}-${hit.category}-${hit.listed_name}-${hit.score}`;
}

function join(values?: string[] | null) {
  const text = (values ?? []).filter(Boolean).join(", ");
  return text || null;
}

function Field({ label, children }: { label: string; children: ReactNode }) {
  return (
    <div>
      <dt>{label}</dt>
      <dd>{children}</dd>
    </div>
  );
}

function verdictCopy(t: Translate, outcome: string, failure?: string | null) {
  if (outcome === "clear") {
    return { title: t("console.product.amlVerdictClear"), lead: t("console.product.amlNoHits"), tone: "success" as const };
  }
  if (outcome === "potential_match") {
    return {
      title: t("console.product.amlVerdictPotential"),
      lead: t("console.product.amlHitsTitle"),
      tone: "warning" as const,
    };
  }
  if (outcome === "confirmed_match") {
    return {
      title: t("console.product.amlVerdictConfirmed"),
      lead: t("console.product.amlTruePositive"),
      tone: "danger" as const,
    };
  }
  return {
    title: t("console.product.amlVerdictFailed"),
    lead: failure || t("console.product.amlOutcomeFailed"),
    tone: "danger" as const,
  };
}
