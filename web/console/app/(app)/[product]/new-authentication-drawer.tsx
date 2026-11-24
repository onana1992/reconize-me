"use client";

import Link from "next/link";
import { useRouter } from "next/navigation";
import { useEffect, useRef, useState, type FormEvent, type PointerEvent } from "react";
import { createPortal } from "react-dom";
import { useT } from "../../../i18n/client";
import { tIntegrationMode } from "../../../lib/environment";
import { createAuthenticationAction } from "./authentications/actions";

export type DrawerIntegration = {
  id: string;
  name: string;
  mode: string;
};

export function NewAuthenticationDrawer({
  integrations,
  defaultIntegrationId,
}: {
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

  function openDrawer() {
    setError(null);
    setCreditBlocked(false);
    setPending(false);
    setSelectedId(defaultIntegrationId ?? integrations[0]?.id ?? "");
    formRef.current?.reset();
    dialogRef.current?.showModal();
    window.requestAnimationFrame(() => {
      formRef.current?.querySelector<HTMLInputElement>("input[name='external_id']")?.focus();
    });
  }

  function closeDrawer() {
    dialogRef.current?.close();
  }

  function onBackdropPointerDown(event: PointerEvent<HTMLDialogElement>) {
    if (event.target !== event.currentTarget) {
      return;
    }
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
      const result = await createAuthenticationAction(new FormData(event.currentTarget));
      if (!result.ok) {
        setCreditBlocked(result.code === "insufficient_credit");
        setError(
          result.code === "insufficient_credit"
            ? t("console.authentications.insufficientCredit")
            : result.code === "no_enrollment"
              ? t("console.authentications.noEnrollment")
              : result.code === "forbidden"
                ? t("console.authentications.locked")
                : t("console.authentications.createError"),
        );
        return;
      }
      closeDrawer();
      router.push(`/authentication/verifications/${result.data.id}`);
      router.refresh();
    } finally {
      setPending(false);
    }
  }

  const drawer = (
    <dialog
      ref={dialogRef}
      className="rm-drawer"
      aria-labelledby="create-authentication-title"
      onPointerDown={onBackdropPointerDown}
      onClose={() => {
        setError(null);
        setCreditBlocked(false);
        setPending(false);
      }}
    >
      <div className="rm-drawer-head">
        <div>
          <p className="rm-drawer-kicker">{t("console.nav.biometric")}</p>
          <h2 id="create-authentication-title">{t("console.product.newAuthentication")}</h2>
          <p className="rm-lead">{t("console.authentications.createLead")}</p>
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
                {t("console.authentications.pickIntegration")}
                <span className="rm-hint">{t("console.authentications.pickIntegrationHint")}</span>
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
            <label>
              {t("console.authentications.externalId")}
              <span className="rm-hint">{t("console.authentications.externalIdHint")}</span>
              <input name="external_id" type="text" required disabled={pending} />
            </label>
          </section>
        </div>
        <div className="rm-drawer-foot">
          <button type="button" data-variant="secondary" onClick={closeDrawer}>
            {t("common.cancel")}
          </button>
          <button type="submit" disabled={pending || !selected}>
            {pending ? t("console.authentications.pending") : t("console.authentications.submit")}
          </button>
        </div>
      </form>
    </dialog>
  );

  return (
    <>
      <button type="button" className="rm-button" onClick={openDrawer} disabled={integrations.length === 0}>
        {t("console.product.newAuthentication")}
      </button>
      {mounted ? createPortal(drawer, document.body) : null}
    </>
  );
}
