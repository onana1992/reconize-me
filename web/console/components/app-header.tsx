"use client";

import Link from "next/link";
import { usePathname } from "next/navigation";
import { useEffect, useState } from "react";
import { Wordmark } from "@kyc/brand";
import { LogoutButton } from "./logout-button";

const MAIN = [
  { href: "/", label: "Accueil", match: (path: string) => path === "/" },
  {
    href: "/verifications",
    label: "Vérifications",
    match: (path: string) =>
      path === "/verifications" || (path.startsWith("/verifications/") && !path.startsWith("/verifications/new")),
  },
  {
    href: "/verifications/new",
    label: "Nouvelle vérification",
    match: (path: string) => path.startsWith("/verifications/new"),
  },
];

const SETTINGS = [
  { href: "/settings/keys", label: "Clés" },
  { href: "/settings/team", label: "Équipe" },
  { href: "/settings/account", label: "Compte" },
];

function active(pathname: string, href: string, match?: (path: string) => boolean) {
  if (match) {
    return match(pathname);
  }
  return pathname === href || pathname.startsWith(`${href}/`);
}

export function AppHeader({ orgName }: { orgName: string }) {
  const pathname = usePathname();
  const [open, setOpen] = useState(false);

  useEffect(() => {
    setOpen(false);
  }, [pathname]);

  return (
    <header className="rm-app-header">
      <Link href="/" className="rm-app-brand">
        <Wordmark size={26} />
      </Link>
      <button
        type="button"
        data-variant="secondary"
        className="rm-nav-toggle"
        aria-expanded={open}
        aria-controls="rm-app-nav"
        onClick={() => setOpen((value) => !value)}
      >
        {open ? "Fermer" : "Menu"}
      </button>
      <nav id="rm-app-nav" className="rm-app-nav" data-open={open ? "true" : undefined}>
        {MAIN.map((item) => (
          <Link
            key={item.href}
            href={item.href}
            aria-current={active(pathname, item.href, item.match) ? "page" : undefined}
          >
            {item.label}
          </Link>
        ))}
        <span className="rm-app-nav-split" aria-hidden="true" />
        {SETTINGS.map((item) => (
          <Link
            key={item.href}
            href={item.href}
            aria-current={active(pathname, item.href) ? "page" : undefined}
          >
            {item.label}
          </Link>
        ))}
        <span className="rm-app-org">{orgName}</span>
        <LogoutButton />
      </nav>
    </header>
  );
}
