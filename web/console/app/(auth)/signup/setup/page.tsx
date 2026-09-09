import { redirect } from "next/navigation";
import { getT } from "../../../../i18n";
import { peekInvite } from "../../../../lib/api";
import { redirectHomeIfSignedIn } from "../../../../lib/session";
import { InvalidInvite } from "../invalid-invite";
import { SetupForm } from "./setup-form";
import "./setup.css";

export default async function SignupSetupPage({
  searchParams,
}: {
  searchParams: Promise<{ email?: string; invite?: string }>;
}) {
  const params = await searchParams;
  const invite = params.invite ?? "";
  const t = await getT();
  let email = (params.email ?? "").trim();

  if (invite) {
    const preview = await peekInvite(invite);
    if (!preview.ok) {
      return <InvalidInvite title={t("signup.invalidTitle")} lead={t("signup.invalidLead")} login={t("signup.login")} />;
    }
    email = preview.data.email;
  } else {
    if (!email) {
      redirect("/signup");
    }
    await redirectHomeIfSignedIn();
  }

  const lead = (invite ? t("setup.leadInvite") : t("setup.leadWithEmail")).replace("{email}", email);

  return (
    <main className="su-card">
      <h1>{t("setup.title")}</h1>
      <p className="su-lead">{lead}</p>
      <SetupForm email={email} invite={invite} />
    </main>
  );
}
