import Link from "next/link";
import { Wordmark } from "@kyc/brand";
import type { Locale } from "../lib/locales";
import { getMessages } from "../lib/messages";
import { FOOTER_NAV, href } from "../lib/routes";
import { CONTACT_EMAIL } from "../lib/site-config";
import { LanguageSwitch } from "./language-switch";

/* Composant serveur : le pied de page n'a pas besoin de l'URL courante. Seule
   la bascule de langue qu'il contient tourne côté client. */

export function SiteFooter({ locale }: { locale: Locale }) {
  const messages = getMessages(locale);

  return (
    <footer className="rm-site-footer">
      <div className="rm-shell">
        <div className="rm-footer-grid">
          <div className="rm-footer-col">
            <Link
              href={href(locale, "home")}
              className="rm-site-brand"
              aria-label={messages.a11y.homeLink}
            >
              <Wordmark size={24} />
            </Link>
            <p className="rm-footer-baseline">{messages.meta.baseline}</p>
            <p className="rm-hint">
              <a href={`mailto:${CONTACT_EMAIL}`}>{CONTACT_EMAIL}</a>
            </p>
          </div>

          {FOOTER_NAV.map((group) => (
            <nav key={group.heading} className="rm-footer-col" aria-labelledby={`rm-footer-${group.heading}`}>
              <h2 id={`rm-footer-${group.heading}`}>{messages.footer[group.heading]}</h2>
              <ul>
                {group.items.map((key) => (
                  <li key={key}>
                    <Link href={href(locale, key)}>{messages.nav[key]}</Link>
                  </li>
                ))}
              </ul>
            </nav>
          ))}
        </div>

        <div className="rm-footer-bottom">
          <LanguageSwitch locale={locale} label={messages.a11y.languageSwitch} />
          <span>
            © {new Date().getFullYear()} {messages.meta.siteName}. {messages.footer.rights}
          </span>
          {/* Règle de vérité CDC §8.3 : l'absence de certification s'écrit. */}
          <span>{messages.footer.honesty}</span>
        </div>
      </div>
    </footer>
  );
}
