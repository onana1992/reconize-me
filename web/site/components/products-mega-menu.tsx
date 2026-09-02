"use client";

import { Popover, PopoverButton, PopoverPanel } from "@headlessui/react";
import {
  CameraIcon,
  ChevronDownIcon,
  IdentificationIcon,
  ShieldExclamationIcon,
} from "@heroicons/react/24/outline";
import Link from "next/link";
import { useRef, type ComponentType, type SVGProps } from "react";
import type { ProductKey } from "../lib/catalog";
import type { Locale } from "../lib/locales";
import { href, type RouteKey } from "../lib/routes";
import type { HeaderProduct } from "./site-header";

type Icon = ComponentType<SVGProps<SVGSVGElement>>;

const PRODUCT_CHROME: Record<
  ProductKey,
  { Icon: Icon; iconClass: string }
> = {
  idv: {
    Icon: IdentificationIcon,
    iconClass: "bg-rm-accent-subtle text-rm-accent",
  },
  biometric: {
    Icon: CameraIcon,
    iconClass: "bg-rm-tone-info-bg text-rm-tone-info-fg",
  },
  aml: {
    Icon: ShieldExclamationIcon,
    iconClass: "bg-rm-tone-warning-bg text-rm-tone-warning-fg",
  },
};

const HOVER_CLOSE_MS = 140;

export function ProductsMegaMenu({
  locale,
  products,
  label,
  current,
}: {
  locale: Locale;
  products: HeaderProduct[];
  label: string;
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
              className="absolute left-1/2 top-full z-50 w-[min(40rem,calc(100vw-2rem))] -translate-x-1/2 pt-3 transition duration-200 ease-rm data-closed:translate-y-1 data-closed:opacity-0"
            >
              <div className="grid grid-cols-2 gap-x-10 gap-y-8 rounded-rm-lg border border-rm-border bg-rm-surface p-8 shadow-rm-lg">
                {products.map((product) => {
                  const chrome = PRODUCT_CHROME[product.id];
                  const Icon = chrome.Icon;
                  return (
                    <Link
                      key={product.route}
                      href={href(locale, product.route)}
                      aria-current={current(product.route)}
                      className="group grid grid-cols-[auto_1fr] gap-x-3 gap-y-2 text-rm-text no-underline hover:text-rm-text"
                    >
                      <span
                        className={`inline-flex size-8 items-center justify-center rounded-lg ${chrome.iconClass}`}
                      >
                        <Icon className="size-4" aria-hidden="true" />
                      </span>
                      <span className="min-w-0">
                        <span className="flex flex-wrap items-center gap-2 text-sm font-semibold">
                          {product.name}
                          <span
                            className="rm-badge text-[0.65rem]"
                            data-tone={product.available ? "success" : "neutral"}
                          >
                            {product.badge}
                          </span>
                        </span>
                        <span className="mt-1 block text-[13px] leading-snug text-rm-text-muted group-hover:text-rm-text">
                          {product.note}
                        </span>
                      </span>
                    </Link>
                  );
                })}
              </div>
            </PopoverPanel>
          </div>
        );
      }}
    </Popover>
  );
}
