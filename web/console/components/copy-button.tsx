"use client";

import { useState } from "react";
import { useT } from "../i18n/client";

type Props = { value: string; label?: string; copiedLabel?: string };

export function CopyButton({ value, label, copiedLabel }: Props) {
  const t = useT();
  const [copied, setCopied] = useState(false);
  const idle = label ?? t("common.copy");
  const done = copiedLabel ?? t("common.copied");

  async function copy() {
    try {
      await navigator.clipboard.writeText(value);
      setCopied(true);
      window.setTimeout(() => setCopied(false), 2000);
    } catch {
      setCopied(false);
    }
  }

  return (
    <button type="button" data-variant="secondary" onClick={copy}>
      {copied ? done : idle}
    </button>
  );
}
