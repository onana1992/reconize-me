import Link from "next/link";
import { notFound } from "next/navigation";
import { StatusBadge } from "@kyc/brand";
import { getT } from "../../../../../../i18n";
import { API_BASE } from "../../../../../../lib/api";
import { parseProduct } from "../../../../../../lib/parse-product";
import { integrationTabHref } from "../../../../../../lib/products";
import { loadIntegration } from "../../load";
import { Snippet } from "./snippet";

export const dynamic = "force-dynamic";

function curlCreate(apiBase: string, keyPrefix: string): string {
  return `curl -X POST "${apiBase}/v1/verifications" \\
  -H "Authorization: Bearer ${keyPrefix}" \\
  -H "Content-Type: application/json" \\
  -H "Idempotency-Key: order-123" \\
  -d '{
    "external_id": "order-123",
    "applicant": {
      "first_name": "Ada",
      "last_name": "Lovelace",
      "email": "ada@example.com"
    }
  }'`;
}

function curlGet(apiBase: string, keyPrefix: string): string {
  return `curl "${apiBase}/v1/verifications/{id}" \\
  -H "Authorization: Bearer ${keyPrefix}"`;
}

const REDIRECT_SNIPPET = `window.location = hosted_url;`;

const INCONTEXT_SNIPPET = `createFrame({ url: hosted_url });`;

export default async function IntegrationInstallPage({
  params,
}: {
  params: Promise<{ product: string; id: string }>;
}) {
  const resolved = await params;
  const productId = await parseProduct(Promise.resolve({ product: resolved.product }));
  const [t, result] = await Promise.all([getT(), loadIntegration(resolved.id)]);
  if (!result.ok) {
    notFound();
  }
  const integration = result.data;
  const apiBase = API_BASE.replace(/\/$/, "");
  const keyPrefix = integration.mode === "live" ? "ky_live_…" : "ky_test_…";

  return (
    <div className="rm-int-install">
      <p className="rm-lead">{t("console.integrations.install.lead")}</p>
      <p className="rm-lead">
        <Link href={integrationTabHref(productId, integration.id, "keys")}>
          {t("console.integrations.install.keysLink")}
        </Link>
      </p>
      <section className="rm-int-panel">
        <h2>{t("console.integrations.install.apiTitle")}</h2>
        <p className="rm-lead">{t("console.integrations.install.apiBody")}</p>
        <Snippet label={t("console.integrations.install.curlLabel")} value={curlCreate(apiBase, keyPrefix)} />
      </section>
      <section className="rm-int-panel">
        <h2>{t("console.integrations.install.openTitle")}</h2>
        <p className="rm-lead">{t("console.integrations.install.openBody")}</p>
        <Snippet label={t("console.integrations.install.openLabel")} value={REDIRECT_SNIPPET} />
      </section>
      <section className="rm-int-panel">
        <div className="rm-int-panel-head">
          <h2>{t("console.integrations.install.incontextTitle")}</h2>
          <StatusBadge label={t("console.integrations.install.incontextSoon")} tone="neutral" />
        </div>
        <p className="rm-lead">{t("console.integrations.install.incontextBody")}</p>
        <Snippet label={t("console.integrations.install.incontextLabel")} value={INCONTEXT_SNIPPET} />
      </section>
      <section className="rm-int-panel">
        <h2>{t("console.integrations.install.decisionTitle")}</h2>
        <p className="rm-lead">{t("console.integrations.install.decisionBody")}</p>
        <Snippet label={t("console.integrations.install.curlLabel")} value={curlGet(apiBase, keyPrefix)} />
      </section>
    </div>
  );
}
