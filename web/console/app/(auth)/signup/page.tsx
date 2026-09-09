import Link from "next/link";
import { redirect } from "next/navigation";
import { getLocale, getT } from "../../../i18n";
import { peekInvite } from "../../../lib/api";
import { redirectHomeIfSignedIn } from "../../../lib/session";
import { sitePrivacyUrl } from "../../../lib/site";
import { InvalidInvite } from "./invalid-invite";
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

  if (invite) {
    const preview = await peekInvite(invite);
    if (!preview.ok) {
      return <InvalidInvite title={t("signup.invalidTitle")} lead={t("signup.invalidLead")} login={t("signup.login")} />;
    }
    const next = new URLSearchParams({
      email: preview.data.email,
      invite,
    });
    redirect(`/signup/setup?${next}`);
  }

  await redirectHomeIfSignedIn();

  return (
    <main className="rm-card rm-auth-card">
      <h1>{t("signup.title")}</h1>
      <p className="rm-lead">{t("signup.lead")}</p>
      <SignupForm email={email} />
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
