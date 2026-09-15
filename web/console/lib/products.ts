import type { IconName } from "../components/nav-icons";

export const PRODUCT_IDS = ["identity", "biometrics", "aml"] as const;

export type ProductId = (typeof PRODUCT_IDS)[number];

export type ProductTabId = "overview" | "integrations";

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
  { id: "integrations", path: "/integrations", labelKey: "console.product.tab.integrations" },
];

export function isProductId(value: string): value is ProductId {
  return (PRODUCT_IDS as readonly string[]).includes(value);
}

export function productTabHref(product: ProductId, tab: ProductTabId): string {
  const path = PRODUCT_TABS.find((item) => item.id === tab)?.path ?? "";
  return `${PRODUCTS[product].href}${path}`;
}

export function matchProductTab(pathname: string, product: ProductId, tab: ProductTabId): boolean {
  const href = productTabHref(product, tab);
  if (tab === "overview") {
    return pathname === href;
  }
  return pathname === href || pathname.startsWith(`${href}/`);
}

const UUID = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i;

export function isIntegrationId(value: string): boolean {
  return UUID.test(value);
}

export type IntegrationTabId = "sessions" | "install" | "keys" | "settings";

export const INTEGRATION_TABS: {
  id: IntegrationTabId;
  path: string;
  labelKey: `console.integrations.tab.${IntegrationTabId}`;
}[] = [
  { id: "sessions", path: "", labelKey: "console.integrations.tab.sessions" },
  { id: "install", path: "/install", labelKey: "console.integrations.tab.install" },
  { id: "keys", path: "/keys", labelKey: "console.integrations.tab.keys" },
  { id: "settings", path: "/settings", labelKey: "console.integrations.tab.settings" },
];

export function integrationWorkspaceId(pathname: string, product: ProductId): string | null {
  const prefix = `${PRODUCTS[product].href}/integrations/`;
  if (!pathname.startsWith(prefix)) {
    return null;
  }
  const id = pathname.slice(prefix.length).split("/")[0] ?? "";
  return UUID.test(id) ? id : null;
}

export function integrationTabHref(product: ProductId, id: string, tab: IntegrationTabId): string {
  const path = INTEGRATION_TABS.find((item) => item.id === tab)?.path ?? "";
  return `${PRODUCTS[product].href}/integrations/${id}${path}`;
}

export function matchIntegrationTab(
  pathname: string,
  product: ProductId,
  id: string,
  tab: IntegrationTabId,
): boolean {
  const href = integrationTabHref(product, id, tab);
  if (tab === "sessions") {
    return pathname === href;
  }
  return pathname === href || pathname.startsWith(`${href}/`);
}

