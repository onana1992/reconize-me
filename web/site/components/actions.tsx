import Link from "next/link";
import type { Locale } from "../lib/locales";
import { getMessages } from "../lib/messages";
import { href } from "../lib/routes";
import { SIGNUP_URL } from "../lib/site-config";

/**
 * Le couple d'appels à l'action du site (CDC §8.2) : « Créer un compte » en
 * primaire partout, la documentation ou les tarifs en secondaire.
 *
 * Un seul composant, pour qu'aucune page n'invente un troisième bouton vert —
 * et pour qu'aucune ne remplace l'inscription par une prise de rendez-vous
 * commerciale, qui n'est pas le chemin principal.
 */
export function PrimaryActions({
  locale,
  secondary = "docs",
}: {
  locale: Locale;
  secondary?: "docs" | "pricing" | "none";
}) {
  const messages = getMessages(locale);

  return (
    <div className="rm-actions">
      <a className="rm-button" href={SIGNUP_URL}>
        {messages.actions.signup}
      </a>

      {secondary === "docs" ? (
        <Link className="rm-button" data-variant="secondary" href={href(locale, "docs")}>
          {messages.actions.viewDocs}
        </Link>
      ) : null}

      {secondary === "pricing" ? (
        <Link className="rm-button" data-variant="secondary" href={href(locale, "pricing")}>
          {messages.actions.pricing}
        </Link>
      ) : null}
    </div>
  );
}
