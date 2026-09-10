import { PageHeader } from "../../../../components/page-header";
import { getT } from "../../../../i18n";
import { consoleApi, type ApiKeyItem } from "../../../../lib/api";
import { resolveEnvironment } from "../../../../lib/environment";
import { parseProduct } from "../../../../lib/parse-product";
import { PRODUCTS } from "../../../../lib/products";
import { requireMe, sessionCookieHeader } from "../../../../lib/session";
import { KeysManager } from "../../settings/keys/keys-manager";

export const dynamic = "force-dynamic";

export default async function ProductIntegrationsPage({
  params,
  searchParams,
}: {
  params: Promise<{ product: string }>;
  searchParams: Promise<{ env?: string }>;
}) {
  const productId = await parseProduct(params);
  const product = PRODUCTS[productId];
  const env = resolveEnvironment((await searchParams).env);
  const me = await requireMe();
  const t = await getT();
  const permissions = me.permissions ?? [];
  const canRead = permissions.includes("API_KEY_READ");
  const canWrite = permissions.includes("API_KEY_WRITE");

  if (!product.metered) {
    return (
      <main>
        <PageHeader
          eyebrow={t(product.navKey)}
          title={t("console.product.tab.integrations")}
          lead={t("console.product.integrationsLead")}
        />
        <div className="rm-empty">
          <p>{t("console.product.integrationsUnavailable")}</p>
        </div>
      </main>
    );
  }

  const keys = canRead ? await consoleApi<ApiKeyItem[]>("/v1/console/api-keys", await sessionCookieHeader()) : null;

  return (
    <main>
      <PageHeader
        eyebrow={t(product.navKey)}
        title={t("console.product.tab.integrations")}
        lead={
          env === "live" ? t("console.product.integrationsKeysBodyLive") : t("console.product.integrationsKeysBodySandbox")
        }
      />
      <section className="rm-section">
        <h2>{t("console.product.integrationsKeys")}</h2>
        {!canRead ? (
          <p role="alert" className="rm-alert">
            {t("console.keys.forbidden")}
          </p>
        ) : !keys?.ok ? (
          <p role="alert" className="rm-alert">
            {keys?.message ?? t("console.keys.loadError")}
          </p>
        ) : (
          <KeysManager keys={keys.data} canWrite={canWrite} environment={env} />
        )}
      </section>
      <section className="rm-section">
        <h2>{t("console.product.integrationsWebhooks")}</h2>
        <p className="rm-lead">{t("console.product.integrationsWebhooksBody")}</p>
      </section>
    </main>
  );
}
