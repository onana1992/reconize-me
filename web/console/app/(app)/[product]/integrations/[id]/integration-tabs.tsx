"use client";

import Link from "next/link";
import { usePathname } from "next/navigation";
import { useT } from "../../../../../i18n/client";
import {
  INTEGRATION_TABS,
  integrationTabHref,
  matchIntegrationTab,
  type ProductId,
} from "../../../../../lib/products";

export function IntegrationTabs({ product, id }: { product: ProductId; id: string }) {
  const t = useT();
  const pathname = usePathname();

  return (
    <nav className="rm-int-tabs" aria-label={t("console.integrations.tabsLabel")}>
      {INTEGRATION_TABS.map((tab) => {
        const href = integrationTabHref(product, id, tab.id);
        const current = matchIntegrationTab(pathname, product, id, tab.id);
        return (
          <Link
            key={tab.id}
            href={href}
            className="rm-int-tab"
            aria-current={current ? "page" : undefined}
          >
            {t(tab.labelKey)}
          </Link>
        );
      })}
    </nav>
  );
}
