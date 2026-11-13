import Image from "next/image";
import { PrimaryActions } from "./actions";
import { Band } from "./band";
import { Reveal } from "./reveal";
import type { Locale } from "../lib/locales";
import type { Messages } from "../lib/messages";
import presence from "../public/media/close-presence.jpg";

/*
 * Fermeture de l'accueil.
 *
 * La page reste blanche. La carte est un panneau d'encre : le titre et les
 * deux actions à gauche, une photographie à droite. `alt` vide : l'image est
 * décorative, elle ne présente personne comme un client (RG-BRAND-11).
 */

export function HomeClose({ locale, copy }: { locale: Locale; copy: Messages["home"] }) {
  return (
    <Band tone="paper" labelledBy="home-close-title" className="rm-close-band">
      <Reveal>
        <div className="rm-close">
          <div className="rm-close-copy">
            <h2 id="home-close-title" className="rm-title" data-size="sub">
              {copy.closingTitle}
            </h2>
            <PrimaryActions locale={locale} />
          </div>

          <div className="rm-close-media">
            <Image
              src={presence}
              alt=""
              fill
              sizes="(min-width: 62rem) 34rem, 100vw"
            />
          </div>
        </div>
      </Reveal>
    </Band>
  );
}
