"use client";

import Link from "next/link";
import { useState, type FormEvent } from "react";
import { PasswordInput } from "../../../components/password-input";
import { RequiredMark } from "../../../components/required-mark";
import { useT } from "../../../i18n/client";
import { NEW_PASSWORD_ATTRS } from "../../../lib/password";
import { resetAction } from "../actions";

export function ResetForm({ token }: { token: string }) {
  const t = useT();
  const [error, setError] = useState<string | null>(null);
  const [done, setDone] = useState(false);
  const [pending, setPending] = useState(false);

  async function onSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const formData = new FormData(event.currentTarget);
    const password = String(formData.get("password") ?? "");
    const confirmation = String(formData.get("passwordConfirm") ?? "");
    if (password !== confirmation) {
      setError(t("reset.passwordMismatch"));
      return;
    }
    setPending(true);
    setError(null);
    const result = await resetAction(formData);
    setPending(false);
    if (!result.ok) {
      setError(result.code === "invalid_or_expired_token" ? t("reset.invalidToken") : result.message);
      return;
    }
    setDone(true);
  }

  if (done) {
    return (
      <>
        <h1>{t("reset.doneTitle")}</h1>
        <p className="rm-lead">{t("reset.doneLead")}</p>
        <p className="rm-notice">{t("reset.done")}</p>
        <p className="rm-auth-links">
          <Link href="/login">{t("reset.login")}</Link>
        </p>
      </>
    );
  }

  return (
    <>
      <h1>{t("reset.title")}</h1>
      <p className="rm-lead">{t("reset.lead")}</p>
      <form onSubmit={onSubmit} className="rm-form">
        <input type="hidden" name="token" value={token} />
        <label>
          <span>
            {t("reset.password")}
            <RequiredMark />
          </span>
          <PasswordInput
            name="password"
            autoComplete="new-password"
            required
            title={t("reset.passwordHint")}
            {...NEW_PASSWORD_ATTRS}
          />
          <span className="rm-hint">{t("reset.passwordHint")}</span>
        </label>
        <label>
          <span>
            {t("reset.confirmPassword")}
            <RequiredMark />
          </span>
          <PasswordInput
            name="passwordConfirm"
            autoComplete="new-password"
            required
            minLength={NEW_PASSWORD_ATTRS.minLength}
            maxLength={NEW_PASSWORD_ATTRS.maxLength}
            title={t("reset.passwordMismatch")}
          />
        </label>
        {error ? (
          <p role="alert" className="rm-alert">
            {error}
          </p>
        ) : null}
        <button type="submit" disabled={pending}>
          {pending ? t("reset.pending") : t("reset.submit")}
        </button>
      </form>
      <p className="rm-auth-links">
        <Link href="/login">{t("reset.back")}</Link>
      </p>
    </>
  );
}
