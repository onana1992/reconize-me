import { PageHeader } from "../../../../components/page-header";
import { getT } from "../../../../i18n";
import { resolveEnvironment } from "../../../../lib/environment";
import { parseProduct } from "../../../../lib/parse-product";
import { PRODUCTS } from "../../../../lib/products";

export default async function ProductConfigurationPage({
  params,
  searchParams,
}: {
  params: Promise<{ product: string }>;
  searchParams: Promise<{ env?: string }>;
}) {
  const productId = await parseProduct(params);
  const product = PRODUCTS[productId];
  const env = resolveEnvironment((await searchParams).env);
  const t = await getT();

  return (
    <main>
      <PageHeader
        eyebrow={t(product.navKey)}
        title={t("console.product.tab.configuration")}
        lead={t("console.product.settingsLead")}
      />
      <p className="rm-lead">
        {env === "live" ? t("console.product.settingsEnvLive") : t("console.product.settingsEnvSandbox")}
      </p>
      {productId === "identity" ? (
        <div className="rm-link-grid">
          <article className="rm-card">
            <h3>{t("console.product.settingsIdentityDecision")}</h3>
            <p className="rm-lead">{t("console.product.settingsIdentityDecisionBody")}</p>
          </article>
          <article className="rm-card">
            <h3>{t("console.product.settingsIdentityDocuments")}</h3>
            <p className="rm-lead">{t("console.product.settingsIdentityDocumentsBody")}</p>
          </article>
          <article className="rm-card">
            <h3>{t("console.product.settingsIdentityFlow")}</h3>
            <p className="rm-lead">{t("console.product.settingsIdentityFlowBody")}</p>
          </article>
        </div>
      ) : (
        <div className="rm-empty">
          <p>{productId === "biometrics" ? t("console.product.settingsBiometrics") : t("console.product.settingsAml")}</p>
        </div>
      )}
    </main>
  );
}
