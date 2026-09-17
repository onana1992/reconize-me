import Link from "next/link";
import { PageHeader } from "../../../components/page-header";
import { getT } from "../../../i18n";
import { consoleApi, type IntegrationListItem, type VerificationList } from "../../../lib/api";
import { isIntegrationId } from "../../../lib/products";
import { sessionCookieHeader } from "../../../lib/session";
import { IdentityTable } from "./identity-table";
import { NewVerificationDrawer } from "./new-verification-drawer";

export async function IdentityOverview({
  canRead,
  canWrite,
  integrationId,
}: {
  canRead: boolean;
  canWrite: boolean;
  integrationId?: string;
}) {
  const t = await getT();
  const cookie = await sessionCookieHeader();
  const filterId = integrationId && isIntegrationId(integrationId) ? integrationId : undefined;
  const [sessions, integrations] = canRead
    ? await Promise.all([
        consoleApi<VerificationList>(
          filterId
            ? `/v1/console/verifications?limit=100&integration_id=${encodeURIComponent(filterId)}`
            : "/v1/console/verifications?limit=100",
          cookie,
        ),
        consoleApi<IntegrationListItem[]>("/v1/console/integrations", cookie),
      ])
    : [null, null];
  const integrationItems = (integrations?.ok ? integrations.data : []).filter((item) => item.product === "identity");
  const integrationNames = Object.fromEntries(integrationItems.map((item) => [item.id, item.name]));
  const filterName = filterId ? integrationNames[filterId] : undefined;

  return (
    <main className="rm-idv">
      <PageHeader
        title={t("console.product.tab.verifications")}
        lead={t("console.verifications.lead")}
        actions={
          canWrite ? (
            integrationItems.length > 0 ? (
              <NewVerificationDrawer
                product="identity"
                integrations={integrationItems.map((item) => ({ id: item.id, name: item.name, mode: item.mode }))}
                defaultIntegrationId={filterId}
              />
            ) : (
              <Link href="/identity/integrations" className="rm-button">
                {t("console.integrations.create")}
              </Link>
            )
          ) : null
        }
      />
      {canWrite && integrationItems.length === 0 ? (
        <p className="rm-hint">{t("console.verifications.noIntegrations")}</p>
      ) : null}
      {filterId ? (
        <p className="rm-hint">
          {t("console.verifications.filteredTo").replace("{name}", filterName ?? filterId)}{" "}
          <Link href="/identity">{t("console.verifications.clearIntegrationFilter")}</Link>
        </p>
      ) : null}
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
          integrationId={filterId}
          integrationNames={integrationNames}
        />
      ) : null}
    </main>
  );
}
