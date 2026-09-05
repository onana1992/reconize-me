"use client";

import Link from "next/link";
import { useRouter } from "next/navigation";
import { useState, type FormEvent } from "react";
import { useLocale, useT } from "../../../../i18n/client";
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
      setError(result.code === "email_taken" ? t("setup.emailTaken") : result.message);
      return;
    }
    router.push(`/verify/pending?email=${encodeURIComponent(email)}`);
  }

  return (
    <form onSubmit={onSubmit} className="su-form">
      <input type="hidden" name="email" value={email} />
      {invite ? <input type="hidden" name="invite_token" value={invite} /> : null}
      {invite ? <input type="hidden" name="organization_name" value="Invited" /> : null}

      {invite ? null : (
        <label className="su-field">
          {t("setup.company")}
          <input name="organization_name" type="text" autoComplete="organization" required minLength={2} maxLength={100} />
        </label>
      )}

      <div className="su-row">
        <label className="su-field">
          {t("setup.firstName")}
          <input name="first_name" type="text" autoComplete="given-name" required minLength={1} maxLength={100} />
        </label>
        <label className="su-field">
          {t("setup.lastName")}
          <input name="last_name" type="text" autoComplete="family-name" required minLength={1} maxLength={100} />
        </label>
      </div>

      <label className="su-field">
        {t("setup.password")}
        <input name="password" type="password" autoComplete="new-password" required minLength={10} maxLength={128} />
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
