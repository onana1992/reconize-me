import Image from "next/image";
import type { ReactNode } from "react";

/*
 * Les porteurs d'image de la vitrine.
 *
 * Tous suivent la même règle, et c'est elle qui justifie le fichier : le
 * contenu est **interchangeable**. La capture pièce et le selfie arrivent en
 * M4, les appels AWS en M5 (voir la roadmap MVP) — la matière produit qu'un
 * site de ce calibre montre n'existe donc pas encore.
 *
 * Chaque porteur accepte soit une `source` d'image, soit des enfants. On
 * remplit aujourd'hui avec de l'illustration ; en M4 on passe une source et la
 * section ne bouge pas. Sans ça, la refonte se repaie dans deux sprints.
 *
 * Deux invariants tenus ici plutôt que laissés aux pages :
 *
 *   - le ratio est toujours figé, sinon la mise en page décale au chargement ;
 *   - une image sous du texte reçoit un voile, sinon le contraste n'est pas
 *     garanti puisqu'il dépend du contenu de la photographie.
 */

export type MediaSource = {
  src: string;
  /** Description réelle. Vide seulement si l'image est décorative. */
  alt: string;
  /** Requis par `next/image` hors import statique. */
  width: number;
  height: number;
  /** À poser sur la seule image au-dessus du pli : c'est l'élément LCP. */
  priority?: boolean;
};

/** Ratios autorisés. Une valeur libre finirait par dériver d'une page à l'autre. */
export type MediaRatio = "wide" | "portrait" | "square" | "phone" | "console";

/*
 * Le cadre photographique.
 *
 * `scrim` n'est pas un effet : sur une photographie, le contraste du texte
 * dépend de ce que la photo contient, donc il ne se calcule pas. Le voile le
 * rend déterministe.
 */
export function Frame({
  source,
  ratio = "wide",
  scrim = false,
  children,
  className,
}: {
  source?: MediaSource;
  ratio?: MediaRatio;
  scrim?: boolean;
  /** Contenu posé par-dessus, ou illustration de remplacement si pas de source. */
  children?: ReactNode;
  className?: string;
}) {
  return (
    <div
      className={className ? `rm-media ${className}` : "rm-media"}
      data-ratio={ratio}
      data-scrim={scrim ? "true" : undefined}
      data-empty={source ? undefined : "true"}
    >
      {source ? (
        <Image
          className="rm-media-image"
          src={source.src}
          alt={source.alt}
          width={source.width}
          height={source.height}
          priority={source.priority}
          sizes="(min-width: 64rem) 50vw, 100vw"
        />
      ) : null}

      {children ? <div className="rm-media-layer">{children}</div> : null}
    </div>
  );
}

/*
 * Cadre d'appareil.
 *
 * Le châssis est en CSS, pas en image : une photographie de téléphone se date
 * au modèle, et il faudrait la remplacer à chaque génération. L'encoche et le
 * bouton sont des pseudo-éléments.
 */
export function DeviceFrame({
  source,
  label,
  children,
  tilt,
}: {
  source?: MediaSource;
  /** Nommé pour les lecteurs d'écran : « écran de consentement », etc. */
  label: string;
  children?: ReactNode;
  /** Léger décalage vertical, pour composer une série de trois. */
  tilt?: "up" | "down";
}) {
  return (
    <figure className="rm-device" data-tilt={tilt} aria-label={label}>
      <div className="rm-device-screen">
        {source ? (
          <Image
            className="rm-media-image"
            src={source.src}
            alt={source.alt}
            width={source.width}
            height={source.height}
            sizes="(min-width: 64rem) 20rem, 60vw"
          />
        ) : (
          children
        )}
      </div>
    </figure>
  );
}

/*
 * Cadre de console : une barre de fenêtre, puis l'écran.
 *
 * La barre ne mime pas un navigateur (pas d'URL, pas d'onglets) : le visiteur
 * de la vitrine ne doit pas croire qu'il est déjà dans le produit.
 */
export function ConsoleFrame({
  source,
  title,
  children,
}: {
  source?: MediaSource;
  title: string;
  children?: ReactNode;
}) {
  /* `rm-screen-*` et non `rm-console-*` : le panneau de code de la page
     documentation possède déjà ce dernier préfixe. */
  return (
    <figure className="rm-screen">
      <div className="rm-screen-bar">
        <span className="rm-screen-dots" aria-hidden="true" />
        <span className="rm-screen-title">{title}</span>
      </div>

      <div className="rm-screen-body">
        {source ? (
          <Image
            className="rm-media-image"
            src={source.src}
            alt={source.alt}
            width={source.width}
            height={source.height}
            sizes="(min-width: 64rem) 40rem, 100vw"
          />
        ) : (
          children
        )}
      </div>
    </figure>
  );
}
