import { PageHeader } from "../../../components/page-header";
import { getT } from "../../../i18n";
import { consoleApi, type VerificationList } from "../../../lib/api";
import { type ApiEnvironment } from "../../../lib/environment";
import { sessionCookieHeader } from "../../../lib/session";
import { IdentityTable } from "./identity-table";
import { NewVerificationDrawer } from "./new-verification-drawer";

export async function IdentityOverview({
  env,
  live,
  canRead,
  canWrite,
}: {
  env: ApiEnvironment;
  live: boolean;
  canRead: boolean;
  canWrite: boolean;
}) {
  const t = await getT();
  const sessions = canRead
    ? await consoleApi<VerificationList>("/v1/console/verifications?limit=100", await sessionCookieHeader())
    : null;
  const newVerification = canWrite ? <NewVerificationDrawer product="identity" env={env} live={live} /> : null;

  return (
    <main className="rm-idv">
      <PageHeader title={t("console.nav.idv")} lead={t("console.verifications.lead")} actions={newVerification} />
      {!canRead ? (
        <div className="rm-idv-empty">
          <p className="rm-idv-empty-title">{t("console.product.sessionsLocked")}</p>
        </div>
      ) : sessions && !sessions.ok ? (
        <div className="rm-idv-empty" role="alert">
          <p className="rm-idv-empty-title">{t("console.verifications.loadError")}</p>
        </div>
      ) : sessions?.ok ? (
        <IdentityTable
          initialItems={sessions.data.items}
          initialCursor={sessions.data.next_cursor ?? null}
          env={env}
        />
      ) : null}
    </main>
  );
}
