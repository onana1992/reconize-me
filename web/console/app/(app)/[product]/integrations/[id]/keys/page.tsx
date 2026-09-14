import { notFound } from "next/navigation";
import { getT } from "../../../../../../i18n";
import { parseProduct } from "../../../../../../lib/parse-product";
import { requireMe } from "../../../../../../lib/session";
import { KeysManager } from "../../../../settings/keys/keys-manager";
import { loadIntegration } from "../../load";

export const dynamic = "force-dynamic";

export default async function IntegrationKeysPage({
  params,
}: {
  params: Promise<{ product: string; id: string }>;
}) {
  const resolved = await params;
  await parseProduct(Promise.resolve({ product: resolved.product }));
  const [me, t, result] = await Promise.all([requireMe(), getT(), loadIntegration(resolved.id)]);
  if (!result.ok) {
    notFound();
  }
  const integration = result.data;
  const live = integration.mode === "live";
  const canRead = (me.permissions ?? []).includes("API_KEY_READ");
  const canWrite = (me.permissions ?? []).includes("API_KEY_WRITE");

  return (
    <section className="rm-int-panel">
      <h2>{t("console.product.integrationsKeys")}</h2>
      <p className="rm-lead">
        {live ? t("console.integrations.keysLeadLive") : t("console.integrations.keysLeadTest")}
      </p>
      {!canRead ? (
        <p role="alert" className="rm-alert">
          {t("console.keys.forbidden")}
        </p>
      ) : (
        <KeysManager keys={integration.keys ?? []} canWrite={canWrite} mode={integration.mode} />
      )}
    </section>
  );
}
