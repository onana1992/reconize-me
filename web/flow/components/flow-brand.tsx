"use client";

import { Wordmark } from "@kyc/brand";
import { LanguageSwitch } from "./language-switch";

export function FlowBrand() {
  return (
    <div className="rm-flow-brand">
      <span className="rm-flow-wordmark">
        <Wordmark size={24} />
      </span>
      <LanguageSwitch />
    </div>
  );
}
