import Link from "next/link";
import { StatusBadge } from "@kyc/brand";
import { NavIcon } from "../../components/nav-icons";
import { PageHeader } from "../../components/page-header";
import { getLocale, getT } from "../../i18n";
import { formatCount, formatMoney } from "../../lib/pricing";
import { PRODUCTS, PRODUCT_IDS, type ProductId } from "../../lib/products";
import { requireMe } from "../../lib/session";

const HOME_LEAD: Record<ProductId, "console.home.idvHomeLead" | "console.home.biometricHomeLead" | "console.home.amlHomeLead"> =
  {
    identity: "console.home.idvHomeLead",
    biometrics: "console.home.biometricHomeLead",
    aml: "console.home.amlHomeLead",
  };

export default async function HomePage() {
  const me = await requireMe();
  const t = await getT();
  const locale = await getLocale();
  const sandboxCount = 0;
  const liveCount = 0;
  const balance = 0;
  const sandboxLabel = formatCount(locale, sandboxCount);
  const liveLabel = formatCount(locale, liveCount);

  return (
    <div className="rm-page">
      <main className="rm-dash">
        <PageHeader title={me.organization.name} lead={t("console.home.period")} />
        <section className="rm-card rm-stats" aria-label={t("console.env.label")}>
          <article className="rm-stat">
            <p className="rm-eyebrow">{t("console.env.sandbox")}</p>
            <p className="rm-stat-value">{sandboxLabel}</p>
            <p className="rm-stat-hint">{t("console.home.sandboxHint")}</p>
          </article>
          <article className="rm-stat">
            <p className="rm-eyebrow">{t("console.env.live")}</p>
            <p className="rm-stat-value">{liveLabel}</p>
            <p className="rm-stat-hint">{t("console.home.liveHint")}</p>
          </article>
          <Link href="/settings/billing" className="rm-stat">
            <p className="rm-eyebrow">{t("console.billing.balance")}</p>
            <p className="rm-stat-value">{formatMoney(locale, balance)}</p>
            <p className="rm-stat-hint">{t("console.home.balanceHint")}</p>
          </Link>
        </section>
        <section className="rm-section">
          <h2>{t("console.home.platformTitle")}</h2>
          <ul className="rm-catalog">
            {PRODUCT_IDS.map((id) => {
              const product = PRODUCTS[id];
              const detail = product.metered
                ? t("console.home.usage").replace("{sandbox}", sandboxLabel).replace("{live}", liveLabel)
                : t(HOME_LEAD[id]);
              return (
                <li key={product.id}>
                  <Link href={product.href} className="rm-catalog-item">
                    <span className="rm-catalog-ico">
                      <NavIcon name={product.icon} />
                    </span>
                    <span className="rm-catalog-copy">
                      <h3 className="rm-catalog-title">{t(product.navKey)}</h3>
                      <span className="rm-catalog-detail">{detail}</span>
                    </span>
                    <span className="rm-catalog-meta">
                      <StatusBadge
                        label={product.metered ? t("console.home.available") : t("console.nav.soon")}
                        tone={product.metered ? "info" : "neutral"}
                      />
                      <span className="rm-catalog-chevron" aria-hidden="true" />
                    </span>
                  </Link>
                </li>
              );
            })}
          </ul>
        </section>
      </main>
    </div>
  );
}
