/*
 * Organization credit is debited per live resource. Sandbox is always free.
 * Frozen M3: USD, $0.90 per live verification, packs $50–$500.
 */

export const PRICING = {
  currency: "USD",
  identity: {
    liveUnit: 0.9,
  },
} as const;

/** Card top-up packs in major units (MVP). Other channels come later. */
export const CREDIT_PACKS = [50, 100, 250, 500] as const;

export const CREDIT_PACKS_MINOR = [5000, 10000, 25000, 50000] as const;

const LOCALE_TAGS = { fr: "fr-FR", en: "en-GB" } as const;

export function formatMoney(locale: "fr" | "en", amount: number): string {
  return new Intl.NumberFormat(LOCALE_TAGS[locale], {
    style: "currency",
    currency: PRICING.currency,
    currencyDisplay: "narrowSymbol",
    minimumFractionDigits: Number.isInteger(amount) ? 0 : 2,
  }).format(amount);
}

export function formatMinor(locale: "fr" | "en", minor: number): string {
  return formatMoney(locale, minor / 100);
}

export function formatCount(locale: "fr" | "en", value: number): string {
  return new Intl.NumberFormat(LOCALE_TAGS[locale]).format(value);
}
