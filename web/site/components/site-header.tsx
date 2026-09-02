"use client";

import { Dialog, DialogPanel, Disclosure, DisclosureButton, DisclosurePanel } from "@headlessui/react";
import {
  Bars3Icon,
  CheckCircleIcon,
  ChevronDownIcon,
  UserIcon,
  XMarkIcon,
} from "@heroicons/react/24/outline";
import { Wordmark } from "@kyc/brand";
import Link from "next/link";
import { usePathname } from "next/navigation";
import { useEffect, useState } from "react";
import type { ProductKey } from "../lib/catalog";
import type { Locale } from "../lib/locales";
import { HEADER_LINKS, href, type RouteKey } from "../lib/routes";
import { LOGIN_URL, SIGNUP_URL } from "../lib/site-config";
import { LanguageMenu } from "./language-menu";
import { ProductsMegaMenu } from "./products-mega-menu";

/*
 * Barre de navigation à deux niveaux, calquée sur la capture : bandeau
 * utilitaire sombre, barre blanche, mega-menu Produits.
 *
 * Composant client pour trois raisons, et trois seulement : marquer la page
 * courante (`usePathname`), ouvrir le menu des produits, ouvrir le tiroir
 * mobile.
 *
 * Le menu des produits est une **divulgation** : clic partout, et survol
 * en plus sur pointeur fin. Un menu uniquement au survol est inatteignable
 * au toucher et piège au clavier.
 *
 * Le tiroir mobile est un Dialog Headless UI, porté hors du <header> : un
 * `position: sticky` ferait sinon du tiroir un descendant coincé dans la barre.
 */

export type HeaderProduct = {
  id: ProductKey;
  route: RouteKey;
  name: string;
  note: string;
  available: boolean;
  badge: string;
};

export type HeaderLabels = {
  nav: Record<RouteKey, string>;
  products: string;
  signup: string;
  login: string;
  getVerified: string;
  mainNav: string;
  utilityNav: string;
  languageSwitch: string;
  homeLink: string;
  openMenu: string;
  closeMenu: string;
};

const navLinkClass =
  "relative inline-flex h-[var(--rm-header-main)] items-center text-sm font-medium text-rm-text-muted no-underline hover:text-rm-text after:absolute after:inset-x-0 after:bottom-0 after:h-[3px] after:bg-transparent hover:after:bg-rm-text aria-[current=page]:text-rm-text aria-[current=page]:after:bg-rm-text";

export function SiteHeader({
  locale,
  labels,
  products,
}: {
  locale: Locale;
  labels: HeaderLabels;
  products: HeaderProduct[];
}) {
  const pathname = usePathname();
  const [drawerOpen, setDrawerOpen] = useState(false);

  useEffect(() => {
    setDrawerOpen(false);
  }, [pathname]);

  useEffect(() => {
    const query = window.matchMedia("(min-width: 64rem)");
    const onChange = () => {
      if (query.matches) {
        setDrawerOpen(false);
      }
    };
    query.addEventListener("change", onChange);
    return () => query.removeEventListener("change", onChange);
  }, []);

  const current = (route: RouteKey) => (pathname === href(locale, route) ? "page" : undefined);

  return (
    <>
      <header className="sticky top-0 z-50">
        <div className="bg-rm-ink text-rm-ink-text">
          <div className="mx-auto flex h-[var(--rm-header-utility)] max-w-rm-wide items-center justify-between px-4 sm:px-6 lg:px-8">
            <nav className="flex items-center gap-5" aria-label={labels.utilityNav}>
              <a
                href={LOGIN_URL}
                className="inline-flex items-center gap-1.5 text-[13px] font-medium text-rm-ink-text no-underline hover:text-white"
              >
                <UserIcon className="size-3.5" aria-hidden="true" />
                {labels.login}
              </a>
              <Link
                href={href(locale, "identityVerification")}
                className="hidden items-center gap-1.5 text-[13px] font-medium text-rm-ink-text no-underline hover:text-white sm:inline-flex"
              >
                <CheckCircleIcon className="size-3.5" aria-hidden="true" />
                {labels.getVerified}
              </Link>
            </nav>
            <LanguageMenu locale={locale} label={labels.languageSwitch} tone="ink" />
          </div>
        </div>

        <div className="border-b border-rm-border bg-rm-surface">
          <div className="mx-auto flex h-[var(--rm-header-main)] max-w-rm-wide items-center px-4 sm:px-6 lg:px-8">
            <div className="flex flex-1">
              <Link
                href={href(locale, "home")}
                className="inline-flex items-center gap-2 font-semibold tracking-tight text-rm-text no-underline hover:text-rm-text max-[23.5rem]:[&_span]:hidden"
                aria-label={labels.homeLink}
              >
                <Wordmark size={26} />
              </Link>
            </div>

            <nav className="hidden items-center gap-8 lg:flex" aria-label={labels.mainNav}>
              <ProductsMegaMenu
                locale={locale}
                products={products}
                label={labels.products}
                current={current}
              />
              {HEADER_LINKS.map((route) => (
                <Link
                  key={route}
                  href={href(locale, route)}
                  aria-current={current(route)}
                  className={navLinkClass}
                >
                  {labels.nav[route]}
                </Link>
              ))}
            </nav>

            <div className="flex flex-1 items-center justify-end gap-3">
              <a
                href={SIGNUP_URL}
                className="hidden whitespace-nowrap rounded-full bg-rm-accent px-5 py-2 text-sm font-semibold text-rm-text-on-accent no-underline hover:bg-rm-accent-hover hover:text-rm-text-on-accent sm:inline-flex sm:items-center"
              >
                {labels.signup}
              </a>
              <button
                type="button"
                className="inline-flex size-11 items-center justify-center rounded-rm-md border border-rm-border-strong bg-rm-surface p-0 text-rm-text hover:bg-rm-bg lg:hidden"
                aria-expanded={drawerOpen}
                aria-label={drawerOpen ? labels.closeMenu : labels.openMenu}
                onPointerDown={(event) => event.preventDefault()}
                onClick={() => setDrawerOpen((open) => !open)}
              >
                {drawerOpen ? (
                  <XMarkIcon className="size-5" aria-hidden="true" />
                ) : (
                  <Bars3Icon className="size-5" aria-hidden="true" />
                )}
              </button>
            </div>
          </div>
        </div>
      </header>

      <Dialog open={drawerOpen} onClose={setDrawerOpen} className="lg:hidden">
        <DialogPanel className="fixed inset-x-0 bottom-0 top-[var(--rm-header-height)] z-40 overflow-y-auto bg-rm-surface px-5 py-6">
          <Disclosure as="div" defaultOpen className="border-b border-rm-border pb-4">
            <DisclosureButton className="group flex w-full items-center justify-between border-0 bg-transparent px-0 py-3 text-left text-lg font-medium text-rm-text hover:bg-transparent">
              {labels.products}
              <ChevronDownIcon className="size-5 group-data-open:rotate-180" aria-hidden="true" />
            </DisclosureButton>
            <DisclosurePanel className="grid gap-1 pb-2">
              {products.map((product) => (
                <Link
                  key={product.route}
                  href={href(locale, product.route)}
                  aria-current={current(product.route)}
                  className="block py-3 text-base text-rm-text no-underline aria-[current=page]:text-rm-accent"
                >
                  {product.name}
                </Link>
              ))}
            </DisclosurePanel>
          </Disclosure>

          <nav className="grid" aria-label={labels.mainNav}>
            {HEADER_LINKS.map((route) => (
              <Link
                key={route}
                href={href(locale, route)}
                aria-current={current(route)}
                className="border-b border-rm-border py-3 text-lg font-medium text-rm-text no-underline aria-[current=page]:text-rm-accent"
              >
                {labels.nav[route]}
              </Link>
            ))}
            <Link
              href={href(locale, "contact")}
              aria-current={current("contact")}
              className="border-b border-rm-border py-3 text-lg font-medium text-rm-text no-underline aria-[current=page]:text-rm-accent"
            >
              {labels.nav.contact}
            </Link>
            <Link
              href={href(locale, "identityVerification")}
              className="border-b border-rm-border py-3 text-lg font-medium text-rm-text no-underline"
            >
              {labels.getVerified}
            </Link>
          </nav>

          <div className="mt-6 grid gap-3">
            <a
              href={SIGNUP_URL}
              className="inline-flex items-center justify-center rounded-full bg-rm-accent px-5 py-3 text-sm font-semibold text-rm-text-on-accent no-underline hover:bg-rm-accent-hover hover:text-rm-text-on-accent"
            >
              {labels.signup}
            </a>
            <a
              href={LOGIN_URL}
              className="inline-flex items-center justify-center rounded-full border border-rm-border-strong bg-rm-surface px-5 py-3 text-sm font-semibold text-rm-text no-underline hover:bg-rm-bg hover:text-rm-text"
            >
              {labels.login}
            </a>
            <LanguageMenu locale={locale} label={labels.languageSwitch} tone="paper" />
          </div>
        </DialogPanel>
      </Dialog>
    </>
  );
}
