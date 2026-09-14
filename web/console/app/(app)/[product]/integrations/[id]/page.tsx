import { notFound } from "next/navigation";
import { getT } from "../../../../../i18n";
import { consoleApi, type VerificationList } from "../../../../../lib/api";
import { parseProduct } from "../../../../../lib/parse-product";
import { requireMe, sessionCookieHeader } from "../../../../../lib/session";
import { IdentityTable } from "../../identity-table";
import { loadIntegration } from "../load";

export const dynamic = "force-dynamic";

export default async function IntegrationSessionsPage({
  params,
}: {
  params: Promise<{ product: string; id: string }>;
}) {
  const resolved = await params;
  await parseProduct(Promise.resolve({ product: resolved.product }));
  const [me, t, integration] = await Promise.all([
    requireMe(),
    getT(),
    loadIntegration(resolved.id),
  ]);
  if (!integration.ok) {
    notFound();
  }
  const canRead = (me.permissions ?? []).includes("VERIFICATION_READ");
  if (!canRead) {
    return <p className="rm-lead">{t("console.product.sessionsLocked")}</p>;
  }
  const sessions = await consoleApi<VerificationList>(
    `/v1/console/verifications?limit=100&integration_id=${encodeURIComponent(integration.data.id)}`,
    await sessionCookieHeader(),
  );
  if (!sessions.ok) {
    return (
      <p role="alert" className="rm-alert">
        {sessions.message ?? t("console.verifications.loadError")}
      </p>
    );
  }
  return (
    <IdentityTable
      initialItems={sessions.data.items}
      initialCursor={sessions.data.next_cursor ?? null}
      integrationId={integration.data.id}
    />
  );
}
