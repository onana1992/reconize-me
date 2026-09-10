import Link from "next/link";
import type { Locale } from "../lib/locales";
import { getMessages, type Messages } from "../lib/messages";
import { href } from "../lib/routes";
import { Band, BandHead } from "./band";
import { SignalList } from "./proof";

/*
 * Page d'un produit annoncé mais non livrable.
 *
 * Contrainte du CDC §6, reprise en kill de la roadmap : ces pages n'ont ni
 * bouton « Essayer » ni bouton « Souscrire ». Leur **unique** appel à l'action
 * est « Être prévenu ». Le composant est partagé par les deux teasers pour que
 * la règle ne puisse pas être respectée d'un côté et oubliée de l'autre — et
 * il n'expose délibérément aucune prop qui permettrait d'y ajouter un bouton
 * d'achat.
 *
 * Le dessin dit la même chose que le texte : pas de cadre de capture (il
 * signifierait « prêt à capturer »), et les contrôles à venir sont listés en
 * signaux à l'état « en attente » — l'anneau vide, jamais la coche verte.
 */

export type TeaserCopy = Messages["biometric"];

export function TeaserPage({
  locale,
  title,
  tagline,
  copy,
}: {
  locale: Locale;
  title: string;
  tagline: string;
  copy: TeaserCopy;
}) {
  const messages = getMessages(locale);

  return (
    <>
      <section className="rm-hero">
        <div className="rm-shell">
          <p className="rm-eyebrow" data-rule="true">
            {copy.eyebrow}
          </p>
          <h1 className="rm-title" data-size="hero">
            {title}
          </h1>
          <p className="rm-lead" data-size="hero">
            {tagline}
          </p>

          {/* Le premier bloc de la page dit qu'on ne peut rien acheter ici. */}
          <div className="rm-notice" role="note">
            <h2>{copy.noticeTitle}</h2>
            <p>{copy.noticeBody}</p>
          </div>
        </div>
      </section>

      <Band tone="stone" labelledBy="rm-idea">
        <div className="rm-split" data-ratio="5-7">
          <BandHead id="rm-idea" eyebrow={copy.ideaEyebrow} title={copy.problemTitle} />
          <div>
            <p className="rm-lead">{copy.problemBody}</p>
            <p>{copy.problemDetail}</p>
          </div>
        </div>
      </Band>

      <Band labelledBy="rm-planned">
        <div className="rm-split" data-ratio="4-8" data-sticky="true">
          <div>
            <BandHead id="rm-planned" title={copy.plannedTitle} lead={copy.plannedLead} />
          </div>

          <div>
            <SignalList
              label={copy.plannedTitle}
              stateLabels={messages.signalStates}
              items={copy.planned.map((item) => ({ name: item, state: "idle" as const }))}
            />

            <h3 className="rm-subhead">{copy.notYetTitle}</h3>
            <ul className="rm-checklist" data-mark="none">
              {copy.notYet.map((item) => (
                <li key={item}>{item}</li>
              ))}
            </ul>
          </div>
        </div>
      </Band>

      <Band tone="ink" labelledBy="rm-teaser-cta">
        <div className="rm-split" data-ratio="7-5">
          <div>
            <h2 id="rm-teaser-cta" className="rm-title">
              {copy.ctaTitle}
            </h2>
            <p className="rm-lead">{copy.ctaBody}</p>

            {/* Seul appel à l'action de la page. Rien qui ressemble à un
                achat, ni à un essai. */}
            <div className="rm-actions">
              <Link className="rm-button" href={href(locale, "contact")}>
                {messages.actions.notify}
              </Link>
            </div>
          </div>

          <div className="rm-band-foot" data-plain="true">
            <p>{copy.availableInstead}</p>
            <Link className="rm-link-action" href={href(locale, "identityVerification")}>
              {messages.actions.idvDetail}
            </Link>
          </div>
        </div>
      </Band>
    </>
  );
}
