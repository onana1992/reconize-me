"use client";

import { Menu, MenuButton, MenuItem, MenuItems } from "@headlessui/react";
import { ChevronDownIcon, GlobeAltIcon } from "@heroicons/react/24/outline";
import Link from "next/link";
import { usePathname } from "next/navigation";
import { LOCALES, LOCALE_LABELS, type Locale } from "../lib/locales";

function swapLocale(pathname: string, target: Locale): string {
  const segments = pathname.split("/");
  segments[1] = target;
  return segments.join("/");
}

/**
 * Sélecteur de langue en menu (globe + libellé), pour la barre utilitaire
 * et le tiroir. Le pied de page garde les pastilles `LanguageSwitch`.
 */
export function LanguageMenu({
  locale,
  label,
  tone = "ink",
}: {
  locale: Locale;
  label: string;
  tone?: "ink" | "paper";
}) {
  const pathname = usePathname();
  const onInk = tone === "ink";

  return (
    <Menu as="div" className="relative">
      <MenuButton
        aria-label={label}
        className={
          onInk
            ? "inline-flex items-center gap-1.5 rounded-rm-sm border-0 bg-transparent p-0 text-[13px] font-medium text-rm-ink-text hover:bg-transparent hover:text-white"
            : "inline-flex items-center gap-1.5 rounded-rm-sm border border-rm-border bg-rm-bg px-3 py-1.5 text-sm font-medium text-rm-text hover:bg-rm-surface"
        }
      >
        <GlobeAltIcon className="size-4" aria-hidden="true" />
        {LOCALE_LABELS[locale]}
        <ChevronDownIcon className="size-3.5" aria-hidden="true" />
      </MenuButton>
      <MenuItems className="absolute right-0 z-50 mt-2 min-w-40 origin-top-right rounded-rm-md border border-rm-border bg-rm-surface p-1 shadow-rm-md outline-none">
        {LOCALES.map((candidate) => (
          <MenuItem key={candidate}>
            <Link
              href={swapLocale(pathname, candidate)}
              hrefLang={candidate}
              lang={candidate}
              aria-current={candidate === locale ? "true" : undefined}
              className="block rounded-rm-sm px-3 py-2 text-sm text-rm-text no-underline data-focus:bg-rm-bg data-focus:text-rm-text aria-[current=true]:font-semibold"
            >
              {LOCALE_LABELS[candidate]}
            </Link>
          </MenuItem>
        ))}
      </MenuItems>
    </Menu>
  );
}
