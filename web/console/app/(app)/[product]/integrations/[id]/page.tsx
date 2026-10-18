import { notFound } from "next/navigation";
import { API_BASE } from "../../../../../lib/api";
import { parseProduct } from "../../../../../lib/parse-product";
import { integrationTabHref } from "../../../../../lib/products";
import { loadIntegration } from "../load";
import { InstallGuide } from "./install-guide";

export const dynamic = "force-dynamic";

export default async function IntegrationInstallPage({
  params,
}: {
  params: Promise<{ product: string; id: string }>;
}) {
  const resolved = await params;
  const productId = await parseProduct(Promise.resolve({ product: resolved.product }));
  const result = await loadIntegration(resolved.id);
  if (!result.ok) {
    notFound();
  }
  const integration = result.data;
  const apiBase = API_BASE.replace(/\/$/, "");
  const keyPrefix = integration.mode === "live" ? "ky_live_…" : "ky_test_…";

  return (
    <InstallGuide
      apiBase={apiBase}
      keyPrefix={keyPrefix}
      keysHref={integrationTabHref(productId, integration.id, "keys")}
    />
  );
}
