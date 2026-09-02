/*
 * Métadonnées et SEO (CDC §8.4).
 *
 * Chaque page déclare son titre et sa description ; le reste — canonique,
 * `hreflang`, Open Graph — est dérivé de la route et de la langue pour qu'aucune
 * page ne puisse l'oublier.
 */

import type { Metadata } from "next";
import ogImage from "@kyc/brand/assets/logo/og-1200x630.png";
import { DEFAULT_LOCALE, LOCALES, type Locale } from "./locales";
import { getMessages } from "./messages";
import { href, type RouteKey } from "./routes";
import { SITE_URL } from "./site-config";

const OG_LOCALES: Record<Locale, string> = { fr: "fr_FR", en: "en_GB" };

export function pageMetadata(locale: Locale, route: RouteKey, title: string, description: string): Metadata {
  const messages = getMessages(locale);
  const path = href(locale, route);

  return {
    title,
    description,
    alternates: {
      canonical: path,
      languages: {
        ...Object.fromEntries(LOCALES.map((other) => [other, href(other, route)])),
        "x-default": href(DEFAULT_LOCALE, route),
      },
    },
    openGraph: {
      type: "website",
      siteName: messages.meta.siteName,
      locale: OG_LOCALES[locale],
      alternateLocale: LOCALES.filter((other) => other !== locale).map((other) => OG_LOCALES[other]),
      url: path,
      title,
      description,
      images: [{ url: ogImage.src, width: ogImage.width, height: ogImage.height, alt: messages.meta.siteName }],
    },
    twitter: {
      card: "summary_large_image",
      title,
      description,
      images: [ogImage.src],
    },
  };
}

/** Racine des métadonnées : sans elle, les URL relatives ne sont pas absolues en OG. */
export const metadataBase = new URL(SITE_URL);
