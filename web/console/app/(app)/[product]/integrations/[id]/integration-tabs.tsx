"use client";

import Link from "next/link";
import { usePathname } from "next/navigation";
import { useT } from "../../../../../i18n/client";
import {
  integrationTabHref,
  matchIntegrationTab,
  type IntegrationTabId,
  type ProductId,
} from "../../../../../lib/products";

export function IntegrationTabs({
  product,
  id,
  tabs,
}: {
  product: ProductId;
  id: string;
  tabs: IntegrationTabId[];
}) {
  const t = useT();
  const pathname = usePathname();

  return (
    <nav className="rm-int-tabs" aria-label={t("console.integrations.tabsLabel")}>
      {tabs.map((tab) => {
        const href = integrationTabHref(product, id, tab);
        const current = matchIntegrationTab(pathname ?? "", product, id, tab);
        return (
          <Link
            key={tab}
            href={href}
            className="rm-int-tab"
            aria-current={current ? "page" : undefined}
          >
            {t(`console.integrations.tab.${tab}`)}
          </Link>
        );
      })}
    </nav>
  );
}
