"use client";

import { Wordmark } from "@kyc/brand";
import { LanguageSwitch } from "./language-switch";

export function FlowBrand() {
  return (
    <div className="rm-flow-brand">
      <Wordmark size={24} tone="accent" />
      <LanguageSwitch />
    </div>
  );
}
