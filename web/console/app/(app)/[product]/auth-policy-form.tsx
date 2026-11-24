"use client";

import { StatusBadge } from "@/brand";
import { useRouter } from "next/navigation";
import { useMemo, useState, type FormEvent } from "react";
import { useT } from "../../../i18n/client";
import type { AuthPolicy } from "../../../lib/api";
import { publishAuthPolicyAction } from "./auth-policy-actions";

type Draft = {
  approval: string;
  attempts: string;
};

export function AuthPolicyForm({
  integrationId,
  policy,
  versions,
  canWrite,
  locale,
}: {
  integrationId: string;
  policy: AuthPolicy;
  versions: AuthPolicy[];
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
  const version = versionLabel(policy.version);
  const badge = policy.published
    ? t("console.rules.badgeVersion").replace("{version}", version)
    : t("console.rules.badgePlatform");
  const published = policy.published
    ? t("console.rules.sourceOrganization")
        .replace("{version}", version)
        .replace("{date}", policy.published_at ? formatWhen(policy.published_at, locale) : "—")
        .replace("{name}", policy.published_by_name ?? "—")
    : t("console.rules.sourcePlatform");

  function setField<K extends keyof Draft>(key: K, value: Draft[K]) {
    setDraft((current) => ({ ...current, [key]: value }));
  }

  async function onSubmit(event: FormEvent) {
    event.preventDefault();
    if (!canWrite) {
      return;
    }
    if (policy.published && !dirty) {
      setError(t("console.rules.unchanged"));
      return;
    }
    if (!window.confirm(t("console.rules.confirm"))) {
      return;
    }
    setPending(true);
    setError(null);
    setNotice(null);
    const body = new FormData();
    body.set("integration_id", integrationId);
    body.set("approval_threshold", draft.approval);
    body.set("on_borderline", "decline");
    body.set("max_capture_attempts", draft.attempts);
    const result = await publishAuthPolicyAction(body);
    setPending(false);
    if (!result.ok) {
      setError(result.message || t("console.authentications.policyError"));
      return;
    }
    setNotice(t("console.rules.published"));
    router.refresh();
  }

  return (
    <form className="rm-rules" onSubmit={(event) => void onSubmit(event)}>
      <header className="rm-rules-status">
        <div className="rm-rules-status-copy">
          <StatusBadge label={badge} tone={policy.published ? "success" : "info"} />
          <p>{published}</p>
          <p className="rm-rules-applies">{t("console.rules.applies")}</p>
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
            <p>{t("console.authentications.policyHint")}</p>
          </div>
          <div className="rm-rules-metrics">
            <NumberField
              label={t("console.authentications.approvalLabel")}
              hint={t("console.rules.range").replace("{min}", "0.90").replace("{max}", "1.00")}
              value={draft.approval}
              min={0.9}
              max={1}
              step="0.01"
              disabled={!canWrite}
              onChange={(value) => setField("approval", value)}
            />
          </div>
        </section>

        <div className="rm-rules-side">
          <section className="rm-rules-panel">
            <div className="rm-rules-panel-head">
              <h2>{t("console.rules.flow")}</h2>
            </div>
            <div className="rm-rules-metrics">
              <NumberField
                label={t("console.authentications.attempts")}
                hint={t("console.rules.range").replace("{min}", "1").replace("{max}", "3")}
                value={draft.attempts}
                min={1}
                max={3}
                step="1"
                disabled={!canWrite}
                onChange={(value) => setField("attempts", value)}
              />
              <NumberField
                label={t("console.authentications.livenessLabel")}
                hint={t("console.authentications.livenessFixed")}
                value="0.80"
                min={0.8}
                max={0.8}
                step="0.01"
                disabled
                onChange={() => undefined}
              />
            </div>
            <div className="rm-rules-consent">
              <p className="rm-rules-consent-lock">
                {t("console.rules.consentLocked").replace("{version}", "auth-consent-1")}
              </p>
              <blockquote lang="fr">
                <span>FR</span>
                {t("console.authentications.consentFr")}
              </blockquote>
              <blockquote lang="en">
                <span>EN</span>
                {t("console.authentications.consentEn")}
              </blockquote>
            </div>
          </section>

          <section className="rm-rules-panel">
            <div className="rm-rules-panel-head">
              <h2>{t("console.rules.history")}</h2>
            </div>
            {versions.length === 0 ? (
              <p className="rm-rules-empty">{t("console.rules.historyEmpty")}</p>
            ) : (
              <ol className="rm-rules-history">
                {versions.map((item) => (
                  <li key={item.version} data-active={item.active ? "true" : "false"}>
                    <span className="rm-rules-history-version">{versionLabel(item.version)}</span>
                    <span>{item.published_at ? formatWhen(item.published_at, locale) : ""}</span>
                    {item.active ? <StatusBadge label={t("console.rules.historyActive")} tone="success" /> : <span />}
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

function NumberField({
  label,
  hint,
  value,
  min,
  max,
  step,
  disabled,
  onChange,
}: {
  label: string;
  hint: string;
  value: string;
  min: number;
  max: number;
  step: string;
  disabled: boolean;
  onChange: (value: string) => void;
}) {
  return (
    <label className="rm-rules-metric">
      <span>
        {label}
        <small>{hint}</small>
      </span>
      <input
        type="number"
        inputMode="decimal"
        min={min}
        max={max}
        step={step}
        value={value}
        disabled={disabled}
        onChange={(event) => onChange(event.target.value)}
      />
    </label>
  );
}

function draftOf(policy: AuthPolicy): Draft {
  return {
    approval: String(policy.approval_threshold),
    attempts: String(policy.max_capture_attempts),
  };
}

function versionLabel(version: string) {
  return version.replace(/^auth-policy-/, "");
}

function formatWhen(value: string, locale: string): string {
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) {
    return value;
  }
  return new Intl.DateTimeFormat(locale, { dateStyle: "medium", timeStyle: "short" }).format(date);
}
