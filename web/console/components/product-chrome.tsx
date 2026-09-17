"use client";

import Link from "next/link";
import { usePathname } from "next/navigation";
import type { ReactNode } from "react";
import { useT } from "../i18n/client";
import { matchProductTab, PRODUCTS, PRODUCT_TABS, integrationWorkspaceId, type ProductId } from "../lib/products";

export function ProductChrome({ product, children }: { product: ProductId; children: ReactNode }) {
  const t = useT();
  const pathname = usePathname();
  const href = PRODUCTS[product].href;
  const insideIntegration = Boolean(integrationWorkspaceId(pathname, product));

  return (
    <>
      <div className="rm-product-bar">
        <nav className="rm-product-tabs" aria-label={t("console.product.tabsLabel")}>
          {PRODUCT_TABS.map((tab) => {
            const target = `${href}${tab.path}`;
            const current = matchProductTab(pathname, product, tab.id);
            return (
              <Link
                key={tab.id}
                href={target}
                className="rm-product-tab"
                aria-current={current ? "page" : undefined}
              >
                {t(tab.labelKey)}
              </Link>
            );
          })}
        </nav>
      </div>
      <div className={insideIntegration ? "rm-page rm-int-page" : "rm-page"}>
        <div className="rm-dash">{children}</div>
      </div>
    </>
  );
}
