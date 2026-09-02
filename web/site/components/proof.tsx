import type { ReactNode } from "react";

/*
 * Le langage visuel du site : « la preuve se construit ».
 *
 * Quatre pièces, réutilisées partout, et rien d'autre. C'est ce qui rend le
 * site reconnaissable sans passer par la photographie ni par l'iconographie
 * de la surveillance : ni cadenas, ni globe, ni empreinte.
 *
 * Règle de vérité qui a dicté ces dessins (CDC §8.3) : le site n'a pas de
 * client à montrer, donc il ne montre personne. Le fragment documentaire est
 * une abstraction dessinée, pas la photo d'une pièce, et la zone portrait est
 * une trame — jamais un visage, réel ou synthétique.
 */

/** Le cadre de capture : quatre équerres. Ce qui entre dedans devient preuve. */
export function CaptureFrame({ children }: { children: ReactNode }) {
  return <div className="rm-frame">{children}</div>;
}

/* Bande de lecture optique. Le mot SPECIMEN y est écrit en clair : c'est un
   gabarit d'illustration, il ne doit pas pouvoir passer pour un vrai relevé. */
const MRZ_LINES = [
  "P<FRARECOGNIZ<ME<<SPECIMEN<<<<<<<<<<<<<<<<<<<",
  "0000000000FRA0001019M3001010<<<<<<<<<<<<<<08",
];

/**
 * Le fragment documentaire : une pièce d'identité abstraite.
 *
 * `label` porte la description accessible — le bloc est une illustration, et
 * il l'annonce plutôt que de laisser un lecteur d'écran énumérer des barres.
 */
export function AbstractDocument({
  kind,
  label,
  fieldsLabel,
}: {
  kind: string;
  label: string;
  fieldsLabel: string;
}) {
  return (
    <figure className="rm-doc" role="img" aria-label={label}>
      <div className="rm-doc-head" aria-hidden="true">
        <p className="rm-doc-kind">{kind}</p>
        <span className="rm-doc-kind">{fieldsLabel}</span>
      </div>

      <div className="rm-doc-body" aria-hidden="true">
        <div className="rm-doc-portrait" />
        <div className="rm-doc-fields">
          <span className="rm-doc-line" data-width="wide" />
          <span className="rm-doc-line" data-width="mid" />
          <span className="rm-doc-line" data-width="full" />
          <span className="rm-doc-line" data-width="short" />
          <span className="rm-doc-line" data-width="mid" />
        </div>
      </div>

      <p className="rm-doc-mrz" aria-hidden="true">
        {MRZ_LINES[0]}
        <br />
        {MRZ_LINES[1]}
      </p>

      <span className="rm-doc-scan" aria-hidden="true" />
    </figure>
  );
}

/*
 * Les signaux : la sortie réelle du produit.
 *
 * Un signal porte un libellé lisible, un code stable et un état. L'état est
 * dessiné par trois formes distinctes — coche, anneau, point — et doublé d'un
 * mot lu par les lecteurs d'écran : jamais la couleur seule (RG-BRAND-05).
 */
export type SignalState = "pass" | "watch" | "idle";

export type Signal = {
  name: string;
  /**
   * Code d'API. Stable, anglais, sans donnée personnelle : il ne se traduit pas.
   *
   * Optionnel, et c'est le point : les pages des produits annoncés listent des
   * contrôles à l'état `idle`, qui n'ont pas encore de code parce qu'ils n'ont
   * pas encore d'implémentation. Inventer un code ici reviendrait à publier une
   * API qui n'existe pas.
   */
  code?: string;
  state: SignalState;
};

export function SignalList({
  items,
  stateLabels,
  label,
}: {
  items: Signal[];
  stateLabels: Record<SignalState, string>;
  label?: string;
}) {
  return (
    <ul className="rm-signals" aria-label={label}>
      {items.map((signal) => (
        <li className="rm-signal" key={signal.code ?? signal.name} data-state={signal.state}>
          <span className="rm-signal-name">
            {signal.name}
            <span className="rm-sr-only"> — {stateLabels[signal.state]}</span>
          </span>
          {signal.code ? <code className="rm-signal-code">{signal.code}</code> : null}
        </li>
      ))}
    </ul>
  );
}

/*
 * La décision : trois issues, jamais un score nu.
 *
 * `reasons` reste optionnel parce qu'une approbation n'a pas de motif à
 * afficher, là où un refus en a toujours un.
 *
 * `titleAs` existe pour le plan du document. Quand le verdict illustre une
 * section — le hero, par exemple — son titre n'est pas un niveau de plan : le
 * poser en h3 sous un h1 fait sauter un rang de titre.
 */
export type VerdictState = "approved" | "review" | "rejected";

export function Verdict({
  state,
  stateLabel,
  title,
  titleAs = "h3",
  summary,
  reasons,
  reasonsLabel,
  children,
}: {
  state: VerdictState;
  stateLabel: string;
  title?: string;
  titleAs?: "h3" | "p";
  summary?: string;
  reasons?: string[];
  reasonsLabel?: string;
  children?: ReactNode;
}) {
  const Title = titleAs;

  return (
    <div className="rm-verdict" data-state={state}>
      <p className="rm-verdict-state">{stateLabel}</p>
      {title ? <Title className="rm-verdict-title">{title}</Title> : null}
      {summary ? <p>{summary}</p> : null}
      {reasons && reasons.length > 0 ? (
        <ul className="rm-reasons" aria-label={reasonsLabel}>
          {reasons.map((reason) => (
            <li key={reason}>{reason}</li>
          ))}
        </ul>
      ) : null}
      {children}
    </div>
  );
}
