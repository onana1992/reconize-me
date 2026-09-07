import Link from "next/link";
import { getT } from "../../../../i18n";
import { ResendForm } from "./resend-form";

export default async function VerifyPendingPage({
  searchParams,
}: {
  searchParams: Promise<{ email?: string }>;
}) {
  const email = (await searchParams).email ?? "";
  const t = await getT();
  const lead = email
    ? t("verifyPending.leadWithEmail").replace("{email}", email)
    : t("verifyPending.lead");

  return (
    <main className="rm-card rm-auth-card">
      <h1>{t("verifyPending.title")}</h1>
      <p className="rm-lead">{lead}</p>
      {email ? <ResendForm email={email} /> : null}
      <p className="rm-auth-links">
        <Link href="/login">{t("verifyPending.login")}</Link>
      </p>
    </main>
  );
}
