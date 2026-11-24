import Link from "next/link";
import { PageHeader } from "../../../components/page-header";
import { getT } from "../../../i18n";
import { consoleApi, type AuthenticationList, type IntegrationListItem } from "../../../lib/api";
import { isIntegrationId } from "../../../lib/products";
import { sessionCookieHeader } from "../../../lib/session";
import { AuthenticationTable } from "./authentication-table";
import { NewAuthenticationDrawer } from "./new-authentication-drawer";

export async function AuthenticationOverview({
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
        consoleApi<AuthenticationList>(
          filterId
            ? `/v1/console/authentications?limit=100&integration_id=${encodeURIComponent(filterId)}`
            : "/v1/console/authentications?limit=100",
          cookie,
        ),
        consoleApi<IntegrationListItem[]>("/v1/console/integrations", cookie),
      ])
    : [null, null];
  const integrationItems = (integrations?.ok ? integrations.data : []).filter(
    (item) => item.product === "authentication",
  );
  const integrationNames = Object.fromEntries(integrationItems.map((item) => [item.id, item.name]));
  const filterName = filterId ? integrationNames[filterId] : undefined;

  return (
    <main className="rm-idv">
      <PageHeader
        title={t("console.product.tab.authentications")}
        actions={
          canWrite ? (
            integrationItems.length > 0 ? (
              <NewAuthenticationDrawer
                integrations={integrationItems.map((item) => ({ id: item.id, name: item.name, mode: item.mode }))}
                defaultIntegrationId={filterId}
              />
            ) : (
              <Link href="/authentication/integrations" className="rm-button">
                {t("console.integrations.create")}
              </Link>
            )
          ) : null
        }
      />
      {canWrite && integrationItems.length === 0 ? (
        <p className="rm-hint">{t("console.authentications.noIntegrations")}</p>
      ) : null}
      {filterId ? (
        <p className="rm-hint">
          {t("console.authentications.filteredTo").replace("{name}", filterName ?? filterId)}{" "}
          <Link href="/authentication">{t("console.authentications.clearIntegrationFilter")}</Link>
        </p>
      ) : null}
      {!canRead ? (
        <div className="rm-idv-empty">
          <p className="rm-idv-empty-title">{t("console.authentications.locked")}</p>
        </div>
      ) : sessions && !sessions.ok ? (
        <div className="rm-idv-empty" role="alert">
          <p className="rm-idv-empty-title">{t("console.authentications.loadError")}</p>
        </div>
      ) : sessions?.ok ? (
        <AuthenticationTable
          initialItems={sessions.data.data}
          initialCursor={sessions.data.next_cursor ?? null}
          integrationId={filterId}
          integrationNames={integrationNames}
        />
      ) : null}
    </main>
  );
}
