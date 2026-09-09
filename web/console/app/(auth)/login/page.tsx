import Link from "next/link";
import { getT } from "../../../i18n";
import { redirectHomeIfSignedIn, safeNextPath } from "../../../lib/session";
import { LoginForm } from "./login-form";

export default async function LoginPage({
  searchParams,
}: {
  searchParams: Promise<{ next?: string }>;
}) {
  const params = await searchParams;
  const next = safeNextPath(params.next);
  await redirectHomeIfSignedIn(next);
  const t = await getT();

  return (
    <main className="rm-card rm-auth-card">
      <h1>{t("login.title")}</h1>
      <p className="rm-lead">{t("login.lead")}</p>
      <LoginForm next={next} />
      <p className="rm-auth-links">
        <Link href="/forgot">{t("login.forgot")}</Link>
        {" · "}
        <Link href="/signup">{t("login.signup")}</Link>
      </p>
    </main>
  );
}
