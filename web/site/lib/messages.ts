/*
 * Chargement des dictionnaires.
 *
 * Le français est la **source de vérité du type** : `Messages` est inféré de
 * `fr.json`, et `en.json` doit s'y conformer. Une clé oubliée en anglais casse
 * le typecheck, elle n'attend pas d'être vue en production.
 *
 * Ce que le type ne voit pas — clé en trop, chaîne vide, liste de longueur
 * différente — est couvert par `scripts/check-messages.mjs`.
 */

import en from "../messages/en.json";
import fr from "../messages/fr.json";
import type { Locale } from "./locales";

export type Messages = typeof fr;

const DICTIONARIES: Record<Locale, Messages> = { fr, en };

export function getMessages(locale: Locale): Messages {
  return DICTIONARIES[locale];
}
