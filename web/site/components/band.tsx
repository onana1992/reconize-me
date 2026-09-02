import type { ReactNode } from "react";

/*
 * La bande : unité de rythme vertical du site.
 *
 * Une page ne compose que du contenu — l'espacement, la largeur de gouttière
 * et le fond viennent d'ici. Trois tons seulement :
 *
 *   paper  le blanc, par défaut
 *   stone  le blanc cassé, pour détacher une section sans la fermer
 *   ink    le vert-noir, réservé aux respirations et aux appels à l'action
 *
 * L'encre n'est pas un thème sombre : le produit reste clair. C'est du
 * contraste éditorial, et il se paie — deux bandes d'encre à la suite sur une
 * même page, c'est déjà trop.
 */

export type BandTone = "paper" | "stone" | "ink";

export function Band({
  children,
  tone = "paper",
  /** Trame de relevé en fond. À réserver aux bandes qui portent une image. */
  mesh = false,
  tight = false,
  labelledBy,
  id,
  /** `text` resserre sur la largeur de lecture confortable (45 rem). */
  width,
}: {
  children: ReactNode;
  tone?: BandTone;
  mesh?: boolean;
  tight?: boolean;
  labelledBy?: string;
  id?: string;
  width?: "text";
}) {
  return (
    <section
      id={id}
      className="rm-band"
      data-tone={tone}
      data-mesh={mesh ? "true" : undefined}
      data-tight={tight ? "true" : undefined}
      aria-labelledby={labelledBy}
    >
      <div className="rm-shell" data-width={width}>
        {children}
      </div>
    </section>
  );
}

/*
 * Chapeau de bande.
 *
 * `numeral` numérote la section dans le fil de la page. C'est un repère de
 * lecture, pas une décoration : il n'est renseigné que sur les pages assez
 * longues pour qu'on s'y perde.
 *
 * Il se fond dans le sur-titre au lieu de trôner au-dessus : posé en gros
 * chiffre, il entrait en concurrence avec la numérotation des étapes juste
 * en dessous, et la page affichait deux « 01 » qui ne comptaient pas la
 * même chose.
 */
export function BandHead({
  id,
  numeral,
  eyebrow,
  title,
  lead,
  size = "section",
}: {
  id?: string;
  numeral?: string;
  eyebrow?: string;
  title: string;
  lead?: string;
  size?: "section" | "sub";
}) {
  return (
    <div className="rm-band-head">
      {eyebrow || numeral ? (
        <p className="rm-eyebrow" data-rule="true">
          {numeral ? <span className="rm-eyebrow-index">{numeral}</span> : null}
          {eyebrow}
        </p>
      ) : null}
      <h2 id={id} className="rm-title" data-size={size === "sub" ? "sub" : undefined}>
        {title}
      </h2>
      {lead ? <p className="rm-lead">{lead}</p> : null}
    </div>
  );
}
