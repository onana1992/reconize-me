"use client";

import Link from "next/link";
import { usePathname } from "next/navigation";
import { LOCALES, LOCALE_LABELS, type Locale } from "../lib/locales";

/**
 * Les segments d'URL sont communs aux deux langues (voir `lib/routes.ts`) :
 * changer de langue est donc un simple échange du premier segment, et l'on
 * reste sur la page qu'on lisait au lieu de retomber sur l'accueil.
 */
function swapLocale(pathname: string, target: Locale): string {
  const segments = pathname.split("/");
  segments[1] = target;
  return segments.join("/");
}

export function LanguageSwitch({ locale, label }: { locale: Locale; label: string }) {
  const pathname = usePathname();

  return (
    <nav className="rm-lang" aria-label={label}>
      {LOCALES.map((candidate) => (
        <Link
          key={candidate}
          href={swapLocale(pathname, candidate)}
          hrefLang={candidate}
          lang={candidate}
          aria-label={LOCALE_LABELS[candidate]}
          aria-current={candidate === locale ? "true" : undefined}
        >
          {candidate}
        </Link>
      ))}
    </nav>
  );
}
