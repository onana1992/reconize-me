import Link from "next/link";
import { redirect } from "next/navigation";

import { StatusBadge } from "@kyc/brand";
import { PageHeader } from "../../../../components/page-header";
import { getLocale, getT } from "../../../../i18n";
import { consoleApi, type IntegrationListItem } from "../../../../lib/api";
import { integrationModeTone, tIntegrationMode } from "../../../../lib/environment";
import { parseProduct } from "../../../../lib/parse-product";
import { isIntegrationId, PRODUCTS } from "../../../../lib/products";
import { requireMe, sessionCookieHeader } from "../../../../lib/session";
import { formatUtc } from "../../../../lib/status";
import { CreateIntegrationButton } from "./create-form";

export const dynamic = "force-dynamic";

export default async function ProductIntegrationsPage({
  params,
  searchParams,
}: {
  params: Promise<{ product: string }>;
  searchParams: Promise<{ id?: string }>;
}) {
  const productId = await parseProduct(params);
  const product = PRODUCTS[productId];
  const selectedId = (await searchParams).id?.trim();
  if (selectedId && isIntegrationId(selectedId)) {
    redirect(`${product.href}/integrations/${selectedId}`);
  }
  const me = await requireMe();
  const [t, locale] = await Promise.all([getT(), getLocale()]);
  const canWrite = (me.permissions ?? []).includes("API_KEY_WRITE");

  if (!product.metered) {
    return (
      <main className="rm-idv">
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

  const list = await consoleApi<IntegrationListItem[]>("/v1/console/integrations", await sessionCookieHeader());

  return (
    <main className="rm-idv">
      <PageHeader
        eyebrow={t(product.navKey)}
        title={t("console.product.tab.integrations")}
        lead={t("console.integrations.lead")}
        actions={canWrite ? <CreateIntegrationButton product={productId} /> : null}
      />
      {!list.ok ? (
        <p role="alert" className="rm-alert">
          {list.message ?? t("console.integrations.loadError")}
        </p>
      ) : list.data.length === 0 ? (
        <div className="rm-empty">
          <p>{t("console.integrations.empty")}</p>
        </div>
      ) : (
        <ul className="rm-int-list">
          {list.data.map((item) => (
            <li key={item.id}>
              <Link href={`${product.href}/integrations/${item.id}`} className="rm-int-card">
                <div className="rm-int-card-copy">
                  <span className="rm-int-card-name">{item.name}</span>
                  <span className="rm-int-card-meta">{formatUtc(item.created_at, locale)}</span>
                </div>
                <div className="rm-int-card-aside">
                  <StatusBadge label={tIntegrationMode(t, item.mode)} tone={integrationModeTone(item.mode)} />
                  <span className="rm-catalog-chevron" aria-hidden="true" />
                </div>
              </Link>
            </li>
          ))}
        </ul>
      )}
    </main>
  );
}
