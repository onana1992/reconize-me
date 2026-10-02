import { notFound } from "next/navigation";
import { StatusBadge } from "@kyc/brand";
import { getLocale, getT } from "../../../../../../i18n";
import { consoleApi, type Me } from "../../../../../../lib/api";
import { integrationModeTone, tIntegrationMode } from "../../../../../../lib/environment";
import { parseProduct } from "../../../../../../lib/parse-product";
import { sessionCookieHeader } from "../../../../../../lib/session";
import { formatUtc } from "../../../../../../lib/status";
import { loadIntegration } from "../../load";
import { loadWebhook } from "./webhook-actions";
import { WebhookSettingsForm } from "./webhook-form";

export const dynamic = "force-dynamic";

export default async function IntegrationSettingsPage({
  params,
}: {
  params: Promise<{ product: string; id: string }>;
}) {
  const resolved = await params;
  await parseProduct(Promise.resolve({ product: resolved.product }));
  const [t, locale, result, me, webhook] = await Promise.all([
    getT(),
    getLocale(),
    loadIntegration(resolved.id),
    consoleApi<Me>("/v1/console/me", await sessionCookieHeader()),
    loadWebhook(resolved.id),
  ]);
  if (!result.ok) {
    notFound();
  }
  const integration = result.data;
  const canWrite =
    me.ok && Array.isArray(me.data.permissions) && me.data.permissions.includes("API_KEY_WRITE");

  return (
    <div className="rm-int-settings">
      <section className="rm-int-panel">
        <h2>{t("console.integrations.details")}</h2>
        <dl className="rm-int-dl">
          <div>
            <dt>{t("console.integrations.name")}</dt>
            <dd>{integration.name}</dd>
          </div>
          <div>
            <dt>{t("console.integrations.mode")}</dt>
            <dd>
              <StatusBadge
                label={tIntegrationMode(t, integration.mode)}
                tone={integrationModeTone(integration.mode)}
              />
            </dd>
          </div>
          <div>
            <dt>{t("console.verifications.id")}</dt>
            <dd>
              <code>{integration.id}</code>
            </dd>
          </div>
          <div>
            <dt>{t("console.integrations.created")}</dt>
            <dd>{formatUtc(integration.created_at, locale)}</dd>
          </div>
        </dl>
      </section>
      <section className="rm-int-panel">
        <h2>{t("console.product.integrationsWebhooks")}</h2>
        <WebhookSettingsForm
          integrationId={integration.id}
          initial={webhook.endpoint}
          deliveries={webhook.deliveries}
          canWrite={Boolean(canWrite)}
        />
      </section>
    </div>
  );
}
