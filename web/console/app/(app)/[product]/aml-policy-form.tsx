"use client";

import { StatusBadge } from "@/brand";
import { useRouter } from "next/navigation";
import { useMemo, useState, type FormEvent } from "react";
import { useT } from "../../../i18n/client";
import type { AmlPolicy } from "../../../lib/api";
import { formatUtc } from "../../../lib/status";
import { publishAmlPolicyAction } from "./aml-actions";
import { categoryLabel } from "./aml-labels";

const CATEGORIES = ["sanction", "debarment", "crime", "pep", "rca"] as const;

type Draft = {
  review: string;
  confirm: string;
  categories: string[];
};

export function AmlPolicyForm({
  policy,
  versions,
  canWrite,
  locale,
}: {
  policy: AmlPolicy;
  versions: AmlPolicy[];
  canWrite: boolean;
  locale: string;
}) {
  const t = useT();
  const router = useRouter();
  const initial = useMemo(() => draftOf(policy), [policy]);
  const [draft, setDraft] = useState<Draft>(initial);
  const [error, setError] = useState<string | null>(null);
  const [notice, setNotice] = useState<string | null>(null);
  const [pending, setPending] = useState(false);
  const dirty = JSON.stringify(draft) !== JSON.stringify(initial);
  const canPublish = canWrite && (!policy.published || dirty);
  const badge = policy.published
    ? t("console.rules.badgeVersion").replace("{version}", policy.version.replace("aml-policy-", ""))
    : t("console.rules.badgePlatform");
  const published = policy.published
    ? t("console.rules.sourceOrganization")
        .replace("{version}", policy.version.replace("aml-policy-", ""))
        .replace("{date}", policy.published_at ? formatUtc(policy.published_at, locale) : "—")
        .replace("{name}", "—")
    : t("console.rules.sourcePlatform");

  function toggleCategory(category: string) {
    setDraft((current) => {
      const has = current.categories.includes(category);
      const categories = has
        ? current.categories.filter((item) => item !== category)
        : [...current.categories, category];
      if (!categories.includes("sanction")) {
        categories.unshift("sanction");
      }
      return { ...current, categories };
    });
  }

  async function onSubmit(event: FormEvent) {
    event.preventDefault();
    if (!canPublish) {
      setError(t("console.rules.unchanged"));
      return;
    }
    if (!window.confirm(t("console.product.amlConfirm"))) {
      return;
    }
    setPending(true);
    setError(null);
    setNotice(null);
    const body = new FormData();
    body.set("review_threshold", draft.review);
    body.set("confirm_threshold", draft.confirm);
    body.set("on_match", policy.on_match);
    if (policy.test_integration_id) {
      body.set("test_integration_id", policy.test_integration_id);
    }
    if (policy.attach_to_identity) {
      body.set("attach_to_identity", "on");
    }
    for (const category of draft.categories) {
      body.append("categories", category);
    }
    const result = await publishAmlPolicyAction(body);
    setPending(false);
    if (!result.ok) {
      setError(result.message || t("console.integrations.createError"));
      return;
    }
    setNotice(t("console.rules.published"));
    router.refresh();
  }

  return (
    <form className="rm-rules" onSubmit={onSubmit}>
      <header className="rm-rules-status">
        <div className="rm-rules-status-copy">
          <StatusBadge label={badge} tone={policy.published ? "success" : "info"} />
          <p>{published}</p>
          <p className="rm-rules-applies">{t("console.product.amlConfirm")}</p>
        </div>
        {canWrite ? (
          <button type="submit" className="rm-rules-publish" disabled={pending || !canPublish}>
            {pending ? t("console.rules.publishing") : t("console.rules.publish")}
          </button>
        ) : (
          <p className="rm-rules-readonly">{t("console.rules.readonly")}</p>
        )}
      </header>
      {error ? (
        <p role="alert" className="rm-alert">
          {error}
        </p>
      ) : null}
      {notice ? <p className="rm-rules-notice">{notice}</p> : null}
      <div className="rm-rules-grid">
        <section className="rm-rules-panel">
          <div className="rm-rules-panel-head">
            <h2>{t("console.rules.profile")}</h2>
            <p>{t("console.product.amlThresholdHint")}</p>
          </div>
          <div className="rm-rules-metrics">
            <label className="rm-rules-metric">
              <span>
                {t("console.product.amlPolicyReview")}
                <small>0.50 – 0.85</small>
              </span>
              <input
                type="number"
                inputMode="decimal"
                name="review_threshold"
                min="0.5"
                max="0.85"
                step="0.001"
                value={draft.review}
                disabled={!canWrite}
                onChange={(event) => setDraft((current) => ({ ...current, review: event.target.value }))}
              />
            </label>
            <label className="rm-rules-metric">
              <span>
                {t("console.product.amlPolicyConfirm")}
                <small>0.90 – 1</small>
              </span>
              <input
                type="number"
                inputMode="decimal"
                min="0.9"
                max="1"
                step="0.001"
                value={draft.confirm}
                disabled={!canWrite}
                onChange={(event) => setDraft((current) => ({ ...current, confirm: event.target.value }))}
              />
            </label>
          </div>
        </section>
        <div className="rm-rules-side">
          <section className="rm-rules-panel">
            <div className="rm-rules-panel-head">
              <h2>{t("console.product.amlPolicyCategories")}</h2>
              <p>{t("console.product.amlCategorySanction")}</p>
            </div>
            <div className="rm-rules-docs">
              {CATEGORIES.map((category) => {
                const on = draft.categories.includes(category);
                return (
                  <label key={category} className="rm-rules-doc" data-on={on ? "true" : "false"}>
                    <input
                      type="checkbox"
                      checked={on}
                      disabled={!canWrite || category === "sanction"}
                      onChange={() => toggleCategory(category)}
                    />
                    <span className="rm-rules-doc-mark" aria-hidden="true" />
                    <span>{categoryLabel(t, category)}</span>
                  </label>
                );
              })}
            </div>
          </section>
          <section className="rm-rules-panel">
            <div className="rm-rules-panel-head">
              <h2>{t("console.rules.history")}</h2>
            </div>
            {versions.length === 0 ? (
              <p className="rm-rules-empty">{t("console.rules.historyEmpty")}</p>
            ) : (
              <ol className="rm-rules-history rm-rules-history-named">
                {versions.map((item) => (
                  <li key={item.version}>
                    <span className="rm-rules-history-version">{item.version}</span>
                    <span>{item.published_at ? formatUtc(item.published_at, locale) : ""}</span>
                    {item.published ? <StatusBadge label={t("console.rules.historyActive")} tone="success" /> : null}
                  </li>
                ))}
              </ol>
            )}
          </section>
        </div>
      </div>
    </form>
  );
}

function draftOf(policy: AmlPolicy): Draft {
  return {
    review: String(policy.review_threshold),
    confirm: String(policy.confirm_threshold),
    categories: policy.published
      ? policy.categories.includes("sanction")
        ? policy.categories
        : ["sanction", ...policy.categories]
      : [...CATEGORIES],
  };
}

