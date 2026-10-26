"use client";

import { useEffect, useId, useLayoutEffect, useMemo, useRef, useState, type KeyboardEvent } from "react";
import { createPortal } from "react-dom";
import { useLocale, useT } from "../i18n/client";
import { countries } from "../lib/countries";

type PanelBox = {
  left: number;
  width: number;
  maxHeight: number;
  top?: number;
  bottom?: number;
};

function fold(value: string) {
  return value
    .normalize("NFD")
    .replace(/[\u0300-\u036f]/g, "")
    .toLowerCase();
}

export function CountrySelect({
  name,
  value,
  onChange,
  disabled,
}: {
  name: string;
  value: string;
  onChange: (code: string) => void;
  disabled?: boolean;
}) {
  const t = useT();
  const locale = useLocale();
  const listId = useId();
  const rootRef = useRef<HTMLDivElement>(null);
  const triggerRef = useRef<HTMLButtonElement>(null);
  const panelRef = useRef<HTMLDivElement>(null);
  const searchRef = useRef<HTMLInputElement>(null);
  const [open, setOpen] = useState(false);
  const [search, setSearch] = useState("");
  const [active, setActive] = useState(0);
  const [box, setBox] = useState<PanelBox | null>(null);
  const [portal, setPortal] = useState<HTMLElement | null>(null);

  const options = useMemo(() => countries(locale), [locale]);
  const selected = options.find((item) => item.code === value.trim().toUpperCase());
  const query = fold(search.trim());
  const matches = query
    ? options.filter((item) => fold(item.name).includes(query) || item.code.toLowerCase().includes(query))
    : options;
  const items = query ? matches : [{ code: "", name: t("common.countryNone") }, ...matches];

  useLayoutEffect(() => {
    if (!open) {
      setBox(null);
      return;
    }
    function place() {
      const rect = triggerRef.current?.getBoundingClientRect();
      if (!rect) {
        return;
      }
      const gap = 4;
      const below = window.innerHeight - rect.bottom - gap;
      const above = rect.top - gap;
      const upward = below < 220 && above > below;
      const maxHeight = Math.max(160, Math.min(320, upward ? above : below));
      setBox({
        left: rect.left,
        width: rect.width,
        maxHeight,
        top: upward ? undefined : rect.bottom + gap,
        bottom: upward ? window.innerHeight - rect.top + gap : undefined,
      });
    }
    place();
    window.addEventListener("resize", place);
    window.addEventListener("scroll", place, true);
    return () => {
      window.removeEventListener("resize", place);
      window.removeEventListener("scroll", place, true);
    };
  }, [open]);

  const panelReady = open && box !== null;

  useLayoutEffect(() => {
    if (!panelReady) {
      return;
    }
    searchRef.current?.focus();
    panelRef.current?.querySelector<HTMLElement>(`[data-index="${active}"]`)?.scrollIntoView({ block: "nearest" });
  }, [panelReady]);

  useEffect(() => {
    if (!open) {
      return;
    }
    const dialog = rootRef.current?.closest("dialog");
    function onPointer(event: PointerEvent) {
      const target = event.target as Node;
      if (rootRef.current?.contains(target) || panelRef.current?.contains(target)) {
        return;
      }
      setOpen(false);
    }
    function onCancel(event: Event) {
      event.preventDefault();
      setOpen(false);
    }
    document.addEventListener("pointerdown", onPointer);
    dialog?.addEventListener("cancel", onCancel);
    return () => {
      document.removeEventListener("pointerdown", onPointer);
      dialog?.removeEventListener("cancel", onCancel);
    };
  }, [open]);

  useEffect(() => {
    setActive((current) => Math.min(current, Math.max(items.length - 1, 0)));
  }, [items.length]);

  useEffect(() => {
    if (!open) {
      return;
    }
    const option = panelRef.current?.querySelector<HTMLElement>(`[data-index="${active}"]`);
    option?.scrollIntoView({ block: "nearest" });
  }, [active, open]);

  function openPanel() {
    if (disabled) {
      return;
    }
    const current = value.trim().toUpperCase();
    const index = options.findIndex((item) => item.code === current);
    setSearch("");
    setActive(index >= 0 ? index + 1 : 0);
    setPortal(rootRef.current?.closest("dialog") ?? document.body);
    setOpen(true);
  }

  function choose(code: string) {
    onChange(code);
    setOpen(false);
    triggerRef.current?.focus();
  }

  function onSearchKey(event: KeyboardEvent<HTMLInputElement>) {
    if (event.key === "Escape") {
      event.preventDefault();
      event.stopPropagation();
      setOpen(false);
      triggerRef.current?.focus();
      return;
    }
    if (items.length === 0) {
      return;
    }
    if (event.key === "ArrowDown") {
      event.preventDefault();
      setActive((current) => Math.min(current + 1, items.length - 1));
      return;
    }
    if (event.key === "ArrowUp") {
      event.preventDefault();
      setActive((current) => Math.max(current - 1, 0));
      return;
    }
    if (event.key === "Home") {
      event.preventDefault();
      setActive(0);
      return;
    }
    if (event.key === "End") {
      event.preventDefault();
      setActive(Math.max(items.length - 1, 0));
      return;
    }
    if (event.key === "Enter" && items[active]) {
      event.preventDefault();
      choose(items[active].code);
    }
  }

  const panel =
    open && portal && box ? (
      <div
        ref={panelRef}
        className="rm-country-panel"
        style={{
          left: box.left,
          width: box.width,
          maxHeight: box.maxHeight,
          top: box.top,
          bottom: box.bottom,
        }}
      >
        <input
          ref={searchRef}
          className="rm-country-search"
          type="search"
          role="combobox"
          autoComplete="off"
          spellCheck={false}
          aria-label={t("common.countrySearch")}
          aria-expanded="true"
          aria-controls={listId}
          aria-autocomplete="list"
          aria-activedescendant={items[active] ? `${listId}-${active}` : undefined}
          placeholder={t("common.countrySearch")}
          value={search}
          onChange={(event) => {
            setSearch(event.target.value);
            setActive(0);
          }}
          onKeyDown={onSearchKey}
        />
        {items.length === 0 ? (
          <p className="rm-country-empty">{t("common.countryEmpty")}</p>
        ) : (
          <ul id={listId} className="rm-country-list" role="listbox">
            {items.map((item, index) => (
              <li key={item.code || "none"}>
                <button
                  type="button"
                  id={`${listId}-${index}`}
                  data-index={index}
                  className="rm-country-option"
                  role="option"
                  aria-selected={item.code === value.trim().toUpperCase()}
                  data-active={index === active ? "true" : undefined}
                  onMouseEnter={() => setActive(index)}
                  onClick={() => choose(item.code)}
                >
                  <span>{item.name}</span>
                  {item.code ? <span className="rm-country-code">{item.code}</span> : null}
                </button>
              </li>
            ))}
          </ul>
        )}
      </div>
    ) : null;

  return (
    <div ref={rootRef} className="rm-country">
      <input type="hidden" name={name} value={value} />
      <button
        ref={triggerRef}
        type="button"
        className="rm-country-trigger"
        aria-haspopup="listbox"
        aria-expanded={open}
        aria-controls={listId}
        disabled={disabled}
        data-empty={selected ? undefined : "true"}
        onClick={() => (open ? setOpen(false) : openPanel())}
      >
        <span className="rm-country-value">{selected?.name ?? t("common.countryPlaceholder")}</span>
        {selected ? <span className="rm-country-code">{selected.code}</span> : null}
        <svg className="rm-country-chevron" width="12" height="12" viewBox="0 0 12 12" aria-hidden="true">
          <path d="M2.5 4.5 6 8l3.5-3.5" fill="none" stroke="currentColor" strokeWidth="1.4" strokeLinecap="round" />
        </svg>
      </button>
      {panel ? createPortal(panel, portal!) : null}
    </div>
  );
}
