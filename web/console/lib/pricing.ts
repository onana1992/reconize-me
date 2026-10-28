/*
 * Organization credit is debited when a live result is delivered. Sandbox is free.
 * Identity $0.80, identity with screening $1.30, AML screening $0.50. Packs $50–$500.
 */

export const PRICING = {
  currency: "USD",
  identity: 0.8,
  identityScreening: 1.3,
  aml: 0.5,
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
