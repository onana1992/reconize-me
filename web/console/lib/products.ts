import type { IconName } from "../components/nav-icons";
import { withEnvironment, type ApiEnvironment } from "./environment";

export const PRODUCT_IDS = ["identity", "biometrics", "aml"] as const;

export type ProductId = (typeof PRODUCT_IDS)[number];

export type ProductTabId = "overview" | "configuration" | "integrations";

export type ProductDef = {
  id: ProductId;
  href: string;
  icon: IconName;
  navKey: "console.nav.idv" | "console.nav.biometric" | "console.nav.aml";
  topKey: "console.top.identity" | "console.top.biometrics" | "console.top.aml";
  titleKey: "console.home.idvTitle" | "console.nav.biometric" | "console.nav.aml";
  leadKey: "console.home.idvCardLead" | "console.home.biometricLead" | "console.home.amlLead";
  /** Live usage of this service debits the organization credit. Teasers do not. */
  metered: boolean;
};

export const PRODUCTS: Record<ProductId, ProductDef> = {
  identity: {
    id: "identity",
    href: "/identity",
    icon: "idcard",
    navKey: "console.nav.idv",
    topKey: "console.top.identity",
    titleKey: "console.home.idvTitle",
    leadKey: "console.home.idvCardLead",
    metered: true,
  },
  biometrics: {
    id: "biometrics",
    href: "/biometrics",
    icon: "scan",
    navKey: "console.nav.biometric",
    topKey: "console.top.biometrics",
    titleKey: "console.nav.biometric",
    leadKey: "console.home.biometricLead",
    metered: false,
  },
  aml: {
    id: "aml",
    href: "/aml",
    icon: "search",
    navKey: "console.nav.aml",
    topKey: "console.top.aml",
    titleKey: "console.nav.aml",
    leadKey: "console.home.amlLead",
    metered: false,
  },
};

export const PRODUCT_TABS: { id: ProductTabId; path: string; labelKey: `console.product.tab.${ProductTabId}` }[] = [
  { id: "overview", path: "", labelKey: "console.product.tab.overview" },
  { id: "configuration", path: "/configuration", labelKey: "console.product.tab.configuration" },
  { id: "integrations", path: "/integrations", labelKey: "console.product.tab.integrations" },
];

export function isProductId(value: string): value is ProductId {
  return (PRODUCT_IDS as readonly string[]).includes(value);
}

export function productTabHref(product: ProductId, tab: ProductTabId, env?: ApiEnvironment): string {
  const path = PRODUCT_TABS.find((item) => item.id === tab)?.path ?? "";
  const href = `${PRODUCTS[product].href}${path}`;
  return env ? withEnvironment(href, env) : href;
}

export function matchProductTab(pathname: string, product: ProductId, tab: ProductTabId): boolean {
  const href = productTabHref(product, tab);
  if (tab === "overview") {
    return pathname === href;
  }
  return pathname === href || pathname.startsWith(`${href}/`);
}

