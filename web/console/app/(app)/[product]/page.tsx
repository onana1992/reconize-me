import { notFound } from "next/navigation";
import { parseProduct } from "../../../lib/parse-product";
import { requireMe } from "../../../lib/session";
import { AmlOverview } from "./aml-overview";
import { AuthenticationOverview } from "./authentication-overview";
import { IdentityOverview } from "./identity-overview";

export default async function ProductOverviewPage({
  params,
  searchParams,
}: {
  params: Promise<{ product: string }>;
  searchParams: Promise<{ integration?: string }>;
}) {
  const productId = await parseProduct(params);
  const me = await requireMe();
  const canReadSessions = me.permissions.includes("VERIFICATION_READ");
  const canWriteSessions = me.permissions.includes("VERIFICATION_WRITE");
  const integrationId = (await searchParams).integration?.trim();

  if (productId === "identity") {
    return (
      <IdentityOverview canRead={canReadSessions} canWrite={canWriteSessions} integrationId={integrationId} />
    );
  }

  if (productId === "aml") {
    return (
      <AmlOverview
        canRead={canReadSessions || me.permissions.includes("API_KEY_READ")}
        canWrite={canWriteSessions}
      />
    );
  }

  if (productId === "authentication") {
    return (
      <AuthenticationOverview
        canRead={canReadSessions}
        canWrite={canWriteSessions}
        integrationId={integrationId}
      />
    );
  }

  notFound();
}
