/*
 * Points d'entrée externes de la vitrine.
 *
 * Tout ce qui pointe hors du site vit ici : le jour où la console change de
 * domaine, un seul fichier bouge.
 */

function fromEnv(name: string, fallback: string): string {
  const value = process.env[name];
  return value && value.length > 0 ? value.replace(/\/$/, "") : fallback;
}

export const SITE_URL = fromEnv("NEXT_PUBLIC_SITE_URL", "http://localhost:3002");

/**
 * Indexation par les moteurs. **Refusée par défaut** : un aperçu ou un staging
 * indexé est un incident SEO long à rattraper. À passer à `true` uniquement sur
 * le domaine de production.
 */
export const INDEXABLE = process.env.NEXT_PUBLIC_SITE_INDEXABLE === "true";

/** Console client. `/signup` n'existe qu'à partir de M2 (roadmap M1 : placeholder admis). */
export const CONSOLE_URL = fromEnv("NEXT_PUBLIC_CONSOLE_URL", "http://localhost:3000");
export const SIGNUP_URL = `${CONSOLE_URL}/signup`;
export const LOGIN_URL = `${CONSOLE_URL}/login`;

/** API publique : la vitrine n'y appelle rien, elle ne fait que documenter. */
export const API_URL = fromEnv("NEXT_PUBLIC_API_URL", "http://localhost:8080");
export const OPENAPI_URL = `${API_URL}/v3/api-docs`;
export const SWAGGER_URL = `${API_URL}/swagger-ui.html`;

export const CONTACT_EMAIL = fromEnv("NEXT_PUBLIC_CONTACT_EMAIL", "contact@recogniz.me");
export const PRIVACY_EMAIL = fromEnv("NEXT_PUBLIC_PRIVACY_EMAIL", "privacy@recogniz.me");
