"use client";

import Link from "next/link";
import { usePathname } from "next/navigation";
import type { ReactNode } from "react";
import { useT } from "../i18n/client";
import { matchProductTab, PRODUCTS, productTabs, integrationWorkspaceId, type ProductId } from "../lib/products";

export function ProductChrome({ product, children }: { product: ProductId; children: ReactNode }) {
  const t = useT();
  const pathname = usePathname();
  const href = PRODUCTS[product].href;
  const insideIntegration = Boolean(integrationWorkspaceId(pathname, product));

  return (
    <>
      <div className="rm-product-bar">
        <nav className="rm-product-tabs" aria-label={t("console.product.tabsLabel")}>
          {productTabs(product).map((tab) => {
            const target = `${href}${tab.path}`;
            const current = matchProductTab(pathname, product, tab.id);
            const labelKey =
              product === "authentication" && tab.id === "verifications"
                ? "console.product.tab.authentications"
                : tab.labelKey;
            return (
              <Link
                key={tab.id}
                href={target}
                prefetch={false}
                className="rm-product-tab"
                aria-current={current ? "page" : undefined}
              >
                {t(labelKey)}
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
