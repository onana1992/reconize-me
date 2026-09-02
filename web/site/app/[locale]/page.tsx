import type { Metadata } from "next";
import Image from "next/image";
import { PrimaryActions } from "../../components/actions";
import { getMessages } from "../../lib/messages";
import { pageMetadata } from "../../lib/metadata";
import { resolveLocale, type LocaleParams } from "../../lib/params";

/*
 * L'image d'ouverture. Une présence humaine, pas un dossier : de dos, sans
 * visage identifiable (RG-BRAND-11, RG-BRAND-12) et sans pièce (RG-BRAND-10).
 *
 * Import statique plutôt que chemin `/media/...` : l'optimiseur lit alors le
 * fichier sur disque au lieu de le récupérer par une requête HTTP sur
 * lui-même — c'est ce détour qui échouait — et les dimensions sont déduites à
 * la compilation au lieu d'être recopiées à la main, deux nombres qui se
 * désynchronisent le jour où l'image est remplacée.
 */
import heroPresence from "../../public/media/hero-presence.png";

/*
 * Accueil — vitrine courte : barre de navigation et hero, rien en dessous.
 * Le reste du site n'est pas servi ; les anciennes URL reviennent ici.
 */

export async function generateMetadata({ params }: LocaleParams): Promise<Metadata> {
  const locale = await resolveLocale(params);
  const messages = getMessages(locale);
  return pageMetadata(locale, "home", messages.home.metaTitle, messages.home.metaDescription);
}

export default async function HomePage({ params }: LocaleParams) {
  const locale = await resolveLocale(params);
  const copy = getMessages(locale).home;

  return (
    <section
      className="rm-hero flex min-h-[calc(100svh-var(--rm-header-height))] flex-col justify-center"
      data-tone="ink"
    >
      <div className="rm-hero-backdrop">
        <Image
          src={heroPresence}
          alt=""
          fill
          /* Élément LCP : ni différé, ni suspendu à un observateur. Sans
             `priority`, Next le charge en paresseux et le plus grand pixel
             de la page arrive après tout le reste.
             `alt` vide parce que l'image est décorative : le sens est dans
             le titre à côté, le répéter n'aiderait personne. */
          priority
          sizes="(min-width: 64rem) 60vw, 100vw"
        />
      </div>

      <div className="rm-shell">
        <div className="rm-hero-copy max-w-[40rem]">
          <h1 className="rm-title" data-size="hero">
            {copy.title}
          </h1>
          <p className="rm-lead" data-size="hero">
            {copy.lead}
          </p>
          <PrimaryActions locale={locale} />
          <p className="rm-hero-note">{copy.heroNote}</p>
        </div>
      </div>
    </section>
  );
}
