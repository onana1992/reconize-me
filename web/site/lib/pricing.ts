/*
 * Organization credit is debited per live resource. Sandbox is always free.
 * Frozen M3: USD, $0.90 per live verification, packs $50 / $100 / $250 / $500.
 */

import type { Locale } from "./locales";

export const PROVISIONAL: boolean = false;

const CURRENCY = "USD";

export const PRICING = {
  identity: {
    /** Prix unitaire d'une vérification live. Le sandbox est à 0. */
    liveUnit: 0.9,
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
