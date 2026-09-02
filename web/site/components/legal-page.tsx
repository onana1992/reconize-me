import type { ReactNode } from "react";
import type { Locale } from "../lib/locales";
import { getMessages, type Messages } from "../lib/messages";
import { Band } from "./band";

/*
 * Page légale.
 *
 * Les trois documents sont des **brouillons** publiés avant relecture juridique
 * (roadmap M1). Le bandeau qui le dit est porté par ce composant partagé : il
 * ne peut donc pas être présent sur deux pages et oublié sur la troisième.
 *
 * La mise en page est celle d'un document, pas d'une page marketing : articles
 * numérotés, filets, et une colonne de tête collante qui garde le titre, l'état
 * du document et sa date sous les yeux pendant qu'on descend les articles. Un
 * texte qu'on doit pouvoir citer se parcourt ; il ne se scrolle pas à l'aveugle.
 */

export type LegalCopy = Messages["legal"]["terms"];

export function LegalPage({
  locale,
  title,
  copy,
  children,
}: {
  locale: Locale;
  title: string;
  copy: LegalCopy;
  /** Rendu après les articles : le DPA y met sa demande de version signable. */
  children?: ReactNode;
}) {
  const messages = getMessages(locale);
  const legal = messages.legal;

  return (
    <Band tight>
      <div className="rm-split" data-ratio="4-8" data-sticky="true">
        <div>
          <p className="rm-eyebrow" data-rule="true">
            {copy.eyebrow}
          </p>
          <h1 className="rm-title">{title}</h1>

          <div className="rm-notice" role="note">
            <h2>{legal.draftTitle}</h2>
            <p>{legal.draftBody}</p>
          </div>

          <p className="rm-hint">
            {legal.lastUpdatedLabel} : {legal.lastUpdated}
          </p>
        </div>

        <div>
          <ol className="rm-clauses">
            {copy.sections.map((section) => (
              <li className="rm-clause" key={section.heading}>
                <h2>{section.heading}</h2>
                <p>{section.body}</p>
              </li>
            ))}
          </ol>

          {children}
        </div>
      </div>
    </Band>
  );
}
