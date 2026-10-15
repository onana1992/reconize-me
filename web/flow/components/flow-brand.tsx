"use client";

import { Logo, flowTheme } from "@/brand";
import { LanguageSwitch } from "./language-switch";

export function FlowBrand() {
  return (
    <div className="rm-flow-brand">
      <span className="rm-flow-wordmark">
        <Logo size={26} />
        <span>{flowTheme.name}</span>
      </span>
      <LanguageSwitch />
    </div>
  );
}
