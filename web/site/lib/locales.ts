/*
 * Langues de la vitrine.
 *
 * Ce module ne dépend d'aucun dictionnaire : il est importé par le middleware,
 * qui tourne sur l'edge runtime. Y ajouter les traductions embarquerait tout le
 * contenu du site dans le bundle du middleware.
 */

export const LOCALES = ["fr", "en"] as const;

export type Locale = (typeof LOCALES)[number];

/** Le français est la langue de référence : le contenu s'y écrit d'abord. */
export const DEFAULT_LOCALE: Locale = "fr";

export const LOCALE_LABELS: Record<Locale, string> = {
  fr: "Français",
  en: "English",
};

export function isLocale(value: string | undefined): value is Locale {
  return value !== undefined && (LOCALES as readonly string[]).includes(value);
}

/**
 * Langue à servir pour un en-tête `Accept-Language`.
 *
 * Les facteurs de qualité sont respectés : `en;q=0.9, fr;q=0.4` rend `en`,
 * même si le français est notre défaut.
 */
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
