"use client";

import { useEffect, useId, useRef, useState } from "react";
import { useRouter } from "next/navigation";
import { LOCALES, setLocaleAction, useLocale, useT } from "../i18n/client";

export function LanguageMenu() {
  const t = useT();
  const locale = useLocale();
  const router = useRouter();
  const [open, setOpen] = useState(false);
  const [pending, setPending] = useState(false);
  const rootRef = useRef<HTMLDivElement>(null);
  const menuId = useId();

  useEffect(() => {
    if (!open) {
      return;
    }

    function onPointer(event: MouseEvent) {
      if (!rootRef.current?.contains(event.target as Node)) {
        setOpen(false);
      }
    }

    function onKey(event: KeyboardEvent) {
      if (event.key === "Escape") {
        setOpen(false);
      }
    }

    document.addEventListener("mousedown", onPointer);
    document.addEventListener("keydown", onKey);
    return () => {
      document.removeEventListener("mousedown", onPointer);
      document.removeEventListener("keydown", onKey);
    };
  }, [open]);

  async function choose(next: (typeof LOCALES)[number]) {
    if (next === locale) {
      setOpen(false);
      return;
    }
    setPending(true);
    await setLocaleAction(next);
    setOpen(false);
    setPending(false);
    router.refresh();
  }

  return (
    <div className="rm-lang-menu" ref={rootRef}>
      <button
        type="button"
        data-variant="secondary"
        className="rm-lang-trigger"
        aria-label={t("common.language")}
        aria-haspopup="menu"
        aria-expanded={open}
        aria-controls={menuId}
        disabled={pending}
        onClick={() => setOpen((value) => !value)}
      >
        <svg width="16" height="16" viewBox="0 0 16 16" aria-hidden="true">
          <circle cx="8" cy="8" r="6.25" fill="none" stroke="currentColor" strokeWidth="1.4" />
          <path
            d="M2 8h12M8 2c-2.2 1.8-3.3 3.8-3.3 6S5.8 12.2 8 14c2.2-1.8 3.3-3.8 3.3-6S10.2 3.8 8 2Z"
            fill="none"
            stroke="currentColor"
            strokeWidth="1.4"
          />
        </svg>
        <span className="rm-lang-label">{t(locale === "fr" ? "locale.fr" : "locale.en")}</span>
        <svg width="12" height="12" viewBox="0 0 12 12" aria-hidden="true">
          <path d="M2.5 4.5 6 8l3.5-3.5" fill="none" stroke="currentColor" strokeWidth="1.4" strokeLinecap="round" />
        </svg>
      </button>
      {open ? (
        <div className="rm-lang-panel" id={menuId} role="menu">
          {LOCALES.map((candidate) => (
            <button
              key={candidate}
              type="button"
              role="menuitem"
              aria-current={candidate === locale ? "true" : undefined}
              disabled={pending}
              onClick={() => choose(candidate)}
            >
              {t(candidate === "fr" ? "locale.fr" : "locale.en")}
            </button>
          ))}
        </div>
      ) : null}
    </div>
  );
}
