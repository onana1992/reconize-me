"use client";

import { StatusBadge } from "@/brand";
import { useRouter } from "next/navigation";
import { useEffect, useMemo, useRef, useState, type FormEvent, type PointerEvent } from "react";
import { createPortal } from "react-dom";
import { useT } from "../../../../i18n/client";
import type { IdvPolicy, IdvPolicyDocument, IdvPolicyVersions } from "../../../../lib/api";
import { publishPolicyAction } from "./actions";

type Outcome = "DECLINED" | "REVIEW";
type ScreeningEffect = "ignore" | "review" | "block";

type Draft = {
  expired: Outcome;
  attempts: Outcome;
  ocr: string;
  liveness: string;
  faceFail: string;
  facePass: string;
  ttl: string;
  captures: string;
  livenessAttempts: string;
  codes: string[];
  screening: boolean;
  onMatch: ScreeningEffect;
};

export function PolicyForm({
  policy,
  versions,
  canWrite,
  locale,
}: {
  policy: IdvPolicy;
  versions: IdvPolicyVersions["versions"];
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
  const canPublish = canWrite && (policy.source === "platform" || dirty);
  const badge =
    policy.source === "organization" && policy.version != null
      ? t("console.rules.badgeVersion").replace("{version}", String(policy.version))
      : t("console.rules.badgePlatform");
  const published =
    policy.source === "organization" && policy.published_at
      ? t("console.rules.sourceOrganization")
          .replace("{version}", String(policy.version ?? ""))
          .replace("{date}", formatWhen(policy.published_at, locale))
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
    if (policy.source !== "platform" && !dirty) {
      setError(t("console.rules.unchanged"));
      return;
    }
    if (!window.confirm(t("console.rules.confirm"))) {
      return;
    }
    setPending(true);
    setError(null);
    setNotice(null);
    const result = await publishPolicyAction({
      profile: {
        expired_outcome: draft.expired,
        attempts_outcome: draft.attempts,
        ocr_review_at: Number(draft.ocr),
        liveness_pass_at: Number(draft.liveness),
        face_match_fail_at: Number(draft.faceFail),
        face_match_pass_at: Number(draft.facePass),
      },
      document_codes: draft.codes,
      flow: {
        link_ttl_seconds: Number(draft.ttl),
        max_capture_attempts: Number(draft.captures),
        max_liveness_attempts: Number(draft.livenessAttempts),
      },
      screening: {
        enabled: draft.screening,
        on_match: draft.onMatch,
      },
    });
    setPending(false);
    if (!result.ok) {
      setError(fieldMessage(t, result.details?.[0]));
      return;
    }
    setNotice(t("console.rules.published"));
    router.refresh();
  }

  return (
    <form className="rm-rules" onSubmit={onSubmit}>
      <header className="rm-rules-status">
        <div className="rm-rules-status-copy">
          <StatusBadge label={badge} tone={policy.source === "organization" ? "success" : "info"} />
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
            <p>{t("console.rules.fixedChain")}</p>
          </div>
          <div className="rm-rules-outcomes">
            <OutcomeField
              label={t("console.rules.expired")}
              value={draft.expired}
              disabled={!canWrite}
              declined={t("console.rules.declined")}
              review={t("console.rules.review")}
              onChange={(value) => setField("expired", value)}
            />
            <OutcomeField
              label={t("console.rules.attemptsOutcome")}
              value={draft.attempts}
              disabled={!canWrite}
              declined={t("console.rules.declined")}
              review={t("console.rules.review")}
              onChange={(value) => setField("attempts", value)}
            />
          </div>
          <div className="rm-rules-metrics">
            <NumberField
              label={t("console.rules.ocr")}
              hint={rangeHint(t, policy.bounds.ocr_review_at)}
              value={draft.ocr}
              min={policy.bounds.ocr_review_at[0]}
              max={policy.bounds.ocr_review_at[1]}
              step="0.01"
              disabled={!canWrite}
              onChange={(value) => setField("ocr", value)}
            />
            <NumberField
              label={t("console.rules.liveness")}
              hint={rangeHint(t, policy.bounds.liveness_pass_at)}
              value={draft.liveness}
              min={policy.bounds.liveness_pass_at[0]}
              max={policy.bounds.liveness_pass_at[1]}
              step="0.01"
              disabled={!canWrite}
              onChange={(value) => setField("liveness", value)}
            />
            <NumberField
              label={t("console.rules.faceFail")}
              hint={rangeHint(t, policy.bounds.face_match_fail_at)}
              value={draft.faceFail}
              min={policy.bounds.face_match_fail_at[0]}
              max={policy.bounds.face_match_fail_at[1]}
              step="0.01"
              disabled={!canWrite}
              onChange={(value) => setField("faceFail", value)}
            />
            <NumberField
              label={t("console.rules.facePass")}
              hint={rangeHint(t, policy.bounds.face_match_pass_at)}
              value={draft.facePass}
              min={policy.bounds.face_match_pass_at[0]}
              max={policy.bounds.face_match_pass_at[1]}
              step="0.01"
              disabled={!canWrite}
              onChange={(value) => setField("facePass", value)}
            />
          </div>
        </section>

        <div className="rm-rules-side">
          <section className="rm-rules-panel">
            <div className="rm-rules-panel-head">
              <h2>{t("console.rules.amlTitle")}</h2>
              <p>{t("console.rules.amlHint")}</p>
            </div>
            <label className="rm-rules-doc" data-on={draft.screening ? "true" : "false"}>
              <input
                type="checkbox"
                checked={draft.screening}
                disabled={!canWrite}
                onChange={(event) => setField("screening", event.target.checked)}
              />
              <span className="rm-rules-doc-mark" aria-hidden="true" />
              <span>{t("console.rules.amlEnable")}</span>
            </label>
            {draft.screening ? (
              <>
                <div className="rm-rules-outcome">
                  <span>{t("console.rules.amlEffect")}</span>
                  <div className="rm-rules-seg" role="radiogroup" aria-label={t("console.rules.amlEffect")}>
                    {(["ignore", "review", "block"] as const).map((effect) => (
                      <button
                        key={effect}
                        type="button"
                        role="radio"
                        aria-checked={draft.onMatch === effect}
                        data-choice={effect === "block" ? "declined" : effect}
                        data-on={draft.onMatch === effect ? "true" : "false"}
                        disabled={!canWrite}
                        onClick={() => setField("onMatch", effect)}
                      >
                        {effect === "ignore"
                          ? t("console.rules.amlIgnore")
                          : effect === "block"
                            ? t("console.rules.amlBlock")
                            : t("console.rules.amlReview")}
                      </button>
                    ))}
                  </div>
                </div>
                <p className="rm-rules-empty">
                  {draft.onMatch === "ignore"
                    ? t("console.rules.amlIgnoreHint")
                    : draft.onMatch === "block"
                      ? t("console.rules.amlBlockHint")
                      : t("console.rules.amlReviewHint")}
                </p>
              </>
            ) : null}
          </section>

          <DocumentCatalog
            documents={policy.documents}
            selected={draft.codes}
            canWrite={canWrite}
            onApply={(codes) => setField("codes", codes)}
          />

          <section className="rm-rules-panel">
            <div className="rm-rules-panel-head">
              <h2>{t("console.rules.flow")}</h2>
            </div>
            <div className="rm-rules-metrics rm-rules-metrics-flow">
              <NumberField
                label={t("console.rules.ttl")}
                hint={ttlCaption(t, draft.ttl)}
                value={draft.ttl}
                min={policy.bounds.link_ttl_seconds[0]}
                max={policy.bounds.link_ttl_seconds[1]}
                step="60"
                disabled={!canWrite}
                onChange={(value) => setField("ttl", value)}
              />
              <NumberField
                label={t("console.rules.captureAttempts")}
                hint={rangeHint(t, policy.bounds.max_capture_attempts)}
                value={draft.captures}
                min={policy.bounds.max_capture_attempts[0]}
                max={policy.bounds.max_capture_attempts[1]}
                step="1"
                disabled={!canWrite}
                onChange={(value) => setField("captures", value)}
              />
              <NumberField
                label={t("console.rules.livenessAttempts")}
                hint={rangeHint(t, policy.bounds.max_liveness_attempts)}
                value={draft.livenessAttempts}
                min={policy.bounds.max_liveness_attempts[0]}
                max={policy.bounds.max_liveness_attempts[1]}
                step="1"
                disabled={!canWrite}
                onChange={(value) => setField("livenessAttempts", value)}
              />
            </div>
            <div className="rm-rules-consent">
              <p className="rm-rules-consent-lock">
                {t("console.rules.consentLocked").replace("{version}", policy.flow.consent_text_version)}
              </p>
              <blockquote lang="fr">
                <span>FR</span>
                {t("console.rules.consentFr")}
              </blockquote>
              <blockquote lang="en">
                <span>EN</span>
                {t("console.rules.consentEn")}
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
                    <span className="rm-rules-history-version">{item.version}</span>
                    <span>{formatWhen(item.published_at, locale)}</span>
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

function DocumentCatalog({
  documents,
  selected,
  canWrite,
  onApply,
}: {
  documents: IdvPolicyDocument[];
  selected: string[];
  canWrite: boolean;
  onApply: (codes: string[]) => void;
}) {
  const t = useT();
  const dialogRef = useRef<HTMLDialogElement>(null);
  const allRef = useRef<HTMLInputElement>(null);
  const [mounted, setMounted] = useState(false);
  const [picked, setPicked] = useState<string[]>(selected);
  const accepted = documents.filter((document) => selected.includes(document.code));
  const allCodes = documents.map((document) => document.code);
  const allOn = documents.length > 0 && picked.length === documents.length;

  useEffect(() => {
    setMounted(true);
  }, []);

  useEffect(() => {
    if (allRef.current) {
      allRef.current.indeterminate = picked.length > 0 && picked.length < documents.length;
    }
  }, [picked, documents.length]);

  function openDrawer() {
    setPicked(selected);
    dialogRef.current?.showModal();
  }

  function closeDrawer() {
    dialogRef.current?.close();
  }

  function toggle(code: string) {
    setPicked((current) =>
      current.includes(code) ? current.filter((item) => item !== code) : [...current, code],
    );
  }

  function onBackdropPointerDown(event: PointerEvent<HTMLDialogElement>) {
    if (event.target !== event.currentTarget) {
      return;
    }
    const rect = event.currentTarget.getBoundingClientRect();
    const inside =
      event.clientX >= rect.left &&
      event.clientX <= rect.right &&
      event.clientY >= rect.top &&
      event.clientY <= rect.bottom;
    if (!inside) {
      closeDrawer();
    }
  }

  const drawer = (
    <dialog
      ref={dialogRef}
      className="rm-drawer rm-drawer-wide"
      aria-labelledby="rules-catalog-title"
      onPointerDown={onBackdropPointerDown}
    >
      <div className="rm-drawer-head">
        <div>
          <p className="rm-drawer-kicker">{t("console.rules.documents")}</p>
          <h2 id="rules-catalog-title">{t("console.rules.drawerTitle")}</h2>
          <p className="rm-lead">{t("console.rules.documentsHint")}</p>
        </div>
        <button type="button" className="rm-dialog-close" aria-label={t("common.close")} onClick={closeDrawer}>
          <svg width="14" height="14" viewBox="0 0 14 14" aria-hidden="true">
            <path d="M3 3l8 8M11 3l-8 8" fill="none" stroke="currentColor" strokeWidth="1.5" strokeLinecap="round" />
          </svg>
        </button>
      </div>
      <div className="rm-drawer-body">
        <div className="rm-rules-catalog-bar">
          <label className="rm-rules-catalog-all">
            <input
              ref={allRef}
              type="checkbox"
              checked={allOn}
              disabled={!canWrite || documents.length === 0}
              onChange={() => setPicked(allOn ? [] : allCodes)}
            />
            {t("console.rules.catalogAll")}
          </label>
          <div className="rm-rules-catalog-actions">
            <button type="button" data-variant="secondary" disabled={!canWrite} onClick={() => setPicked(allCodes)}>
              {t("console.rules.selectAll")}
            </button>
            <button type="button" data-variant="secondary" disabled={!canWrite} onClick={() => setPicked([])}>
              {t("console.rules.selectNone")}
            </button>
          </div>
        </div>
        {picked.length === 0 ? <p className="rm-rules-empty">{t("console.rules.documentsNone")}</p> : null}
        <div className="rm-rules-docs">
          {documents.map((document) => {
            const on = picked.includes(document.code);
            return (
              <label key={document.code} className="rm-rules-doc" data-on={on ? "true" : "false"}>
                <input type="checkbox" checked={on} disabled={!canWrite} onChange={() => toggle(document.code)} />
                <span className="rm-rules-doc-mark" aria-hidden="true" />
                <span className="rm-rules-doc-copy">
                  <span className="rm-rules-doc-title">{documentLabel(t, document.code)}</span>
                  <span className="rm-rules-doc-meta">
                    {document.code} · {document.country}
                    {document.sides.length > 0 ? ` · ${document.sides.map((side) => sideLabel(t, side)).join(", ")}` : ""}
                  </span>
                </span>
                <span className="rm-rules-doc-state">{on ? t("console.rules.accepted") : t("console.rules.refused")}</span>
              </label>
            );
          })}
        </div>
      </div>
      <div className="rm-drawer-foot">
        <button type="button" data-variant="secondary" onClick={closeDrawer}>
          {canWrite ? t("common.cancel") : t("common.close")}
        </button>
        {canWrite ? (
          <button
            type="button"
            onClick={() => {
              onApply(picked);
              closeDrawer();
            }}
          >
            {t("console.rules.applySelection")}
          </button>
        ) : null}
      </div>
    </dialog>
  );

  return (
    <section className="rm-rules-panel">
      <div className="rm-rules-docs-head">
        <div className="rm-rules-panel-head">
          <h2>{t("console.rules.documents")}</h2>
          <p>{t("console.rules.documentsHint")}</p>
        </div>
        {documents.length > 0 ? (
          <button type="button" data-variant="secondary" onClick={openDrawer}>
            {t("console.rules.chooseDocuments")}
          </button>
        ) : null}
      </div>
      {documents.length === 0 ? (
        <p className="rm-rules-empty">{t("console.rules.documentsEmpty")}</p>
      ) : (
        <>
          <p className="rm-rules-docs-count">
            {t("console.rules.documentsCount")
              .replace("{selected}", String(accepted.length))
              .replace("{total}", String(documents.length))}
          </p>
          {accepted.length === 0 ? (
            <p className="rm-rules-empty">{t("console.rules.documentsNone")}</p>
          ) : (
            <ul className="rm-rules-chips">
              {accepted.map((document) => (
                <li key={document.code} className="rm-rules-chip">
                  {documentLabel(t, document.code)}
                </li>
              ))}
            </ul>
          )}
        </>
      )}
      {mounted ? createPortal(drawer, document.body) : null}
    </section>
  );
}

function OutcomeField({
  label,
  value,
  disabled,
  declined,
  review,
  onChange,
}: {
  label: string;
  value: Outcome;
  disabled: boolean;
  declined: string;
  review: string;
  onChange: (value: Outcome) => void;
}) {
  return (
    <div className="rm-rules-outcome">
      <span>{label}</span>
      <div className="rm-rules-seg" role="radiogroup" aria-label={label}>
        <button
          type="button"
          role="radio"
          aria-checked={value === "DECLINED"}
          data-choice="declined"
          data-on={value === "DECLINED" ? "true" : "false"}
          disabled={disabled}
          onClick={() => onChange("DECLINED")}
        >
          {declined}
        </button>
        <button
          type="button"
          role="radio"
          aria-checked={value === "REVIEW"}
          data-choice="review"
          data-on={value === "REVIEW" ? "true" : "false"}
          disabled={disabled}
          onClick={() => onChange("REVIEW")}
        >
          {review}
        </button>
      </div>
    </div>
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
  hint?: string;
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
        {hint ? <small>{hint}</small> : null}
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

function screeningEffect(policy: IdvPolicy): ScreeningEffect {
  const effect = policy.screening?.on_match;
  if (effect === "ignore" || effect === "block") {
    return effect;
  }
  return "review";
}

function draftOf(policy: IdvPolicy): Draft {
  return {
    expired: policy.profile.expired_outcome,
    attempts: policy.profile.attempts_outcome,
    ocr: String(policy.profile.ocr_review_at),
    liveness: String(policy.profile.liveness_pass_at),
    faceFail: String(policy.profile.face_match_fail_at),
    facePass: String(policy.profile.face_match_pass_at),
    ttl: String(policy.flow.link_ttl_seconds),
    captures: String(policy.flow.max_capture_attempts),
    livenessAttempts: String(policy.flow.max_liveness_attempts),
    codes: policy.documents.filter((document) => document.enabled).map((document) => document.code),
    screening: policy.screening?.enabled ?? false,
    onMatch: screeningEffect(policy),
  };
}

function documentLabel(t: ReturnType<typeof useT>, code: string) {
  if (code === "CANADA_PERMANENT_RESIDENT") {
    return t("console.rules.nameCanadaPermanentResident");
  }
  if (code === "QUEBEC_DRIVER_LICENSE") {
    return t("console.rules.nameQuebecDriverLicense");
  }
  return code;
}

function sideLabel(t: ReturnType<typeof useT>, side: string) {
  if (side === "FRONT") {
    return t("console.rules.sideFront");
  }
  if (side === "BACK") {
    return t("console.rules.sideBack");
  }
  return side;
}

function fieldMessage(t: ReturnType<typeof useT>, detail: { field: string; code: string } | undefined) {
  if (!detail) {
    return t("console.rules.errorInvalid");
  }
  if (detail.code === "below_minimum") {
    return t("console.rules.errorBelow");
  }
  if (detail.code === "above_maximum") {
    return t("console.rules.errorAbove");
  }
  if (detail.code === "order") {
    return t("console.rules.errorOrder");
  }
  if (detail.code === "empty") {
    return t("console.rules.errorEmpty");
  }
  if (detail.code === "unknown") {
    return t("console.rules.errorUnknown");
  }
  return t("console.rules.errorInvalid");
}

function rangeHint(t: ReturnType<typeof useT>, bounds: number[]) {
  return t("console.rules.range").replace("{min}", String(bounds[0])).replace("{max}", String(bounds[1]));
}

function ttlCaption(t: ReturnType<typeof useT>, seconds: string) {
  const value = Number(seconds);
  if (!Number.isFinite(value) || value <= 0) {
    return "";
  }
  const hours = value / 3600;
  const label = Number.isInteger(hours) ? String(hours) : hours.toFixed(1);
  return t("console.rules.ttlHours").replace("{hours}", label);
}

function formatWhen(value: string, locale: string) {
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) {
    return value;
  }
  return new Intl.DateTimeFormat(locale, { dateStyle: "medium", timeStyle: "short" }).format(date);
}
