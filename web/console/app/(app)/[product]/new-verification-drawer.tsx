"use client";

import Link from "next/link";
import { useRouter } from "next/navigation";
import { useEffect, useRef, useState, type FormEvent, type PointerEvent } from "react";
import { createPortal } from "react-dom";
import { useT } from "../../../i18n/client";
import { tIntegrationMode } from "../../../lib/environment";
import { createVerificationAction } from "./verifications/actions";

const SCENARIOS = [
  ["approved", "console.verifications.scenarioApproved"],
  ["unsupported", "console.verifications.scenarioUnsupported"],
  ["expired", "console.verifications.scenarioExpired"],
  ["liveness_fail", "console.verifications.scenarioLiveness"],
  ["mismatch", "console.verifications.scenarioMismatch"],
  ["review", "console.verifications.scenarioReview"],
] as const;

export type DrawerIntegration = {
  id: string;
  name: string;
  mode: string;
};

export function NewVerificationDrawer({
  product,
  integrations,
  defaultIntegrationId,
}: {
  product: string;
  integrations: DrawerIntegration[];
  defaultIntegrationId?: string;
}) {
  const t = useT();
  const router = useRouter();
  const dialogRef = useRef<HTMLDialogElement>(null);
  const formRef = useRef<HTMLFormElement>(null);
  const [mounted, setMounted] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [creditBlocked, setCreditBlocked] = useState(false);
  const [pending, setPending] = useState(false);
  const initialId = defaultIntegrationId ?? integrations[0]?.id ?? "";
  const [selectedId, setSelectedId] = useState(initialId);

  useEffect(() => {
    setMounted(true);
  }, []);

  const selected = integrations.find((item) => item.id === selectedId) ?? integrations[0];
  const live = selected?.mode === "live";

  function openDrawer() {
    setError(null);
    setCreditBlocked(false);
    setPending(false);
    setSelectedId(defaultIntegrationId ?? integrations[0]?.id ?? "");
    formRef.current?.reset();
    dialogRef.current?.showModal();
    window.requestAnimationFrame(() => {
      formRef.current?.querySelector<HTMLInputElement>("input[name='first_name']")?.focus();
    });
  }

  function closeDrawer() {
    dialogRef.current?.close();
  }

  function onBackdropPointerDown(event: PointerEvent<HTMLDialogElement>) {
    if (event.target !== event.currentTarget) {
      return;
    }
    // Native <select> menus paint outside the panel and can emit a 0,0 click on the dialog.
    if (event.clientX === 0 && event.clientY === 0) {
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

  async function onSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setPending(true);
    setError(null);
    setCreditBlocked(false);
    try {
      const result = await createVerificationAction(new FormData(event.currentTarget));
      if (!result.ok) {
        setCreditBlocked(result.code === "insufficient_credit");
        setError(
          result.code === "insufficient_credit"
            ? t("console.verifications.insufficientCredit")
            : result.code === "forbidden"
              ? t("console.product.sessionsLocked")
              : t("console.verifications.createError"),
        );
        return;
      }
      closeDrawer();
      router.push(`/${product}/verifications/${result.data.id}`);
      router.refresh();
    } finally {
      setPending(false);
    }
  }

  const drawer = (
    <dialog
      ref={dialogRef}
      className="rm-drawer rm-drawer-wide"
      aria-labelledby="create-verification-title"
      onPointerDown={onBackdropPointerDown}
      onClose={() => {
        setError(null);
        setCreditBlocked(false);
        setPending(false);
      }}
    >
      <div className="rm-drawer-head">
        <div>
          <p className="rm-drawer-kicker">{t("console.nav.idv")}</p>
          <h2 id="create-verification-title">{t("console.product.newVerification")}</h2>
          <p className="rm-lead">{t("console.verifications.createLead")}</p>
        </div>
        <button type="button" className="rm-dialog-close" aria-label={t("common.close")} onClick={closeDrawer}>
          <svg width="14" height="14" viewBox="0 0 14 14" aria-hidden="true">
            <path
              d="M3 3l8 8M11 3l-8 8"
              fill="none"
              stroke="currentColor"
              strokeWidth="1.5"
              strokeLinecap="round"
            />
          </svg>
        </button>
      </div>
      <form ref={formRef} onSubmit={onSubmit} className="rm-form rm-drawer-form">
        {integrations.length <= 1 ? <input type="hidden" name="integration_id" value={selected?.id ?? ""} /> : null}
        <div className="rm-drawer-body">
          {error ? (
            <p role="alert" className="rm-alert">
              {error}
              {creditBlocked ? (
                <>
                  {" "}
                  <Link href="/settings/billing">{t("console.nav.billing")}</Link>
                </>
              ) : null}
            </p>
          ) : null}
          {integrations.length > 1 ? (
            <section className="rm-drawer-section">
              <label>
                {t("console.verifications.pickIntegration")}
                <span className="rm-hint">{t("console.verifications.pickIntegrationHint")}</span>
                <select
                  name="integration_id"
                  value={selectedId}
                  disabled={pending}
                  onPointerDown={(event) => event.stopPropagation()}
                  onClick={(event) => event.stopPropagation()}
                  onChange={(event) => setSelectedId(event.target.value)}
                >
                  {integrations.map((item) => (
                    <option key={item.id} value={item.id}>
                      {item.name} ({tIntegrationMode(t, item.mode)})
                    </option>
                  ))}
                </select>
              </label>
            </section>
          ) : null}
          <section className="rm-drawer-section">
            <h3 className="rm-drawer-section-title">{t("console.verifications.applicantSection")}</h3>
            <p className="rm-hint">{t("console.verifications.applicantHint")}</p>
            <div className="rm-drawer-row">
              <label>
                {t("console.verifications.firstName")}
                <input name="first_name" type="text" autoComplete="given-name" disabled={pending} />
              </label>
              <label>
                {t("console.verifications.lastName")}
                <input name="last_name" type="text" autoComplete="family-name" disabled={pending} />
              </label>
            </div>
            <label>
              {t("console.verifications.email")}
              <input name="email" type="email" autoComplete="email" disabled={pending} />
            </label>
          </section>
          <section className="rm-drawer-section">
            <label>
              {t("console.verifications.externalId")}
              <span className="rm-hint">{t("console.verifications.externalIdHint")}</span>
              <input name="external_id" type="text" disabled={pending} />
            </label>
          </section>
          {live ? null : (
            <fieldset className="rm-drawer-section" disabled={pending}>
              <legend className="rm-drawer-section-title">{t("console.verifications.scenario")}</legend>
              <p className="rm-hint">{t("console.verifications.scenarioHint")}</p>
              <div className="rm-choice-list">
                {SCENARIOS.map(([value, key]) => (
                  <label key={value} className="rm-choice">
                    <input type="radio" name="sandbox_scenario" value={value} defaultChecked={value === "approved"} />
                    <span>{t(key)}</span>
                  </label>
                ))}
              </div>
            </fieldset>
          )}
        </div>
        <div className="rm-drawer-foot">
          <button type="button" data-variant="secondary" onClick={closeDrawer}>
            {t("common.cancel")}
          </button>
          <button type="submit" disabled={pending || !selected}>
            {pending ? t("console.verifications.pending") : t("console.verifications.submit")}
          </button>
        </div>
      </form>
    </dialog>
  );

  return (
    <>
      <button type="button" className="rm-button" onClick={openDrawer} disabled={integrations.length === 0}>
        {t("console.product.newVerification")}
      </button>
      {mounted ? createPortal(drawer, document.body) : null}
    </>
  );
}
