"use client";

import { useRouter } from "next/navigation";
import { useRef, useState } from "react";
import { SecretBlock } from "../../../../components/secret-block";
import { RequiredMark } from "../../../../components/required-mark";
import { useT } from "../../../../i18n/client";
import { createIntegrationAction } from "../../settings/actions";

export function CreateIntegrationButton({ product }: { product: string }) {
  const t = useT();
  const router = useRouter();
  const dialogRef = useRef<HTMLDialogElement>(null);
  const formRef = useRef<HTMLFormElement>(null);
  const [error, setError] = useState<string | null>(null);
  const [pending, setPending] = useState(false);
  const [issuedKey, setIssuedKey] = useState<string | null>(null);
  const [createdId, setCreatedId] = useState<string | null>(null);

  function openDialog() {
    setError(null);
    setIssuedKey(null);
    setCreatedId(null);
    formRef.current?.reset();
    dialogRef.current?.showModal();
    formRef.current?.querySelector<HTMLInputElement>("input[name='name']")?.focus();
  }

  function closeDialog() {
    dialogRef.current?.close();
  }

  function openCreated() {
    if (!createdId) {
      return;
    }
    closeDialog();
    router.push(`/${product}/integrations/${createdId}`);
    router.refresh();
  }

  async function onSubmit(formData: FormData) {
    setPending(true);
    setError(null);
    const result = await createIntegrationAction(formData);
    setPending(false);
    if (!result.ok) {
      if (result.code === "live_locked") {
        setError(t("console.integrations.liveLocked"));
      } else if (result.code === "name_taken") {
        setError(t("console.integrations.nameTaken"));
      } else if (result.code === "forbidden") {
        setError(t("console.integrations.forbidden"));
      } else if (result.code === "validation_error") {
        setError(t("console.integrations.nameRequired"));
      } else {
        setError(t("console.integrations.createError"));
      }
      return;
    }
    setCreatedId(result.data.id);
    setIssuedKey(result.data.key ?? null);
    router.refresh();
  }

  return (
    <>
      <button type="button" onClick={openDialog}>
        {t("console.integrations.create")}
      </button>
      <dialog
        ref={dialogRef}
        className="rm-dialog"
        aria-labelledby="create-integration-title"
        onClick={(event) => {
          if (event.target === event.currentTarget) {
            closeDialog();
          }
        }}
        onClose={() => {
          setError(null);
          setIssuedKey(null);
          setCreatedId(null);
        }}
      >
        <div className="rm-dialog-head">
          <h2 id="create-integration-title">{t("console.integrations.createTitle")}</h2>
          <button type="button" className="rm-dialog-close" aria-label={t("common.close")} onClick={closeDialog}>
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
        {issuedKey && createdId ? (
          <div className="rm-form">
            <SecretBlock
              title={t("console.keys.issuedTitle").replace("{env}", t("console.integrations.modeTest"))}
              description={t("console.keys.issuedBody")}
              value={issuedKey}
              copyLabel={t("common.copyKey")}
            />
            <div className="rm-actions">
              <button type="button" onClick={openCreated}>
                {t("console.integrations.open")}
              </button>
            </div>
          </div>
        ) : (
          <form ref={formRef} action={onSubmit} className="rm-form">
            <label>
              <span>
                {t("console.integrations.name")}
                <RequiredMark />
              </span>
              <span className="rm-hint">{t("console.integrations.nameHint")}</span>
              <input name="name" type="text" required maxLength={128} />
            </label>
            <label>
              <span>{t("console.integrations.mode")}</span>
              <select name="mode" defaultValue="test">
                <option value="test">{t("console.integrations.modeTest")}</option>
                <option value="live" disabled>
                  {t("console.integrations.modeLive")}
                </option>
              </select>
            </label>
            <p className="rm-hint">{t("console.integrations.liveLocked")}</p>
            {error ? (
              <p role="alert" className="rm-alert">
                {error}
              </p>
            ) : null}
            <div className="rm-actions">
              <button type="submit" disabled={pending}>
                {pending ? t("console.integrations.creating") : t("console.integrations.create")}
              </button>
              <button type="button" data-variant="secondary" onClick={closeDialog}>
                {t("common.cancel")}
              </button>
            </div>
          </form>
        )}
      </dialog>
    </>
  );
}
