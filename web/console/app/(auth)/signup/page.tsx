import Link from "next/link";
import { getLocale, getT } from "../../../i18n";
import { sitePrivacyUrl } from "../../../lib/site";
import { SignupForm } from "./signup-form";

export default async function SignupPage({
  searchParams,
}: {
  searchParams: Promise<{ invite?: string; email?: string }>;
}) {
  const params = await searchParams;
  const invite = params.invite ?? "";
  const email = params.email ?? "";
  const locale = await getLocale();
  const t = await getT();

  return (
    <main className="rm-card rm-auth-card">
      <h1>{t("signup.title")}</h1>
      <p className="rm-lead">{invite ? t("signup.leadInvite") : t("signup.lead")}</p>
      <SignupForm invite={invite} email={email} />
      <p className="rm-auth-legal">
        {t("signup.privacyBefore")}
        <a href={sitePrivacyUrl(locale)} target="_blank" rel="noreferrer">
          {t("signup.privacyLink")}
        </a>
        {t("signup.privacyAfter")}
      </p>
      <p className="rm-auth-links">
        {t("signup.hasAccount")} <Link href="/login">{t("signup.login")}</Link>
      </p>
    </main>
  );
}
