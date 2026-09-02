import Link from "next/link";
import { PRODUCT_AVAILABLE, PRODUCT_ROUTE, type ProductKey } from "../lib/catalog";
import type { Locale } from "../lib/locales";
import { getMessages } from "../lib/messages";
import { href } from "../lib/routes";

/*
 * Carte de la gamme.
 *
 * La disponibilité pilote l'apparence, pas seulement le badge : le produit
 * vendable est sur fond blanc et bordé de vert, une annonce est sur fond en
 * retrait et sans relief. Un visiteur pressé doit pouvoir trancher sans lire.
 *
 * La carte n'a qu'un seul geste, « voir la page » — jamais « essayer » ni
 * « souscrire », que le produit soit disponible ou non. L'appel à l'action
 * d'achat appartient à la bande de conclusion, pas au catalogue.
 */
export function OfferCard({ locale, product }: { locale: Locale; product: ProductKey }) {
  const messages = getMessages(locale);
  const copy = messages.products[product];
  const available = PRODUCT_AVAILABLE[product];

  return (
    <Link
      className="rm-offer"
      data-available={available ? "true" : "false"}
      href={href(locale, PRODUCT_ROUTE[product])}
    >
      <span className="rm-badge" data-tone={available ? "success" : "neutral"}>
        {available ? messages.badges.available : messages.badges.roadmap}
      </span>
      <h3>{copy.name}</h3>
      <p className="rm-offer-tagline">{copy.tagline}</p>
      <p>{copy.summary}</p>
      <span className="rm-offer-more">{messages.actions.readMore}</span>
    </Link>
  );
}
