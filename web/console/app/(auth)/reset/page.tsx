import Link from "next/link";
import { getT } from "../../../i18n";
import { ResetForm } from "./reset-form";

export default async function ResetPage({
  searchParams,
}: {
  searchParams: Promise<{ token?: string }>;
}) {
  const token = (await searchParams).token ?? "";
  const t = await getT();

  return (
    <main className="rm-card rm-auth-card">
      <h1>{t("reset.title")}</h1>
      {token ? (
        <>
          <p className="rm-lead">{t("reset.lead")}</p>
          <ResetForm token={token} />
        </>
      ) : (
        <p className="rm-alert">{t("reset.missingToken")}</p>
      )}
      <p className="rm-auth-links">
        <Link href="/login">{t("reset.back")}</Link>
      </p>
    </main>
  );
}
