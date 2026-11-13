/*
 * Table des routes de la vitrine (CDC §8.1).
 *
 * Les segments d'URL sont **identiques dans les deux langues** : seul le
 * préfixe change (`/fr/pricing`, `/en/pricing`). Traduire les segments ferait
 * diverger les routes de celles documentées au CDC pour un gain SEO marginal.
 *
 * Cette table est la source unique de la navigation, du pied de page et du
 * sitemap : une page oubliée dans le sitemap devient impossible.
 */

import type { Locale } from "./locales";

export const ROUTES = {
  home: "/",
  identityVerification: "/products/identity-verification",
  biometricAuthentication: "/products/biometric-authentication",
  amlScreening: "/products/aml-screening",
  pricing: "/pricing",
  services: "/services",
  company: "/company",
  security: "/security",
  docs: "/docs",
  supportedDocuments: "/resources/supported-documents",
  blog: "/blog",
  contact: "/contact",
  terms: "/legal/terms",
  privacy: "/legal/privacy",
  dpa: "/legal/dpa",
} as const;

export type RouteKey = keyof typeof ROUTES;

export const ROUTE_KEYS = Object.keys(ROUTES) as RouteKey[];

export function href(locale: Locale, key: RouteKey): string {
  const path = ROUTES[key];
  return path === "/" ? `/${locale}` : `/${locale}${path}`;
}

/*
 * Barre de navigation.
 *
 * Les trois produits vivent dans un mega-menu plutôt qu'en onglets : c'est
 * le seul endroit du site où l'on voit d'un coup d'œil lequel est vendable et
 * lesquels sont annoncés. Aplatir la gamme dans la barre ferait passer les deux
 * teasers pour des produits livrés.
 */
export const PRODUCT_NAV: RouteKey[] = [
  "identityVerification",
  "biometricAuthentication",
  "amlScreening",
];

export const COMPANY_NAV = ["company", "contact"] as const satisfies readonly RouteKey[];

export const RESOURCE_NAV = ["docs", "supportedDocuments", "blog", "pricing"] as const satisfies readonly RouteKey[];

export const LEGAL_NAV = ["terms", "privacy", "dpa"] as const satisfies readonly RouteKey[];

export const FOOTER_NAV: { heading: "products" | "resources" | "legal"; items: RouteKey[] }[] = [
  {
    heading: "products",
    items: ["identityVerification", "biometricAuthentication", "amlScreening"],
  },
  { heading: "resources", items: ["docs", "supportedDocuments", "blog", "pricing"] },
  { heading: "legal", items: ["terms", "privacy", "dpa"] },
];

/** Priorités de sitemap : l'accueil et le produit vendable passent devant le légal. */
export const SITEMAP_PRIORITY: Record<RouteKey, number> = {
  home: 1,
  identityVerification: 0.9,
  pricing: 0.9,
  services: 0.6,
  company: 0.6,
  docs: 0.8,
  supportedDocuments: 0.5,
  blog: 0.5,
  security: 0.7,
  biometricAuthentication: 0.5,
  amlScreening: 0.5,
  contact: 0.5,
  terms: 0.3,
  privacy: 0.3,
  dpa: 0.3,
};
