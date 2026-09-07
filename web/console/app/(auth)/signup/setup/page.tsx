import { redirect } from "next/navigation";
import { getT } from "../../../../i18n";
import { SetupForm } from "./setup-form";
import "./setup.css";

export default async function SignupSetupPage({
  searchParams,
}: {
  searchParams: Promise<{ email?: string; invite?: string }>;
}) {
  const params = await searchParams;
  const email = (params.email ?? "").trim();
  const invite = params.invite ?? "";
  const t = await getT();

  if (!email) {
    redirect(invite ? `/signup?invite=${encodeURIComponent(invite)}` : "/signup");
  }

  const lead = (invite ? t("setup.leadInvite") : t("setup.leadWithEmail")).replace(
    "{email}",
    email,
  );

  return (
    <main className="su-card">
      <h1>{t("setup.title")}</h1>
      <p className="su-lead">{lead}</p>
      <SetupForm email={email} invite={invite} />
    </main>
  );
}
