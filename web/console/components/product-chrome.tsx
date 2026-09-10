"use client";

import Link from "next/link";
import { usePathname, useSearchParams } from "next/navigation";
import { useEffect, type ReactNode } from "react";
import { useT } from "../i18n/client";
import {
  ENVIRONMENTS,
  resolveEnvironment,
  tEnvironment,
  withEnvironment,
  writeEnvironmentCookie,
} from "../lib/environment";
import { matchProductTab, PRODUCTS, PRODUCT_TABS, type ProductId } from "../lib/products";

export function ProductChrome({ product, children }: { product: ProductId; children: ReactNode }) {
  const t = useT();
  const pathname = usePathname();
  const searchParams = useSearchParams();
  const env = resolveEnvironment(searchParams.get("env"));
  const href = PRODUCTS[product].href;

  useEffect(() => {
    writeEnvironmentCookie(env);
  }, [env]);

  return (
    <>
      <div className="rm-product-bar" data-env={env}>
        <nav className="rm-product-tabs" aria-label={t("console.product.tabsLabel")}>
          {PRODUCT_TABS.map((tab) => {
            const target = withEnvironment(`${href}${tab.path}`, env);
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
        <div className="rm-env-switch" role="radiogroup" aria-label={t("console.env.switchLabel")}>
          {ENVIRONMENTS.map((option) => {
            const selected = option === env;
            return (
              <Link
                key={option}
                href={withEnvironment(pathname, option)}
                className="rm-env-option"
                data-env={option}
                role="radio"
                aria-checked={selected}
                aria-current={selected ? "true" : undefined}
              >
                {tEnvironment(t, option)}
              </Link>
            );
          })}
        </div>
      </div>
      <div className="rm-page" data-env={env}>
        <p className="rm-env-banner" data-env={env} role="status">
          <strong>{tEnvironment(t, env)}</strong>
          {env === "live" ? t("console.env.liveLead") : t("console.env.sandboxLead")}
        </p>
        <div className="rm-dash">{children}</div>
      </div>
    </>
  );
}
