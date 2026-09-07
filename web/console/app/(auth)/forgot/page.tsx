import Link from "next/link";
import { getT } from "../../../i18n";
import { ForgotForm } from "./forgot-form";

export default async function ForgotPage() {
  const t = await getT();

  return (
    <main className="rm-card rm-auth-card">
      <h1>{t("forgot.title")}</h1>
      <p className="rm-lead">{t("forgot.lead")}</p>
      <ForgotForm />
      <p className="rm-auth-links">
        <Link href="/login">{t("forgot.back")}</Link>
      </p>
    </main>
  );
}
