"use client";

import Link from "next/link";
import { useState } from "react";
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

  async function onSubmit(formData: FormData) {
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
      <p className="rm-notice">
        {t("reset.done")} <Link href="/login">{t("reset.login")}</Link>
      </p>
    );
  }

  return (
    <form action={onSubmit} className="rm-form">
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
        <span className="rm-hint rm-hint-danger">{t("reset.passwordHint")}</span>
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
  );
}
