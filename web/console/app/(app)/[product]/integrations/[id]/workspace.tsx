import Link from "next/link";
import { StatusBadge } from "@kyc/brand";
import { getLocale, getT } from "../../../../../i18n";
import type { IntegrationResponse } from "../../../../../lib/api";
import { integrationModeTone, tIntegrationMode } from "../../../../../lib/environment";
import { PRODUCTS, type ProductId } from "../../../../../lib/products";
import { formatUtc } from "../../../../../lib/status";
import { NewVerificationDrawer } from "../../new-verification-drawer";
import { IntegrationTabs } from "./integration-tabs";

export async function IntegrationWorkspace({
  productId,
  integration,
  canWriteSessions,
  children,
}: {
  productId: ProductId;
  integration: IntegrationResponse;
  canWriteSessions: boolean;
  children: React.ReactNode;
}) {
  const [t, locale] = await Promise.all([getT(), getLocale()]);
  const live = integration.mode === "live";

  return (
    <div className="rm-int">
      <div className="rm-int-top">
        <Link href={`${PRODUCTS[productId].href}/integrations`} className="rm-int-back">
          <span className="rm-int-back-chevron" aria-hidden="true" />
          {t("console.integrations.back")}
        </Link>
        <header className="rm-int-head">
          <div className="rm-int-head-copy">
            <div className="rm-int-title-row">
              <h1>{integration.name}</h1>
              <StatusBadge
                label={tIntegrationMode(t, integration.mode)}
                tone={integrationModeTone(integration.mode)}
              />
            </div>
            <p className="rm-int-meta">{formatUtc(integration.created_at, locale)}</p>
          </div>
          {canWriteSessions ? (
            <div className="rm-int-head-actions">
              <NewVerificationDrawer product={productId} integrationId={integration.id} live={live} />
            </div>
          ) : null}
        </header>
      </div>
      <IntegrationTabs product={productId} id={integration.id} />
      <div className="rm-int-body">{children}</div>
    </div>
  );
}
