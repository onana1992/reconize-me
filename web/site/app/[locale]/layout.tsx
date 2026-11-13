import type { Metadata } from "next";
import { DM_Sans, Epilogue, JetBrains_Mono, Plus_Jakarta_Sans } from "next/font/google";
import "@/brand/tokens.css";
import "@/brand/base.css";
import "@/brand/site.css";
import "../globals.css";
import { SiteFooter } from "../../components/site-footer";
import { SiteHeader, type HeaderProduct } from "../../components/site-header";
import { PRODUCT_ORDER, PRODUCT_ROUTE } from "../../lib/catalog";
import { DEFAULT_LOCALE, LOCALES, isLocale, type Locale } from "../../lib/locales";
import { getMessages } from "../../lib/messages";
import { metadataBase } from "../../lib/metadata";
import { COMPANY_NAV, LEGAL_NAV, PRODUCT_NAV, RESOURCE_NAV } from "../../lib/routes";
import { INDEXABLE } from "../../lib/site-config";

/*
 * Racine du site. Le layout vit sous `[locale]` parce que l'attribut `lang` de
 * <html> dépend de la langue : le poser plus haut obligerait à mentir sur l'une
 * des deux versions.
 */

/*
 * Fontes.
 *
 * `next/font` les télécharge au build et les sert depuis notre domaine : aucune
 * requête vers un tiers, aucun décalage de mise en page, et rien à demander au
 * visiteur en matière de cookies.
 *
 * Une seule famille de texte (RG-BRAND-03) : Plus Jakarta Sans, un
 * néo-grotesque plus distinctif qu'Inter, toujours lisible pour une
 * plateforme d'identité. Le monospace n'est pas une seconde voix de marque,
 * c'est l'outil des codes, des quantités et des extraits d'API — il porte
 * déjà son propre token depuis M0.
 *
 * Les deux fontes sont branchées sur `--rm-font-brand` / `--rm-font-brand-mono`,
 * les points d'accroche prévus dans `tokens.css` : la vitrine habille la marque
 * sans qu'aucune valeur de token bouge, donc sans toucher console ni flow.
 */
const sans = Plus_Jakarta_Sans({
  subsets: ["latin", "latin-ext"],
  display: "swap",
  variable: "--rm-font-brand",
});

const mono = JetBrains_Mono({
  subsets: ["latin"],
  display: "swap",
  variable: "--rm-font-brand-mono",
});

/*
 * Hero seulement, calqué sur smile.id : Epilogue 500 pour le titre,
 * DM Sans 400 pour le sous-titre. Le reste du site garde Plus Jakarta Sans.
 */
const display = Epilogue({
  subsets: ["latin", "latin-ext"],
  weight: "500",
  display: "swap",
  variable: "--rm-font-display",
});

const heroText = DM_Sans({
  subsets: ["latin", "latin-ext"],
  weight: "400",
  display: "swap",
  variable: "--rm-font-hero-text",
});

export function generateStaticParams() {
  return LOCALES.map((locale) => ({ locale }));
}

/** Hors des langues connues, 404 : pas de rendu à la demande. */
export const dynamicParams = false;

type LayoutProps = {
  children: React.ReactNode;
  params: Promise<{ locale: string }>;
};

export async function generateMetadata({
  params,
}: {
  params: Promise<{ locale: string }>;
}): Promise<Metadata> {
  const { locale } = await params;
  const messages = getMessages(isLocale(locale) ? locale : DEFAULT_LOCALE);

  return {
    metadataBase,
    title: {
      default: `${messages.meta.siteName} — ${messages.home.metaTitle}`,
      template: messages.meta.titleTemplate,
    },
    description: messages.home.metaDescription,
    applicationName: messages.meta.siteName,
    robots: { index: INDEXABLE, follow: INDEXABLE },
  };
}

export default async function LocaleLayout({ children, params }: LayoutProps) {
  const { locale: raw } = await params;
  const locale: Locale = isLocale(raw) ? raw : DEFAULT_LOCALE;
  const messages = getMessages(locale);

  /* Le menu des produits est construit ici, à partir du catalogue : la barre de
     navigation ne décide pas de ce qui est vendable, elle l'affiche. */
  const products: HeaderProduct[] = PRODUCT_ORDER.filter((product) =>
    PRODUCT_NAV.includes(PRODUCT_ROUTE[product]),
  ).map((product) => ({
    id: product,
    route: PRODUCT_ROUTE[product],
    name: messages.products[product].name,
    note: messages.products[product].tagline,
  }));

  const companyName = {
    company: messages.nav.about,
    contact: messages.nav.contactUs,
  } as const;
  const companyLinks = COMPANY_NAV.map((route) => ({
    route,
    name: companyName[route],
  }));

  const resourceLinks = RESOURCE_NAV.map((route) => ({
    route,
    name: messages.nav[route],
  }));

  const legalLinks = LEGAL_NAV.map((route) => ({
    route,
    name: messages.nav[route],
  }));

  return (
    <html
      lang={locale}
      className={`${sans.variable} ${mono.variable} ${display.variable} ${heroText.variable}`}
      suppressHydrationWarning
    >
      <head>
        {/* Sans JavaScript, l'apparition au défilement ne se déclenchera jamais :
            on neutralise son état de départ plutôt que de laisser une page
            blanche. Le cas `prefers-reduced-motion` est traité en CSS. */}
        <noscript>
          <style>{".rm-reveal{opacity:1!important;transform:none!important}"}</style>
        </noscript>
      </head>
      <body suppressHydrationWarning>
        <div className="rm-site">
          <a className="rm-skip" href="#rm-content">
            {messages.a11y.skipToContent}
          </a>

          <SiteHeader
            locale={locale}
            products={products}
            companyLinks={companyLinks}
            resourceLinks={resourceLinks}
            legalLinks={legalLinks}
            labels={{
              nav: messages.nav,
              products: messages.nav.products,
              resources: messages.nav.resources,
              legal: messages.footer.legal,
              signup: messages.actions.signup,
              login: messages.actions.login,
              mainNav: messages.a11y.mainNav,
              utilityNav: messages.a11y.utilityNav,
              languageSwitch: messages.a11y.languageSwitch,
              homeLink: messages.a11y.homeLink,
              openMenu: messages.a11y.openMenu,
              closeMenu: messages.a11y.closeMenu,
            }}
          />

          <main id="rm-content">{children}</main>
          <SiteFooter locale={locale} />
        </div>
      </body>
    </html>
  );
}
