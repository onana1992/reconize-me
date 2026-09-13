import { redirect } from "next/navigation";
import { getT } from "../../../../i18n";
import { peekInvite } from "../../../../lib/api";
import { tRole } from "../../../../lib/labels";
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
  let lead = "";

  if (invite) {
    const preview = await peekInvite(invite);
    if (!preview.ok) {
      return <InvalidInvite title={t("signup.invalidTitle")} lead={t("signup.invalidLead")} login={t("signup.login")} />;
    }
    email = preview.data.email;
    const inviter = preview.data.invited_by_name?.trim() || t("setup.inviterFallback");
    lead = t("setup.leadInvite")
      .replace("{email}", email)
      .replace("{org}", preview.data.organization_name)
      .replace("{inviter}", inviter)
      .replace("{role}", tRole(t, preview.data.role));
  } else {
    if (!email) {
      redirect("/signup");
    }
    await redirectHomeIfSignedIn();
    lead = t("setup.leadWithEmail").replace("{email}", email);
  }

  return (
    <main className="su-card">
      <h1>{t("setup.title")}</h1>
      <p className="su-lead">{lead}</p>
      <SetupForm email={email} invite={invite} />
    </main>
  );
}
