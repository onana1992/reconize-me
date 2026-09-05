"use client";

import Link from "next/link";
import { usePathname } from "next/navigation";

const ITEMS = [
  { href: "/settings/keys", label: "Clés API" },
  { href: "/settings/team", label: "Équipe" },
  { href: "/settings/account", label: "Compte" },
];

export function SettingsNav() {
  const pathname = usePathname();

  return (
    <nav className="rm-subnav" aria-label="Paramètres">
      {ITEMS.map((item) => (
        <Link key={item.href} href={item.href} aria-current={pathname === item.href ? "page" : undefined}>
          {item.label}
        </Link>
      ))}
    </nav>
  );
}
