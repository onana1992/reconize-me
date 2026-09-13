"use client";

import Link from "next/link";
import { usePathname } from "next/navigation";
import { useEffect, useId, useState, type ReactNode } from "react";
import { Logo, Wordmark } from "@kyc/brand";
import { useT } from "../i18n/client";
import { displayName, initials, tRole } from "../lib/labels";
import { PRODUCTS, PRODUCT_IDS } from "../lib/products";
import { LanguageMenu } from "./language-menu";
import { LogoutButton } from "./logout-button";
import { NavIcon, type IconName } from "./nav-icons";

const STORAGE_KEY = "rm-console-nav-collapsed";
const DESKTOP_MQ = "(min-width: 64rem)";

export type AppShellProps = {
  orgName: string;
  email: string;
  firstName: string;
  lastName: string;
  role: string;
  permissions: string[];
  children: ReactNode;
};

type NavLink = {
  href: string;
  labelKey: "console.nav.home" | "console.nav.billing" | "console.nav.team" | "console.nav.activity";
  icon: IconName;
  match: (path: string) => boolean;
  permission?: string;
};

const HOME: NavLink = {
  href: "/",
  labelKey: "console.nav.home",
  icon: "home",
  match: (path) => path === "/",
};

const SETTINGS: NavLink[] = [
  { href: "/settings/team", labelKey: "console.nav.team", icon: "team", match: (path) => path.startsWith("/settings/team") },
  {
    href: "/settings/billing",
    labelKey: "console.nav.billing",
    icon: "billing",
    match: (path) => path.startsWith("/settings/billing"),
  },
  {
    href: "/settings/activity",
    labelKey: "console.nav.activity",
    icon: "activity",
    match: (path) => path.startsWith("/settings/activity"),
    permission: "AUDIT_READ",
  },
];

function sectionTitle(
  path: string,
):
  | "console.top.home"
  | "console.top.identity"
  | "console.top.biometrics"
  | "console.top.aml"
  | "console.top.billing"
  | "console.top.keys"
  | "console.top.team"
  | "console.top.activity"
  | "console.top.account" {
  if (path.startsWith("/identity")) {
    return "console.top.identity";
  }
  if (path.startsWith("/biometrics")) {
    return "console.top.biometrics";
  }
  if (path.startsWith("/aml")) {
    return "console.top.aml";
  }
  if (path.startsWith("/settings/billing")) {
    return "console.top.billing";
  }
  if (path.startsWith("/settings/keys")) {
    return "console.top.keys";
  }
  if (path.startsWith("/settings/team")) {
    return "console.top.team";
  }
  if (path.startsWith("/settings/activity")) {
    return "console.top.activity";
  }
  if (path.startsWith("/settings/account")) {
    return "console.top.account";
  }
  return "console.top.home";
}

function isDesktop() {
  return window.matchMedia(DESKTOP_MQ).matches;
}

export function AppShell({
  orgName,
  email,
  firstName,
  lastName,
  role,
  permissions,
  children,
}: AppShellProps) {
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
  const initial = (orgName.trim().charAt(0) || "R").toUpperCase();
  const railCollapsed = desktop && collapsed;
  const profileName = displayName(firstName, lastName, email);
  const profileInitials = initials(firstName, lastName, email);
  const profileRole = role ? tRole(t, role) : "";

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
          <NavGroup collapsed={railCollapsed}>
            <NavItem item={HOME} pathname={pathname} collapsed={railCollapsed} />
          </NavGroup>
          <NavGroup label={t("console.nav.services")} collapsed={railCollapsed}>
            {PRODUCT_IDS.map((id) => {
              const product = PRODUCTS[id];
              const current = pathname === product.href || pathname.startsWith(`${product.href}/`);
              const label = t(product.navKey);
              return (
                <Link
                  key={product.id}
                  href={product.href}
                  className="rm-shell-item"
                  aria-current={current ? "page" : undefined}
                  title={railCollapsed ? label : undefined}
                >
                  <span className="rm-shell-ico">
                    <NavIcon name={product.icon} />
                  </span>
                  <span className="rm-shell-label">{label}</span>
                </Link>
              );
            })}
          </NavGroup>
          <NavGroup label={t("console.nav.settings")} collapsed={railCollapsed}>
            {SETTINGS.filter((item) => !item.permission || permissions.includes(item.permission)).map((item) => (
              <NavItem key={item.href} item={item} pathname={pathname} collapsed={railCollapsed} />
            ))}
          </NavGroup>
        </nav>
        <div className="rm-shell-foot">
          <div className="rm-shell-org" title={railCollapsed ? orgName : email || undefined}>
            <span className="rm-shell-initial" aria-hidden="true">
              {initial}
            </span>
            {railCollapsed ? null : (
              <div className="rm-shell-org-meta">
                <span className="rm-shell-org-name">{orgName}</span>
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
          {email ? (
            <Link
              href="/settings/account"
              className="rm-shell-profile"
              title={profileName}
              aria-current={pathname.startsWith("/settings/account") ? "page" : undefined}
            >
              <span className="rm-shell-initial" aria-hidden="true">
                {profileInitials}
              </span>
              <span className="rm-shell-profile-meta">
                <span className="rm-shell-profile-name">{profileName}</span>
                <span className="rm-shell-profile-role">{profileRole}</span>
              </span>
            </Link>
          ) : null}
        </header>
        <div className="rm-shell-body">{children}</div>
      </div>
    </div>
  );
}

function NavGroup({ label, collapsed, children }: { label?: string; collapsed: boolean; children: ReactNode }) {
  return (
    <div className="rm-shell-group">
      {label ? (
        <p className="rm-shell-group-label">{collapsed ? <span className="rm-sr-only">{label}</span> : label}</p>
      ) : null}
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
