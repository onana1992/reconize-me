/*
 * Contrôle SEO du site prégénéré (CDC §8.4).
 *
 * Part du sitemap produit au build — lui-même dérivé de `lib/routes.ts` — et
 * vérifie pour chaque URL que la page existe bel et bien en statique et qu'elle
 * porte ses balises. Une page ajoutée sans métadonnées, ou une langue oubliée
 * dans les `hreflang`, sort en erreur.
 *
 *     npm run build && node scripts/check-seo.mjs
 */

import { existsSync, readFileSync } from "node:fs";
import { fileURLToPath } from "node:url";
import { dirname, join } from "node:path";

const ROOT = join(dirname(fileURLToPath(import.meta.url)), "..");
const OUTPUT = join(ROOT, ".next", "server", "app");
const LOCALES = ["fr", "en"];

const sitemapPath = join(OUTPUT, "sitemap.xml.body");
if (!existsSync(sitemapPath)) {
  console.error("Sitemap absent : lancer `npm run build` avant ce contrôle.");
  process.exit(1);
}

const sitemap = readFileSync(sitemapPath, "utf8");
const urls = [...sitemap.matchAll(/<loc>([^<]+)<\/loc>/g)].map((match) => match[1]);

if (urls.length === 0) {
  console.error("Sitemap vide.");
  process.exit(1);
}

const problems = [];

/** `/fr` → `fr.html`, `/fr/legal/dpa` → `fr/legal/dpa.html`. */
function htmlPathFor(pathname) {
  return join(OUTPUT, `${pathname.replace(/^\//, "")}.html`);
}

function attribute(tag, name) {
  const match = tag.match(new RegExp(`${name}="([^"]*)"`));
  return match ? match[1] : null;
}

for (const url of urls) {
  const { pathname } = new URL(url);
  const file = htmlPathFor(pathname);

  if (!existsSync(file)) {
    problems.push(`${pathname} : pas de page statique (${file})`);
    continue;
  }

  const html = readFileSync(file, "utf8");
  const head = html.slice(0, html.indexOf("</head>"));
  const locale = pathname.split("/")[1];

  if (!html.includes(`<html lang="${locale}"`)) {
    problems.push(`${pathname} : attribut lang absent ou différent de "${locale}"`);
  }

  const title = head.match(/<title>([^<]*)<\/title>/);
  if (!title || title[1].trim() === "") {
    problems.push(`${pathname} : titre vide`);
  }

  const description = [...head.matchAll(/<meta name="description" content="([^"]*)"/g)];
  if (description.length !== 1 || description[0][1].trim() === "") {
    problems.push(`${pathname} : description absente, vide ou en double`);
  }

  const links = [...head.matchAll(/<link[^>]*>/g)].map((match) => match[0]);

  const canonical = links.find((tag) => attribute(tag, "rel") === "canonical");
  if (!canonical) {
    problems.push(`${pathname} : canonique absente`);
  } else if (!attribute(canonical, "href").endsWith(pathname)) {
    problems.push(`${pathname} : canonique pointe ailleurs — ${attribute(canonical, "href")}`);
  }

  const alternates = links
    .filter((tag) => attribute(tag, "rel") === "alternate")
    .map((tag) => attribute(tag, "hrefLang") ?? attribute(tag, "hreflang"));

  for (const expected of [...LOCALES, "x-default"]) {
    if (!alternates.includes(expected)) {
      problems.push(`${pathname} : hreflang "${expected}" absent`);
    }
  }

  if (!head.includes('property="og:locale"')) {
    problems.push(`${pathname} : og:locale absente`);
  }

  // L'image OG vient de packages/brand. Vérifier la balise ne suffit pas :
  // une aperçu cassée ne se voit que le jour du partage.
  const ogImage = head.match(/<meta property="og:image" content="([^"]*)"/);
  if (!ogImage) {
    problems.push(`${pathname} : og:image absente`);
  } else {
    const asset = join(ROOT, ".next", new URL(ogImage[1], "http://local").pathname.replace("/_next/", ""));
    if (!existsSync(asset)) {
      problems.push(`${pathname} : og:image introuvable sur le disque — ${ogImage[1]}`);
    }
  }
}

console.log(`${urls.length} URL au sitemap, ${LOCALES.length} langues.`);

if (problems.length > 0) {
  console.error(`\n${problems.length} problème(s) :`);
  for (const problem of problems) {
    console.error(`  ${problem}`);
  }
  process.exit(1);
}

console.log("Chaque page est statique, titrée, canonique et alternée.");
