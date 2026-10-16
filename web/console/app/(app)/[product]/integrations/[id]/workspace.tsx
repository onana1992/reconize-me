import Link from "next/link";
import { StatusBadge } from "@/brand";
import { BackLink } from "../../../../../components/back-link";
import { getLocale, getT } from "../../../../../i18n";
import type { IntegrationResponse } from "../../../../../lib/api";
import { integrationModeTone, tIntegrationMode } from "../../../../../lib/environment";
import { PRODUCTS, type ProductId } from "../../../../../lib/products";
import { formatUtc } from "../../../../../lib/status";
import { IntegrationTabs } from "./integration-tabs";

export async function IntegrationWorkspace({
  productId,
  integration,
  children,
}: {
  productId: ProductId;
  integration: IntegrationResponse;
  children: React.ReactNode;
}) {
  const [t, locale] = await Promise.all([getT(), getLocale()]);

  return (
    <div className="rm-int">
      <div className="rm-int-top">
        <BackLink href={`${PRODUCTS[productId].href}/integrations`}>{t("console.integrations.back")}</BackLink>
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
          <div className="rm-int-head-actions">
            <Link
              href={`${PRODUCTS[productId].href}?integration=${encodeURIComponent(integration.id)}`}
              className="rm-button"
              data-variant="secondary"
            >
              {t("console.integrations.viewVerifications")}
            </Link>
          </div>
        </header>
      </div>
      <IntegrationTabs product={productId} id={integration.id} />
      <div className="rm-int-body">{children}</div>
    </div>
  );
}
