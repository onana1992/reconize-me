/*
 * Chiffres de l'offre.
 *
 * Les montants et les quotas ne sont pas du texte : ils ne vivent pas dans les
 * dictionnaires, ils sont formatés par langue. Le gel des prix en M3 (roadmap
 * §6) ne touche que ce fichier.
 *
 * PROVISOIRE — devise et montants restent à figer avant le premier Checkout.
 * La page tarifs affiche l'avertissement correspondant tant que `PROVISIONAL`
 * est vrai.
 */

import type { Locale } from "./locales";

/** Passera à `false` au gel des prix (M3), avec les montants réels. */
export const PROVISIONAL: boolean = true;

const CURRENCY = "EUR";

export const PRICING = {
  sandbox: {
    monthly: 0,
    included: 50,
  },
  production: {
    monthly: 49,
    included: 200,
    /** Au-delà du forfait, à l'unité. */
    overage: 0.9,
  },
} as const;

const LOCALE_TAGS: Record<Locale, string> = { fr: "fr-FR", en: "en-GB" };

export function formatMoney(locale: Locale, amount: number): string {
  return new Intl.NumberFormat(LOCALE_TAGS[locale], {
    style: "currency",
    currency: CURRENCY,
    minimumFractionDigits: Number.isInteger(amount) ? 0 : 2,
  }).format(amount);
}

export function formatCount(locale: Locale, value: number): string {
  return new Intl.NumberFormat(LOCALE_TAGS[locale]).format(value);
}
