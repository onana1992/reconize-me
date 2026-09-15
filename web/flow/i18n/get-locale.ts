import { cache } from "react";
import { cookies, headers } from "next/headers";
import { isLocale, LOCALE_COOKIE, negotiateLocale, type Locale } from "./locales";

export const getLocale = cache(async (): Promise<Locale> => {
  const jar = await cookies();
  const value = jar.get(LOCALE_COOKIE)?.value;
  if (isLocale(value)) {
    return value;
  }
  return negotiateLocale((await headers()).get("accept-language"));
});
