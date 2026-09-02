"use client";

import { useId, useRef, useState, type ReactNode } from "react";

/*
 * Onglets.
 *
 * Ce que ça achète : de la densité. Trois couches d'architecture ou cinq cas
 * d'usage tiennent dans un écran au lieu de cinq écrans de défilement, et la
 * page cesse d'être une liste pour ressembler à un produit.
 *
 * Ce que ça coûte : du contenu masqué. Donc deux règles.
 *
 *   - Le premier panneau est toujours ouvert. Une section qui commence vide ne
 *     dit rien à celui qui ne clique pas.
 *   - Le contenu masqué reste dans le DOM (`hidden`), pas retiré du rendu :
 *     il doit être trouvable par la recherche du navigateur et par l'indexeur.
 *
 * Le motif suit le patron ARIA « tabs with manual activation » : les flèches
 * déplacent le focus **et** l'activation, Début et Fin vont aux extrémités.
 * L'activation automatique au focus est le bon choix ici parce qu'un panneau
 * est court et sans effet de bord — personne ne perd de saisie en survolant
 * les onglets au clavier.
 */

export type Tab = {
  /** Identifiant stable, employé dans les `id` ARIA. */
  key: string;
  label: string;
  /** Numérotation optionnelle, pour les couches d'architecture. */
  index?: string;
  panel: ReactNode;
};

export function Tabs({
  tabs,
  label,
  orientation = "horizontal",
}: {
  tabs: Tab[];
  /** Nomme la liste d'onglets : « Couches de l'architecture », etc. */
  label: string;
  orientation?: "horizontal" | "vertical";
}) {
  const base = useId();
  const [active, setActive] = useState(0);
  const buttons = useRef<(HTMLButtonElement | null)[]>([]);

  const move = (to: number) => {
    const next = (to + tabs.length) % tabs.length;
    setActive(next);
    buttons.current[next]?.focus();
  };

  const onKeyDown = (event: React.KeyboardEvent) => {
    const previousKey = orientation === "vertical" ? "ArrowUp" : "ArrowLeft";
    const nextKey = orientation === "vertical" ? "ArrowDown" : "ArrowRight";

    if (event.key === previousKey) {
      event.preventDefault();
      move(active - 1);
    } else if (event.key === nextKey) {
      event.preventDefault();
      move(active + 1);
    } else if (event.key === "Home") {
      event.preventDefault();
      move(0);
    } else if (event.key === "End") {
      event.preventDefault();
      move(tabs.length - 1);
    }
  };

  return (
    <div className="rm-tabs" data-orientation={orientation}>
      <div
        className="rm-tablist"
        role="tablist"
        aria-label={label}
        aria-orientation={orientation}
        onKeyDown={onKeyDown}
      >
        {tabs.map((tab, position) => (
          <button
            key={tab.key}
            ref={(node) => {
              buttons.current[position] = node;
            }}
            type="button"
            role="tab"
            id={`${base}-tab-${tab.key}`}
            className="rm-tab"
            aria-selected={position === active}
            aria-controls={`${base}-panel-${tab.key}`}
            /* Un seul onglet dans l'ordre de tabulation : on entre dans la
               liste, puis on circule aux flèches. C'est ce que le patron ARIA
               demande, et ça évite d'imposer cinq tabulations pour traverser. */
            tabIndex={position === active ? 0 : -1}
            onClick={() => setActive(position)}
          >
            {tab.index ? <span className="rm-tab-index">{tab.index}</span> : null}
            {tab.label}
          </button>
        ))}
      </div>

      {tabs.map((tab, position) => (
        <div
          key={tab.key}
          role="tabpanel"
          id={`${base}-panel-${tab.key}`}
          className="rm-tabpanel"
          aria-labelledby={`${base}-tab-${tab.key}`}
          hidden={position !== active}
          /* Le panneau est focalisable pour que la tabulation depuis l'onglet
             y entre, mais il n'est pas dans l'ordre naturel. */
          tabIndex={0}
        >
          {tab.panel}
        </div>
      ))}
    </div>
  );
}
