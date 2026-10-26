import Link from "next/link";
import { PageHeader } from "../../../components/page-header";
import { getT } from "../../../i18n";
import { consoleApi, type IntegrationListItem, type ScreeningList } from "../../../lib/api";
import { sessionCookieHeader } from "../../../lib/session";
import { AmlTable } from "./aml-table";
import { NewScreeningDrawer } from "./new-screening-drawer";

export async function AmlOverview({ canRead, canWrite }: { canRead: boolean; canWrite: boolean }) {
  const t = await getT();
  const cookie = await sessionCookieHeader();
  const [list, integrations] = canRead
    ? await Promise.all([
        consoleApi<ScreeningList>("/v1/console/screenings?limit=50", cookie),
        consoleApi<IntegrationListItem[]>("/v1/console/integrations", cookie),
      ])
    : [null, null];
  const catalog = integrations?.ok ? integrations.data : [];
  const integrationItems = catalog.filter((item) => item.product === "aml");
  const integrationNames = Object.fromEntries(catalog.map((item) => [item.id, item.name]));
  const integrationModes = Object.fromEntries(catalog.map((item) => [item.id, item.mode]));

  return (
    <main className="rm-idv">
      <PageHeader
        title={t("console.product.tab.verifications")}
        lead={t("console.product.amlListLead")}
        actions={
          canWrite ? (
            integrationItems.length > 0 ? (
              <NewScreeningDrawer
                integrations={integrationItems.map((item) => ({ id: item.id, name: item.name, mode: item.mode }))}
              />
            ) : (
              <Link href="/aml/integrations" className="rm-button">
                {t("console.integrations.create")}
              </Link>
            )
          ) : null
        }
      />
      {canWrite && integrationItems.length === 0 ? (
        <p className="rm-hint">{t("console.verifications.noIntegrations")}</p>
      ) : null}
      {!canRead ? (
        <div className="rm-idv-empty">
          <p className="rm-idv-empty-title">{t("console.product.sessionsLocked")}</p>
        </div>
      ) : list && !list.ok ? (
        <div className="rm-idv-empty" role="alert">
          <p className="rm-idv-empty-title">{t("console.verifications.loadError")}</p>
        </div>
      ) : list?.ok ? (
        <AmlTable
          initialItems={list.data.data}
          initialCursor={list.data.next_cursor ?? null}
          integrationNames={integrationNames}
          integrationModes={integrationModes}
        />
      ) : null}
    </main>
  );
}
