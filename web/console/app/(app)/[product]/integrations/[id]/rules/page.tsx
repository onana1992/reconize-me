import { notFound, redirect } from "next/navigation";
import { getLocale, getT } from "../../../../../../i18n";
import {
  consoleApi,
  type AmlPolicy,
  type AmlPolicyVersions,
  type IdvPolicy,
  type IdvPolicyVersions,
} from "../../../../../../lib/api";
import { parseProduct } from "../../../../../../lib/parse-product";
import { requireMe, sessionCookieHeader } from "../../../../../../lib/session";
import { AmlPolicyForm } from "../../../aml-policy-form";
import { PolicyForm } from "../../../rules/policy-form";
import { loadIntegration } from "../../load";

export const dynamic = "force-dynamic";

export default async function IntegrationRulesPage({
  params,
}: {
  params: Promise<{ product: string; id: string }>;
}) {
  const resolved = await params;
  const productId = await parseProduct(Promise.resolve({ product: resolved.product }));
  if (productId !== "identity" && productId !== "aml") {
    notFound();
  }
  const [me, t, locale, result] = await Promise.all([
    requireMe(),
    getT(),
    getLocale(),
    loadIntegration(resolved.id),
  ]);
  if (!result.ok) {
    notFound();
  }
  const integration = result.data;
  if (integration.product !== productId) {
    notFound();
  }
  const canWrite = me.permissions.includes("POLICY_WRITE");

  if (integration.mode !== "live") {
    redirect(`/${productId}/integrations/${integration.id}`);
  }

  const cookie = await sessionCookieHeader();
  const query = `integration_id=${encodeURIComponent(integration.id)}`;

  if (productId === "aml") {
    const [policy, versions] = await Promise.all([
      consoleApi<AmlPolicy>(`/v1/console/aml/policy?${query}`, cookie),
      consoleApi<AmlPolicyVersions>(`/v1/console/aml/policy/versions?${query}`, cookie),
    ]);
    if (!policy.ok) {
      return <p className="rm-lead">{t("console.rules.loadError")}</p>;
    }
    return (
      <AmlPolicyForm
        integrationId={integration.id}
        policy={policy.data}
        versions={versions.ok ? versions.data.versions : []}
        canWrite={canWrite}
        locale={locale}
      />
    );
  }

  const [policy, versions] = await Promise.all([
    consoleApi<IdvPolicy>(`/v1/console/identity/policy?${query}`, cookie),
    consoleApi<IdvPolicyVersions>(`/v1/console/identity/policy/versions?${query}`, cookie),
  ]);
  if (!policy.ok) {
    return <p className="rm-lead">{t("console.rules.loadError")}</p>;
  }
  return (
    <PolicyForm
      integrationId={integration.id}
      policy={policy.data}
      versions={versions.ok ? versions.data.versions : []}
      canWrite={canWrite}
      locale={locale}
    />
  );
}
