export const LOCALE_COOKIE = "rm_locale";
export const LOCALES = ["fr", "en"] as const;
export type Locale = (typeof LOCALES)[number];
export const DEFAULT_LOCALE: Locale = "fr";

export function isLocale(value: string | undefined): value is Locale {
  return value !== undefined && (LOCALES as readonly string[]).includes(value);
}

export function negotiateLocale(header: string | null): Locale {
  if (!header) {
    return DEFAULT_LOCALE;
  }

  const ranked = header
    .split(",")
    .map((part) => {
      const [tag, ...parameters] = part.trim().split(";");
      const quality = parameters
        .map((parameter) => parameter.trim())
        .find((parameter) => parameter.startsWith("q="));
      return {
        primary: tag.trim().toLowerCase().split("-")[0],
        quality: quality ? Number.parseFloat(quality.slice(2)) : 1,
      };
    })
    .filter((entry) => Number.isFinite(entry.quality) && entry.quality > 0)
    .sort((a, b) => b.quality - a.quality);

  return ranked.find((entry) => isLocale(entry.primary))?.primary as Locale | undefined ?? DEFAULT_LOCALE;
}
