/*
 * Organization credit is debited per live resource. Sandbox is always free.
 * Amounts stay provisional until Stripe card top-up (M3).
 */

export const PRICING = {
  currency: "USD",
  identity: {
    liveUnit: 0.9,
  },
} as const;

/** Card top-up packs (MVP). Other channels come later. */
export const CREDIT_PACKS = [50, 100, 250, 500] as const;

const LOCALE_TAGS = { fr: "fr-FR", en: "en-GB" } as const;

export function formatMoney(locale: "fr" | "en", amount: number): string {
  return new Intl.NumberFormat(LOCALE_TAGS[locale], {
    style: "currency",
    currency: PRICING.currency,
    currencyDisplay: "narrowSymbol",
    minimumFractionDigits: Number.isInteger(amount) ? 0 : 2,
  }).format(amount);
}

export function formatCount(locale: "fr" | "en", value: number): string {
  return new Intl.NumberFormat(LOCALE_TAGS[locale]).format(value);
}
