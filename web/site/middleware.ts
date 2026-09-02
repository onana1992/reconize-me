/*
 * Négociation de langue.
 *
 * Toutes les pages vivent sous un préfixe de langue (`/fr/...`, `/en/...`) :
 * une URL est donc toujours explicite, et le SSG produit les deux versions.
 * Le middleware ne sert qu'à envoyer une URL sans préfixe vers la bonne langue.
 */

import { NextResponse, type NextRequest } from "next/server";
import { isLocale, negotiateLocale } from "./lib/locales";

/*
 * Fichiers servis à la racine : ils n'ont pas de langue.
 *
 * `/media/` en fait partie, et l'oubli coûte cher : sans lui, une image du
 * dossier `public` est redirigée vers `/fr/media/...`, qui n'existe pas. Le
 * navigateur ne montre rien et l'optimiseur d'images répond 400 — on croit à
 * un problème de mise en page alors que le fichier n'a jamais été servi.
 */
const UNPREFIXED =
  /^\/(?:media\/|opengraph-image|icon|apple-icon|robots\.txt|sitemap\.xml|favicon\.ico)/;

export function middleware(request: NextRequest) {
  const { pathname } = request.nextUrl;

  if (UNPREFIXED.test(pathname)) {
    return NextResponse.next();
  }

  if (isLocale(pathname.split("/")[1])) {
    return NextResponse.next();
  }

  const locale = negotiateLocale(request.headers.get("accept-language"));
  const target = request.nextUrl.clone();
  target.pathname = pathname === "/" ? `/${locale}` : `/${locale}${pathname}`;
  return NextResponse.redirect(target);
}

export const config = {
  matcher: ["/((?!_next/).*)"],
};
