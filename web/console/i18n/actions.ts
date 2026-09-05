"use server";

import { cookies } from "next/headers";
import { isLocale, LOCALE_COOKIE, type Locale } from "./locales";

export async function setLocaleAction(locale: Locale) {
  if (!isLocale(locale)) {
    return;
  }
  const jar = await cookies();
  jar.set(LOCALE_COOKIE, locale, {
    path: "/",
    maxAge: 60 * 60 * 24 * 365,
    sameSite: "lax",
  });
}
