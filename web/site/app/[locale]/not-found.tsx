import Link from "next/link";
import { DEFAULT_LOCALE } from "../../lib/locales";
import { getMessages } from "../../lib/messages";
import { href } from "../../lib/routes";

/*
 * Le middleware préfixe toute URL sans langue : une adresse inconnue arrive
 * donc déjà sous une langue, et ce 404 est rendu dans la bonne coquille.
 * `params` n'est pas transmis aux pages 404 — on retombe sur le français.
 */
export default function NotFound() {
  const messages = getMessages(DEFAULT_LOCALE);

  return (
    <section className="rm-band">
      <div className="rm-shell" data-width="text">
        <p className="rm-eyebrow" data-rule="true">
          404
        </p>
        <h1 className="rm-title" data-size="hero">
          {messages.notFound.title}
        </h1>
        <p className="rm-lead" data-size="hero">
          {messages.notFound.body}
        </p>
        <div className="rm-actions">
          <Link className="rm-button" href={href(DEFAULT_LOCALE, "home")}>
            {messages.notFound.action}
          </Link>
        </div>
      </div>
    </section>
  );
}
