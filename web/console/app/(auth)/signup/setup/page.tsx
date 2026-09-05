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

  return (
    <main className="su-card">
      <div
        className="su-progress"
        role="progressbar"
        aria-valuemin={1}
        aria-valuemax={4}
        aria-valuenow={1}
        aria-label={t("setup.progress")}
      >
        <span data-on="true" />
        <span />
        <span />
        <span />
      </div>
      <h1>{t("setup.title")}</h1>
      <p className="su-lead">{t("setup.lead")}</p>
      <SetupForm email={email} invite={invite} />
    </main>
  );
}
