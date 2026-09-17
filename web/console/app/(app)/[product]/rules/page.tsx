import { PageHeader } from "../../../../components/page-header";
import { getT } from "../../../../i18n";
import { parseProduct } from "../../../../lib/parse-product";
import { PRODUCTS } from "../../../../lib/products";
import { requireMe } from "../../../../lib/session";

export const dynamic = "force-dynamic";

export default async function ProductRulesPage({ params }: { params: Promise<{ product: string }> }) {
  const productId = await parseProduct(params);
  const product = PRODUCTS[productId];
  await requireMe();
  const t = await getT();

  return (
    <main className="rm-idv">
      <PageHeader
        eyebrow={t(product.navKey)}
        title={t("console.product.tab.rules")}
        lead={t("console.product.settingsLead")}
      />
      {productId === "identity" ? (
        <div className="rm-link-grid">
          <article className="rm-card">
            <h2>{t("console.product.settingsIdentityDecision")}</h2>
            <p className="rm-lead">{t("console.product.settingsIdentityDecisionBody")}</p>
          </article>
          <article className="rm-card">
            <h2>{t("console.product.settingsIdentityDocuments")}</h2>
            <p className="rm-lead">{t("console.product.settingsIdentityDocumentsBody")}</p>
          </article>
          <article className="rm-card">
            <h2>{t("console.product.settingsIdentityFlow")}</h2>
            <p className="rm-lead">{t("console.product.settingsIdentityFlowBody")}</p>
          </article>
        </div>
      ) : (
        <p className="rm-lead">
          {productId === "biometrics" ? t("console.product.settingsBiometrics") : t("console.product.settingsAml")}
        </p>
      )}
    </main>
  );
}
