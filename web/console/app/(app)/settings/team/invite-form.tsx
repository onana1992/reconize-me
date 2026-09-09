"use client";

import { useRouter } from "next/navigation";
import { useRef, useState } from "react";
import { RequiredMark } from "../../../../components/required-mark";
import { useT } from "../../../../i18n/client";
import { inviteMemberAction } from "../actions";
import { ASSIGNABLE_ROLES, tRole } from "../../../../lib/labels";

export function InviteButton() {
  const t = useT();
  const router = useRouter();
  const dialogRef = useRef<HTMLDialogElement>(null);
  const formRef = useRef<HTMLFormElement>(null);
  const [error, setError] = useState<string | null>(null);
  const [pending, setPending] = useState(false);

  function openDialog() {
    setError(null);
    formRef.current?.reset();
    dialogRef.current?.showModal();
    formRef.current?.querySelector<HTMLInputElement>("input[name='email']")?.focus();
  }

  function closeDialog() {
    dialogRef.current?.close();
  }

  async function onSubmit(formData: FormData) {
    setPending(true);
    setError(null);
    const result = await inviteMemberAction(formData);
    setPending(false);
    if (!result.ok) {
      if (result.code === "already_invited") {
        setError(t("console.team.alreadyInvited"));
      } else if (result.code === "already_member") {
        setError(t("console.team.alreadyMember"));
      } else if (result.code === "forbidden") {
        setError(t("console.team.inviteForbidden"));
      } else if (result.code === "validation_error") {
        setError(t("console.team.invalidRole"));
      } else {
        setError(result.message);
      }
      return;
    }
    closeDialog();
    router.refresh();
  }

  return (
    <>
      <button type="button" onClick={openDialog}>
        {t("console.team.invite")}
      </button>
      <dialog
        ref={dialogRef}
        className="rm-dialog"
        aria-labelledby="invite-dialog-title"
        onClick={(event) => {
          if (event.target === event.currentTarget) {
            closeDialog();
          }
        }}
        onClose={() => setError(null)}
      >
        <div className="rm-dialog-head">
          <h2 id="invite-dialog-title">{t("console.team.inviteTitle")}</h2>
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
        <form ref={formRef} action={onSubmit} className="rm-form">
          <label>
            <span>
              {t("common.email")}
              <RequiredMark />
            </span>
            <input name="email" type="email" required maxLength={255} />
          </label>
          <label>
            <span>
              {t("console.home.roleLabel")}
              <RequiredMark />
            </span>
            <select name="role" defaultValue="member">
              {ASSIGNABLE_ROLES.map((role) => (
                <option key={role} value={role}>
                  {tRole(t, role)}
                </option>
              ))}
            </select>
          </label>
          {error ? (
            <p role="alert" className="rm-alert">
              {error}
            </p>
          ) : null}
          <div className="rm-actions">
            <button type="submit" disabled={pending}>
              {pending ? t("console.team.sending") : t("console.team.invite")}
            </button>
            <button type="button" data-variant="secondary" onClick={closeDialog}>
              {t("common.cancel")}
            </button>
          </div>
        </form>
      </dialog>
    </>
  );
}
