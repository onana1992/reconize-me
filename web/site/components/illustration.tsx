import type { ReactElement } from "react";

/*
 * Illustration technique.
 *
 * En SVG en ligne et en `currentColor`, pour trois raisons : c'est net à
 * n'importe quelle densité d'écran, ça se thématise sur la bande d'encre sans
 * second fichier, et ça ne coûte aucune requête (RG-BRAND-13).
 *
 * Le trait est volontairement filaire et ouvert. Une icône pleine se lirait
 * comme un pictogramme d'interface, alors que ces glyphes accompagnent un
 * argument : ils décorent une section, ils ne désignent pas une commande.
 *
 * `aria-hidden` sur tous : le sens est porté par le titre à côté. Un glyphe
 * annoncé au lecteur d'écran ne ferait que répéter ce titre.
 */

export type GlyphName =
  | "api"
  | "link"
  | "console"
  | "webhook"
  | "shield"
  | "ledger"
  | "frame"
  | "match";

const PATHS: Record<GlyphName, ReactElement> = {
  /* Chevrons de code : l'appel direct. */
  api: (
    <>
      <path d="M17 16 9 24l8 8" />
      <path d="M31 16l8 8-8 8" />
      <path d="M26 11l-4 26" />
    </>
  ),

  /* Maillons : le lien hébergé qu'on transmet à son client. */
  link: (
    <>
      <path d="M21 27a6 6 0 0 1 0-8l4-4a6 6 0 0 1 8 8l-1 1" />
      <path d="M27 21a6 6 0 0 1 0 8l-4 4a6 6 0 0 1-8-8l1-1" />
    </>
  ),

  /* Fenêtre à lignes : la console d'équipe. */
  console: (
    <>
      <rect x="8" y="12" width="32" height="26" rx="3" />
      <path d="M8 20h32" />
      <path d="M15 27h12" />
      <path d="M15 32h18" />
    </>
  ),

  /* Nœud et flèche sortante : la notification poussée. */
  webhook: (
    <>
      <circle cx="14" cy="17" r="4" />
      <path d="M14 21v6a5 5 0 0 0 5 5h13" />
      <path d="M28 28l4 4-4 4" />
    </>
  ),

  /* Écu et coche : la donnée tenue. */
  shield: (
    <>
      <path d="M24 8l14 5v10c0 9-6 15-14 17-8-2-14-8-14-17V13z" />
      <path d="M18 24l4.5 4.5L31 19" />
    </>
  ),

  /* Registre : la trace conservée. */
  ledger: (
    <>
      <rect x="11" y="9" width="26" height="30" rx="3" />
      <path d="M17 17h14" />
      <path d="M17 24h14" />
      <path d="M17 31h9" />
    </>
  ),

  /* Équerres de cadrage : la capture. */
  frame: (
    <>
      <path d="M10 18v-5a3 3 0 0 1 3-3h5" />
      <path d="M30 10h5a3 3 0 0 1 3 3v5" />
      <path d="M38 30v5a3 3 0 0 1-3 3h-5" />
      <path d="M18 38h-5a3 3 0 0 1-3-3v-5" />
      <path d="M24 20v8" />
      <path d="M20 24h8" />
    </>
  ),

  /* Deux ovales reliés : la même personne des deux côtés. */
  match: (
    <>
      <ellipse cx="16" cy="24" rx="6" ry="8" />
      <ellipse cx="32" cy="24" rx="6" ry="8" />
      <path d="M22 24h4" />
    </>
  ),
};

export function Glyph({ name }: { name: GlyphName }) {
  return (
    <svg
      className="rm-glyph"
      viewBox="0 0 48 48"
      aria-hidden="true"
      focusable="false"
      fill="none"
      stroke="currentColor"
      strokeWidth={1.25}
      strokeLinecap="round"
      strokeLinejoin="round"
    >
      {PATHS[name]}
    </svg>
  );
}
