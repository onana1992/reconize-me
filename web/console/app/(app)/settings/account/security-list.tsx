"use client";

import { useRef, useState } from "react";
import { PasswordInput } from "../../../../components/password-input";
import { RequiredMark } from "../../../../components/required-mark";
import { useT } from "../../../../i18n/client";
import { NEW_PASSWORD_ATTRS } from "../../../../lib/password";
import { changePasswordAction } from "../actions";

export function SecurityList() {
  const t = useT();
  const dialogRef = useRef<HTMLDialogElement>(null);
  const [formKey, setFormKey] = useState(0);

  function openPassword() {
    setFormKey((value) => value + 1);
    dialogRef.current?.showModal();
  }

  function closePassword() {
    dialogRef.current?.close();
  }

  return (
    <>
      <div className="rm-details">
        <button type="button" onClick={openPassword}>
          <span>{t("console.account.password")}</span>
          <ChevronIcon />
        </button>
      </div>
      <dialog
        ref={dialogRef}
        className="rm-dialog"
        aria-labelledby="password-dialog-title"
        onClick={(event) => {
          if (event.target === event.currentTarget) {
            closePassword();
          }
        }}
      >
        <div className="rm-dialog-head">
          <h2 id="password-dialog-title">{t("console.account.password")}</h2>
          <button type="button" className="rm-dialog-close" aria-label={t("common.close")} onClick={closePassword}>
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
        <p className="rm-lead">{t("console.account.passwordLead")}</p>
        <PasswordForm key={formKey} onSuccess={closePassword} onCancel={closePassword} />
      </dialog>
    </>
  );
}

function PasswordForm({ onSuccess, onCancel }: { onSuccess: () => void; onCancel: () => void }) {
  const t = useT();
  const [error, setError] = useState<string | null>(null);
  const [pending, setPending] = useState(false);

  async function onSubmit(formData: FormData) {
    setPending(true);
    setError(null);
    const result = await changePasswordAction(formData);
    setPending(false);
    if (!result.ok) {
      setError(result.code === "invalid_credentials" ? t("console.account.passwordWrong") : result.message);
      return;
    }
    onSuccess();
  }

  return (
    <form action={onSubmit} className="rm-form">
      <label>
        <span>
          {t("console.account.currentPassword")}
          <RequiredMark />
        </span>
        <PasswordInput name="current_password" autoComplete="current-password" required />
      </label>
      <label>
        <span>
          {t("console.account.newPassword")}
          <RequiredMark />
        </span>
        <PasswordInput
          name="new_password"
          autoComplete="new-password"
          required
          title={t("setup.passwordHint")}
          {...NEW_PASSWORD_ATTRS}
        />
        <span className="rm-hint rm-hint-danger">{t("setup.passwordHint")}</span>
      </label>
      {error ? (
        <p role="alert" className="rm-alert">
          {error}
        </p>
      ) : null}
      <div className="rm-actions">
        <button type="submit" disabled={pending}>
          {pending ? t("console.account.passwordPending") : t("console.account.passwordSubmit")}
        </button>
        <button type="button" data-variant="secondary" onClick={onCancel}>
          {t("common.cancel")}
        </button>
      </div>
    </form>
  );
}

function ChevronIcon() {
  return (
    <svg width="16" height="16" viewBox="0 0 16 16" aria-hidden="true">
      <path d="M6 3.5 11 8l-5 4.5" fill="none" stroke="currentColor" strokeWidth="1.5" strokeLinecap="round" />
    </svg>
  );
}
