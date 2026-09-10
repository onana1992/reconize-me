/*
 * La gamme, et surtout ce qui est vendable dedans.
 *
 * Un seul endroit décide de la disponibilité d'un produit. Le CDC §6 l'interdit
 * ailleurs : un produit sur la feuille de route n'a ni bouton d'essai ni bouton
 * d'achat. Le kill de la roadmap est explicite là-dessus — une vitrine
 * qui vend l'authentification biométrique ou le criblage AML bloque le go-live.
 *
 * Le jour où l'un des deux ouvre, une seule ligne bouge ici, et la barre de
 * navigation, la page d'accueil et les cartes suivent.
 */

import type { RouteKey } from "./routes";

export type ProductKey = "idv" | "biometric" | "aml";

/** L'ordre d'affichage partout : le produit vendable d'abord. */
export const PRODUCT_ORDER: ProductKey[] = ["idv", "biometric", "aml"];

export const PRODUCT_ROUTE: Record<ProductKey, RouteKey> = {
  idv: "identityVerification",
  biometric: "biometricAuthentication",
  aml: "amlScreening",
};

export const PRODUCT_AVAILABLE: Record<ProductKey, boolean> = {
  idv: true,
  biometric: false,
  aml: false,
};

const BY_ROUTE = new Map<RouteKey, ProductKey>(
  PRODUCT_ORDER.map((product) => [PRODUCT_ROUTE[product], product]),
);

export function productForRoute(route: RouteKey): ProductKey | undefined {
  return BY_ROUTE.get(route);
}
