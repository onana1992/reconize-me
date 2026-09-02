import { DEFAULT_LOCALE, isLocale, type Locale } from "./locales";

export type LocaleParams = { params: Promise<{ locale: string }> };

/**
 * Toute page passe par ici : `params` arrive en `string` brut, le reste du code
 * ne manipule que `Locale`. Le repli sur le français ne devrait jamais servir —
 * `dynamicParams = false` refuse déjà les langues inconnues — mais il évite
 * qu'une faute de frappe dans une route se transforme en plantage de rendu.
 */
export async function resolveLocale(params: LocaleParams["params"]): Promise<Locale> {
  const { locale } = await params;
  return isLocale(locale) ? locale : DEFAULT_LOCALE;
}
