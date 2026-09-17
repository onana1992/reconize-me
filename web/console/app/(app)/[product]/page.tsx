import Link from "next/link";
import { StatusBadge } from "@kyc/brand";
import { PageHeader } from "../../../components/page-header";
import { getLocale, getT } from "../../../i18n";
import { formatCount } from "../../../lib/pricing";
import { parseProduct } from "../../../lib/parse-product";
import { PRODUCTS, productTabHref } from "../../../lib/products";
import { requireMe } from "../../../lib/session";
import { IdentityOverview } from "./identity-overview";

export default async function ProductOverviewPage({
  params,
  searchParams,
}: {
  params: Promise<{ product: string }>;
  searchParams: Promise<{ integration?: string }>;
}) {
  const productId = await parseProduct(params);
  const product = PRODUCTS[productId];
  const me = await requireMe();
  const t = await getT();
  const locale = await getLocale();
  const canReadSessions = me.permissions.includes("VERIFICATION_READ");
  const canWriteSessions = me.permissions.includes("VERIFICATION_WRITE");
  const integrationId = (await searchParams).integration?.trim();

  if (productId === "identity") {
    return (
      <IdentityOverview canRead={canReadSessions} canWrite={canWriteSessions} integrationId={integrationId} />
    );
  }

  return (
    <main>
      <PageHeader
        eyebrow={t("console.nav.services")}
        title={t("console.product.tab.verifications")}
        lead={t(product.leadKey)}
        actions={
          <StatusBadge
            label={product.metered ? t("console.product.metered") : t("console.nav.soon")}
            tone={product.metered ? "info" : "neutral"}
          />
        }
      />
      <section className="rm-kpi-grid" aria-label={t("console.env.label")}>
        <article className="rm-card rm-kpi">
          <p className="rm-eyebrow">{t("console.product.tab.integrations")}</p>
          <p className="rm-kpi-value">{formatCount(locale, 0)}</p>
          <p className="rm-lead">{t("console.product.billingNotSold")}</p>
        </article>
      </section>
      <section className="rm-section">
        <h2>{t("console.product.tabsLabel")}</h2>
        <div className="rm-link-grid">
          <Link href={productTabHref(productId, "integrations")} className="rm-card rm-link-card">
            <h3>{t("console.product.tab.integrations")}</h3>
            <p>{t("console.product.integrationsLead")}</p>
          </Link>
          <Link href={productTabHref(productId, "rules")} className="rm-card rm-link-card">
            <h3>{t("console.product.tab.rules")}</h3>
            <p>{t("console.product.settingsLead")}</p>
          </Link>
        </div>
      </section>
    </main>
  );
}
