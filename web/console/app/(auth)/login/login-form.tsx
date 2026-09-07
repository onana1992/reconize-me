"use client";

import { useRouter } from "next/navigation";
import { useState, type FormEvent } from "react";
import { PasswordInput } from "../../../components/password-input";
import { RequiredMark } from "../../../components/required-mark";
import { useT } from "../../../i18n/client";
import { loginAction } from "../actions";

export function LoginForm({ next }: { next: string }) {
  const t = useT();
  const router = useRouter();
  const [error, setError] = useState<string | null>(null);
  const [pending, setPending] = useState(false);

  async function onSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setPending(true);
    setError(null);
    try {
      const result = await loginAction(new FormData(event.currentTarget));
      if (!result.ok) {
        if (result.code === "email_unverified") {
          setError(t("login.emailUnverified"));
          return;
        }
        if (result.code === "invalid_credentials") {
          setError(t("login.invalidCredentials"));
          return;
        }
        if (result.code === "rate_limited") {
          setError(t("login.rateLimited"));
          return;
        }
        setError(result.message);
        return;
      }
      router.push(next);
      router.refresh();
    } finally {
      setPending(false);
    }
  }

  return (
    <form onSubmit={onSubmit} className="rm-form">
      <input type="hidden" name="next" value={next} />
      <label>
        <span>
          {t("login.email")}
          <RequiredMark />
        </span>
        <input name="email" type="email" autoComplete="email" required maxLength={255} />
      </label>
      <label>
        <span>
          {t("login.password")}
          <RequiredMark />
        </span>
        <PasswordInput name="password" autoComplete="current-password" required maxLength={128} />
      </label>
      {error ? (
        <p role="alert" className="rm-alert">
          {error}
        </p>
      ) : null}
      <button type="submit" disabled={pending}>
        {pending ? t("login.pending") : t("login.submit")}
      </button>
    </form>
  );
}
