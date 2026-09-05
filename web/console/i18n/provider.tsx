"use client";

import { createContext, useContext, type ReactNode } from "react";
import { createT, getMessages, type Translate } from "./messages";
import type { Locale } from "./locales";

type I18nContextValue = {
  locale: Locale;
  t: Translate;
};

const I18nContext = createContext<I18nContextValue | null>(null);

export function I18nProvider({ locale, children }: { locale: Locale; children: ReactNode }) {
  const t = createT(getMessages(locale));
  return <I18nContext.Provider value={{ locale, t }}>{children}</I18nContext.Provider>;
}

function useI18n(): I18nContextValue {
  const value = useContext(I18nContext);
  if (!value) {
    throw new Error("useT must be used within I18nProvider");
  }
  return value;
}

export function useT(): Translate {
  return useI18n().t;
}

export function useLocale(): Locale {
  return useI18n().locale;
}
