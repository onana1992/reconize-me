import Link from "next/link";
import { CopyButton } from "../../../components/copy-button";
import { getT } from "../../../i18n";
import { verifyEmail } from "../actions";

export default async function VerifyPage({
  searchParams,
}: {
  searchParams: Promise<{ token?: string }>;
}) {
  const token = (await searchParams).token ?? "";
  const t = await getT();

  if (!token) {
    return (
      <main className="rm-card rm-auth-card">
        <h1>{t("verify.missingTitle")}</h1>
        <p className="rm-alert">{t("verify.missingToken")}</p>
        <p className="rm-auth-links">
          <Link href="/login">{t("verify.login")}</Link>
          {" · "}
          <Link href="/signup">{t("verify.signup")}</Link>
        </p>
      </main>
    );
  }

  const result = await verifyEmail(token);

  if (!result.ok) {
    return (
      <main className="rm-card rm-auth-card">
        <h1>{t("verify.invalidTitle")}</h1>
        <p className="rm-lead">{t("verify.invalidLead")}</p>
        <p className="rm-alert">
          {result.code === "invalid_or_expired_token" ? t("verify.invalidToken") : result.message}
        </p>
        <p className="rm-auth-links">
          <Link href="/login">{t("verify.login")}</Link>
          {" · "}
          <Link href="/signup">{t("verify.signup")}</Link>
        </p>
      </main>
    );
  }

  const key = result.data.key;

  return (
    <main className="rm-card rm-auth-card">
      <h1>{t("verify.title")}</h1>
      <p className="rm-lead">{key ? t("verify.leadKey") : t("verify.lead")}</p>
      {key ? (
        <label>
          {t("verify.keyTitle")}
          <span className="rm-secret">
            <code>{key}</code>
            <CopyButton value={key} label={t("common.copyKey")} />
          </span>
        </label>
      ) : (
        <p className="rm-notice">{t("verify.activated")}</p>
      )}
      <p className="rm-auth-links">
        <Link href="/login">{t("verify.login")}</Link>
      </p>
    </main>
  );
}
