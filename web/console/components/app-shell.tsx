"use client";

import Link from "next/link";
import { usePathname } from "next/navigation";
import { useEffect, useId, useState, type ReactNode } from "react";
import { Logo, Wordmark } from "@kyc/brand";
import { useT } from "../i18n/client";
import { LanguageMenu } from "./language-menu";
import { LogoutButton } from "./logout-button";
import { NavIcon, type IconName } from "./nav-icons";

const STORAGE_KEY = "rm-console-nav-collapsed";
const DESKTOP_MQ = "(min-width: 64rem)";

export type AppShellProps = {
  orgName: string;
  orgSlug: string;
  email: string;
  role: string;
  plan: string;
  children: ReactNode;
};

type NavLink = {
  href: string;
  labelKey: "console.nav.home" | "console.nav.keys" | "console.nav.team" | "console.nav.account";
  icon: IconName;
  match: (path: string) => boolean;
};

type SoonItem = {
  labelKey: "console.nav.idv" | "console.nav.biometric" | "console.nav.aml";
  icon: IconName;
};

const OVERVIEW: NavLink[] = [
  { href: "/", labelKey: "console.nav.home", icon: "home", match: (path) => path === "/" },
];

const SOON: SoonItem[] = [
  { labelKey: "console.nav.idv", icon: "idcard" },
  { labelKey: "console.nav.biometric", icon: "scan" },
  { labelKey: "console.nav.aml", icon: "search" },
];

const ORG: NavLink[] = [
  { href: "/settings/keys", labelKey: "console.nav.keys", icon: "key", match: (path) => path.startsWith("/settings/keys") },
  { href: "/settings/team", labelKey: "console.nav.team", icon: "team", match: (path) => path.startsWith("/settings/team") },
  {
    href: "/settings/account",
    labelKey: "console.nav.account",
    icon: "user",
    match: (path) => path.startsWith("/settings/account"),
  },
];

function sectionTitle(path: string): "console.top.home" | "console.top.keys" | "console.top.team" | "console.top.account" {
  if (path.startsWith("/settings/keys")) {
    return "console.top.keys";
  }
  if (path.startsWith("/settings/team")) {
    return "console.top.team";
  }
  if (path.startsWith("/settings/account")) {
    return "console.top.account";
  }
  return "console.top.home";
}

function isDesktop() {
  return window.matchMedia(DESKTOP_MQ).matches;
}

export function AppShell({ orgName, orgSlug, email, plan, children }: AppShellProps) {
  const t = useT();
  const pathname = usePathname();
  const navId = useId();
  const [collapsed, setCollapsed] = useState(false);
  const [mobileOpen, setMobileOpen] = useState(false);
  const [desktop, setDesktop] = useState(false);

  useEffect(() => {
    try {
      setCollapsed(window.localStorage.getItem(STORAGE_KEY) === "1");
    } catch {
      /* ignore */
    }
    const mq = window.matchMedia(DESKTOP_MQ);
    const sync = () => setDesktop(mq.matches);
    sync();
    mq.addEventListener("change", sync);
    return () => mq.removeEventListener("change", sync);
  }, []);

  useEffect(() => {
    setMobileOpen(false);
  }, [pathname]);

  useEffect(() => {
    if (!mobileOpen) {
      return;
    }
    function onKey(event: KeyboardEvent) {
      if (event.key === "Escape") {
        setMobileOpen(false);
      }
    }
    document.addEventListener("keydown", onKey);
    const previous = document.body.style.overflow;
    document.body.style.overflow = "hidden";
    return () => {
      document.removeEventListener("keydown", onKey);
      document.body.style.overflow = previous;
    };
  }, [mobileOpen]);

  function toggleNav() {
    if (isDesktop()) {
      setCollapsed((value) => {
        const next = !value;
        try {
          window.localStorage.setItem(STORAGE_KEY, next ? "1" : "0");
        } catch {
          /* ignore */
        }
        return next;
      });
      return;
    }
    setMobileOpen((value) => !value);
  }

  const expanded = desktop ? !collapsed : mobileOpen;
  const toggleLabel = desktop
    ? collapsed
      ? t("console.nav.expand")
      : t("console.nav.collapse")
    : mobileOpen
      ? t("console.nav.close")
      : t("console.nav.open");
  const planLabel = plan === "sandbox" ? t("console.plan.sandbox") : plan;
  const initial = (orgName.trim().charAt(0) || "R").toUpperCase();
  const railCollapsed = desktop && collapsed;

  return (
    <div
      className="rm-shell"
      data-collapsed={collapsed ? "true" : undefined}
      data-mobile-open={mobileOpen ? "true" : undefined}
    >
      {mobileOpen ? (
        <div className="rm-shell-backdrop" role="presentation" onClick={() => setMobileOpen(false)} />
      ) : null}
      <aside className="rm-shell-sidebar" id={navId}>
        <Link href="/" className="rm-app-brand rm-shell-brand">
          {railCollapsed ? <Logo size={24} title="Recogniz-Me" /> : <Wordmark size={24} />}
        </Link>
        <nav className="rm-shell-nav" aria-label={t("console.nav.label")}>
          <NavGroup label={t("console.nav.overview")} collapsed={railCollapsed}>
            {OVERVIEW.map((item) => (
              <NavItem key={item.href} item={item} pathname={pathname} collapsed={railCollapsed} />
            ))}
          </NavGroup>
          <NavGroup label={t("console.nav.solutions")} collapsed={railCollapsed}>
            {SOON.map((item) => (
              <SoonNavItem key={item.labelKey} item={item} />
            ))}
          </NavGroup>
          <NavGroup label={t("console.nav.organization")} collapsed={railCollapsed}>
            {ORG.map((item) => (
              <NavItem key={item.href} item={item} pathname={pathname} collapsed={railCollapsed} />
            ))}
          </NavGroup>
        </nav>
        <div className="rm-shell-foot">
          <div className="rm-shell-org" title={railCollapsed ? `${orgName} · ${orgSlug}` : email || undefined}>
            <span className="rm-shell-initial" aria-hidden="true">
              {initial}
            </span>
            {railCollapsed ? null : (
              <div className="rm-shell-org-meta">
                <span className="rm-shell-org-name">{orgName}</span>
                <span className="rm-shell-org-slug">
                  {orgSlug}
                  {planLabel ? ` · ${planLabel}` : ""}
                </span>
              </div>
            )}
          </div>
          <div className="rm-shell-foot-actions">
            <LanguageMenu />
            <LogoutButton />
          </div>
        </div>
      </aside>
      <div className="rm-shell-main">
        <header className="rm-shell-topbar">
          <button
            type="button"
            data-variant="secondary"
            className="rm-shell-toggle"
            aria-expanded={expanded}
            aria-controls={navId}
            aria-label={toggleLabel}
            onClick={toggleNav}
          >
            <NavIcon name={desktop ? (collapsed ? "expand" : "collapse") : mobileOpen ? "close" : "menu"} />
          </button>
          <p className="rm-shell-crumb">{t(sectionTitle(pathname))}</p>
        </header>
        <div className="rm-page">{children}</div>
      </div>
    </div>
  );
}

function NavGroup({ label, collapsed, children }: { label: string; collapsed: boolean; children: ReactNode }) {
  return (
    <div className="rm-shell-group">
      <p className="rm-shell-group-label">{collapsed ? <span className="rm-sr-only">{label}</span> : label}</p>
      {children}
    </div>
  );
}

function NavItem({ item, pathname, collapsed }: { item: NavLink; pathname: string; collapsed: boolean }) {
  const t = useT();
  const label = t(item.labelKey);
  const current = item.match(pathname);

  return (
    <Link
      href={item.href}
      className="rm-shell-item"
      aria-current={current ? "page" : undefined}
      title={collapsed ? label : undefined}
    >
      <span className="rm-shell-ico">
        <NavIcon name={item.icon} />
      </span>
      <span className="rm-shell-label">{label}</span>
    </Link>
  );
}

function SoonNavItem({ item }: { item: SoonItem }) {
  const t = useT();
  const label = t(item.labelKey);
  const hint = `${label} — ${t("console.nav.soon")}. ${t("console.home.waitlist")}`;

  return (
    <span className="rm-shell-item" data-soon="true" title={hint} aria-disabled="true">
      <span className="rm-shell-ico">
        <NavIcon name={item.icon} />
      </span>
      <span className="rm-shell-label">{label}</span>
      <span className="rm-sr-only">{t("console.nav.soon")}</span>
    </span>
  );
}
