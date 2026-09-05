function fromEnv(name: string, fallback: string): string {
  const value = process.env[name];
  return value && value.length > 0 ? value.replace(/\/$/, "") : fallback;
}

export const SITE_URL = fromEnv("NEXT_PUBLIC_SITE_URL", "http://localhost:3002");

export function sitePath(locale: string, path: string): string {
  return `${SITE_URL}/${locale}${path}`;
}

export function sitePrivacyUrl(locale: string): string {
  return sitePath(locale, "/legal/privacy");
}

export function siteTermsUrl(locale: string): string {
  return sitePath(locale, "/legal/terms");
}

export function siteDpaUrl(locale: string): string {
  return sitePath(locale, "/legal/dpa");
}

export function siteContactUrl(locale: string): string {
  return sitePath(locale, "/contact");
}
