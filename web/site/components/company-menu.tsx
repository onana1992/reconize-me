"use client";

import { Popover, PopoverButton, PopoverPanel } from "@headlessui/react";
import { ChevronDownIcon } from "@heroicons/react/24/outline";
import Link from "next/link";
import { useRef } from "react";
import type { Locale } from "../lib/locales";
import { href, type RouteKey } from "../lib/routes";

const HOVER_CLOSE_MS = 140;

export type CompanyLink = {
  route: RouteKey;
  name: string;
};

export function CompanyMenu({
  locale,
  label,
  links,
  current,
}: {
  locale: Locale;
  label: string;
  links: CompanyLink[];
  current: (route: RouteKey) => "page" | undefined;
}) {
  const closeTimer = useRef<number>(0);

  return (
    <Popover className="relative">
      {({ close }) => {
        const cancelClose = () => window.clearTimeout(closeTimer.current);
        const scheduleClose = () => {
          cancelClose();
          closeTimer.current = window.setTimeout(close, HOVER_CLOSE_MS);
        };

        return (
          <div onMouseEnter={cancelClose} onMouseLeave={scheduleClose}>
            <PopoverButton className="relative inline-flex h-[var(--rm-header-main)] items-center gap-1 rounded-none border-0 bg-transparent p-0 text-sm font-medium text-rm-text-muted shadow-none hover:!bg-transparent hover:text-rm-text focus-visible:rounded-none focus-visible:outline-none active:!bg-transparent after:absolute after:inset-x-0 after:bottom-0 after:h-[3px] after:bg-transparent hover:after:bg-rm-text data-open:text-rm-text data-open:after:bg-rm-text focus-visible:after:bg-rm-text">
              {label}
              <ChevronDownIcon className="size-3.5" aria-hidden="true" />
            </PopoverButton>

            <PopoverPanel
              transition
              className="absolute left-0 top-full z-50 min-w-56 pt-3 transition duration-200 ease-rm data-closed:translate-y-1 data-closed:opacity-0"
            >
              <div className="grid gap-1 rounded-rm-lg border border-rm-border bg-rm-surface p-2 shadow-rm-lg">
                {links.map((link) => (
                  <Link
                    key={link.route}
                    href={href(locale, link.route)}
                    aria-current={current(link.route)}
                    onClick={() => close()}
                    className="rounded-rm-md px-3 py-2.5 text-sm font-medium text-rm-text no-underline hover:bg-rm-bg aria-[current=page]:text-rm-accent"
                  >
                    {link.name}
                  </Link>
                ))}
              </div>
            </PopoverPanel>
          </div>
        );
      }}
    </Popover>
  );
}
