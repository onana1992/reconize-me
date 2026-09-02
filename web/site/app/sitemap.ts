import type { MetadataRoute } from "next";
import { DEFAULT_LOCALE, LOCALES } from "../lib/locales";
import { SITEMAP_PRIORITY, href } from "../lib/routes";
import { SITE_URL } from "../lib/site-config";

export const dynamic = "force-static";

/*
 * Le sitemap ne porte que l'accueil : les autres pages ne sont plus servies.
 */
export default function sitemap(): MetadataRoute.Sitemap {
  const absolute = (path: string) => new URL(path, SITE_URL).toString();

  return LOCALES.flatMap((locale) =>
    (["home"] as const).map((key) => ({
      url: absolute(href(locale, key)),
      priority: SITEMAP_PRIORITY[key],
      alternates: {
        languages: {
          ...Object.fromEntries(LOCALES.map((other) => [other, absolute(href(other, key))])),
          "x-default": absolute(href(DEFAULT_LOCALE, key)),
        },
      },
    })),
  );
}
