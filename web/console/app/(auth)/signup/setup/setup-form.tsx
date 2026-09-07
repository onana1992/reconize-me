"use client";

import Link from "next/link";
import { useRouter } from "next/navigation";
import { useState, type FormEvent } from "react";
import { PasswordInput } from "../../../../components/password-input";
import { RequiredMark } from "../../../../components/required-mark";
import { useLocale, useT } from "../../../../i18n/client";
import { NEW_PASSWORD_ATTRS } from "../../../../lib/password";
import { siteDpaUrl, siteTermsUrl } from "../../../../lib/site";
import { signupAction } from "../../actions";

export function SetupForm({ email, invite }: { email: string; invite: string }) {
  const t = useT();
  const locale = useLocale();
  const router = useRouter();
  const [error, setError] = useState<string | null>(null);
  const [pending, setPending] = useState(false);
  const backQuery = new URLSearchParams({ email, ...(invite ? { invite } : {}) });

  async function onSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setPending(true);
    setError(null);
    const result = await signupAction(new FormData(event.currentTarget));
    setPending(false);
    if (!result.ok) {
      if (result.code === "email_taken") {
        setError(t("setup.emailTaken"));
        return;
      }
      if (result.code === "invalid_or_expired_token") {
        setError(t("setup.invalidToken"));
        return;
      }
      if (result.code === "validation_error") {
        setError(t("setup.validation"));
        return;
      }
      setError(result.message);
      return;
    }
    router.push(`/verify/pending?email=${encodeURIComponent(email)}`);
  }

  return (
    <form onSubmit={onSubmit} className="su-form">
      <label className="su-field">
        <span>
          {t("setup.email")}
          <RequiredMark />
        </span>
        <input name="email" type="email" value={email} readOnly autoComplete="email" />
      </label>
      {invite ? <input type="hidden" name="invite_token" value={invite} /> : null}
      {invite ? <input type="hidden" name="organization_name" value="Invited" /> : null}

      {invite ? null : (
        <label className="su-field">
          <span>
            {t("setup.company")}
            <RequiredMark />
          </span>
          <input name="organization_name" type="text" autoComplete="organization" required minLength={2} maxLength={100} />
        </label>
      )}

      <div className="su-row">
        <label className="su-field">
          <span>
            {t("setup.firstName")}
            <RequiredMark />
          </span>
          <input name="first_name" type="text" autoComplete="given-name" required minLength={1} maxLength={100} />
        </label>
        <label className="su-field">
          <span>
            {t("setup.lastName")}
            <RequiredMark />
          </span>
          <input name="last_name" type="text" autoComplete="family-name" required minLength={1} maxLength={100} />
        </label>
      </div>

      <label className="su-field">
        <span>
          {t("setup.password")}
          <RequiredMark />
        </span>
        <PasswordInput
          name="password"
          autoComplete="new-password"
          required
          title={t("setup.passwordHint")}
          {...NEW_PASSWORD_ATTRS}
        />
        <span className="su-hint">{t("setup.passwordHint")}</span>
      </label>

      <div className="su-checks">
        <label className="su-check">
          <input name="terms" type="checkbox" required />
          <span>
            {t("setup.termsBefore")}
            <a href={siteTermsUrl(locale)} target="_blank" rel="noreferrer">
              {t("setup.termsLink")}
            </a>
            {t("setup.termsBetween")}
            <a href={siteDpaUrl(locale)} target="_blank" rel="noreferrer">
              {t("setup.dpaLink")}
            </a>
            {t("setup.termsAfter")}
            <RequiredMark />
          </span>
        </label>
        <label className="su-check">
          <input name="marketing" type="checkbox" />
          <span title={t("setup.marketingHint")}>{t("setup.marketing")}</span>
        </label>
      </div>

      {error ? (
        <p role="alert" className="su-error">
          {error}
        </p>
      ) : null}

      <div className="su-actions">
        <Link href={`/signup?${backQuery}`} className="su-back">
          ← {t("setup.back")}
        </Link>
        <button type="submit" className="su-submit" disabled={pending}>
          {pending ? t("setup.pending") : t("setup.continue")}
        </button>
      </div>
    </form>
  );
}
