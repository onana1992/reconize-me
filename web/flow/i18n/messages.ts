import en from "./en.json";
import fr from "./fr.json";
import type { Locale } from "./locales";

export type Messages = typeof fr;
export type MessageKey = DotPath<Messages>;
export type Translate = (key: MessageKey) => string;

type DotPath<T, Prefix extends string = ""> = {
  [K in keyof T & string]: T[K] extends string
    ? `${Prefix}${K}`
    : DotPath<T[K], `${Prefix}${K}.`>;
}[keyof T & string];

const DICTIONARIES: Record<Locale, Messages> = { fr, en };

export function getMessages(locale: Locale): Messages {
  return DICTIONARIES[locale];
}

export function createT(messages: Messages): Translate {
  return function t(key) {
    const value = key.split(".").reduce<unknown>((node, part) => {
      if (node && typeof node === "object" && part in node) {
        return (node as Record<string, unknown>)[part];
      }
      return undefined;
    }, messages);
    return typeof value === "string" ? value : key;
  };
}
