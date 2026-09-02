/*
 * Parité des dictionnaires.
 *
 * Le typecheck attrape déjà une clé manquante en anglais : `Messages` est
 * inféré de `fr.json`. Il ne voit pas trois choses, et ce script s'en charge :
 *
 *   - une clé en trop dans une langue (TypeScript autorise le surplus) ;
 *   - une liste de longueur différente (`string[]` ne porte pas d'arité) ;
 *   - une chaîne vide, qui passe tous les types du monde.
 *
 *     node scripts/check-messages.mjs
 */

import { readFileSync } from "node:fs";
import { fileURLToPath } from "node:url";
import { dirname, join } from "node:path";

const ROOT = join(dirname(fileURLToPath(import.meta.url)), "..");
const REFERENCE = "fr";
const LOCALES = ["fr", "en"];

function load(locale) {
  return JSON.parse(readFileSync(join(ROOT, "messages", `${locale}.json`), "utf8"));
}

/** Aplatit en chemins pointés, en gardant l'index des listes. */
function flatten(value, prefix = "", output = new Map()) {
  if (Array.isArray(value)) {
    output.set(prefix, { kind: "array", length: value.length });
    value.forEach((item, index) => flatten(item, `${prefix}[${index}]`, output));
  } else if (value !== null && typeof value === "object") {
    output.set(prefix, { kind: "object" });
    for (const [key, child] of Object.entries(value)) {
      flatten(child, prefix ? `${prefix}.${key}` : key, output);
    }
  } else {
    output.set(prefix, { kind: typeof value, value });
  }
  return output;
}

const VERBOSE = process.argv.includes("--verbose");

const dictionaries = new Map(LOCALES.map((locale) => [locale, flatten(load(locale))]));
const reference = dictionaries.get(REFERENCE);
const problems = [];
const identical = [];

for (const locale of LOCALES.filter((candidate) => candidate !== REFERENCE)) {
  const compared = dictionaries.get(locale);

  for (const [path, expected] of reference) {
    const actual = compared.get(path);
    if (!actual) {
      problems.push(`${locale} : clé absente — ${path}`);
      continue;
    }
    if (actual.kind !== expected.kind) {
      problems.push(`${locale} : type ${actual.kind} au lieu de ${expected.kind} — ${path}`);
      continue;
    }
    if (expected.kind === "array" && actual.length !== expected.length) {
      problems.push(
        `${locale} : liste de ${actual.length} élément(s) au lieu de ${expected.length} — ${path}`,
      );
    }
    if (expected.kind === "string" && actual.value === expected.value) {
      identical.push(`${path} = ${JSON.stringify(actual.value)}`);
    }
  }

  for (const path of compared.keys()) {
    if (!reference.has(path)) {
      problems.push(`${locale} : clé en trop, absente de ${REFERENCE} — ${path}`);
    }
  }
}

for (const [locale, flat] of dictionaries) {
  for (const [path, entry] of flat) {
    if (entry.kind === "string" && entry.value.trim() === "") {
      problems.push(`${locale} : chaîne vide — ${path}`);
    }
  }
}

const total = [...reference.values()].filter((entry) => entry.kind === "string").length;
console.log(`${total} chaînes par langue, ${LOCALES.length} langues.`);
console.log(
  `${identical.length} chaîne(s) identiques entre langues — noms propres, codes de statut, chiffres.`,
);
console.log("  (--verbose pour les lister et vérifier qu'aucune traduction n'a été oubliée)");

if (VERBOSE) {
  for (const entry of identical) {
    console.log(`    ${entry}`);
  }
}

if (problems.length > 0) {
  console.error(`\n${problems.length} problème(s) :`);
  for (const problem of problems) {
    console.error(`  ${problem}`);
  }
  process.exit(1);
}

console.log("Dictionnaires alignés.");
