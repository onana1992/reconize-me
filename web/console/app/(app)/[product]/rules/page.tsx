import { PageHeader } from "../../../../components/page-header";
import { getLocale, getT } from "../../../../i18n";
import { consoleApi, type AmlPolicy, type AmlPolicyVersions, type IdvPolicy, type IdvPolicyVersions } from "../../../../lib/api";
import { parseProduct } from "../../../../lib/parse-product";
import { PRODUCTS } from "../../../../lib/products";
import { requireMe, sessionCookieHeader } from "../../../../lib/session";
import { AmlPolicyForm } from "../aml-policy-form";
import { PolicyForm } from "./policy-form";

export const dynamic = "force-dynamic";

export default async function ProductRulesPage({ params }: { params: Promise<{ product: string }> }) {
  const productId = await parseProduct(params);
  const product = PRODUCTS[productId];
  const me = await requireMe();
  const t = await getT();

  if (productId === "aml") {
    const cookie = await sessionCookieHeader();
    const locale = await getLocale();
    const [policy, versions] = await Promise.all([
      consoleApi<AmlPolicy>("/v1/console/aml/policy", cookie),
      consoleApi<AmlPolicyVersions>("/v1/console/aml/policy/versions", cookie),
    ]);
    const canWrite = me.permissions.includes("POLICY_WRITE");
    return (
      <main className="rm-rules-page">
        <PageHeader
          eyebrow={t(product.navKey)}
          title={t("console.product.tab.rules")}
          lead={t("console.product.settingsAml")}
        />
        {policy.ok ? (
          <AmlPolicyForm
            policy={policy.data}
            versions={versions.ok ? versions.data.versions : []}
            canWrite={canWrite}
            locale={locale}
          />
        ) : (
          <p className="rm-lead">{t("console.rules.loadError")}</p>
        )}
      </main>
    );
  }

  if (productId !== "identity") {
    return (
      <main className="rm-idv">
        <PageHeader
          eyebrow={t(product.navKey)}
          title={t("console.product.tab.rules")}
          lead={t("console.product.settingsLead")}
        />
        <p className="rm-lead">
          {productId === "biometrics" ? t("console.product.settingsBiometrics") : t("console.product.settingsAml")}
        </p>
      </main>
    );
  }

  const cookie = await sessionCookieHeader();
  const [locale, policy, versions] = await Promise.all([
    getLocale(),
    consoleApi<IdvPolicy>("/v1/console/identity/policy", cookie),
    consoleApi<IdvPolicyVersions>("/v1/console/identity/policy/versions", cookie),
  ]);
  const canWrite = me.permissions.includes("POLICY_WRITE");

  return (
    <main className="rm-rules-page">
      <PageHeader
        eyebrow={t(product.navKey)}
        title={t("console.product.tab.rules")}
        lead={t("console.product.settingsLead")}
      />
      {policy.ok ? (
        <PolicyForm
          policy={policy.data}
          versions={versions.ok ? versions.data.versions : []}
          canWrite={canWrite}
          locale={locale}
        />
      ) : (
        <p className="rm-lead">{t("console.rules.loadError")}</p>
      )}
    </main>
  );
}
