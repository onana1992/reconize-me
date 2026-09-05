"use client";

import { useState } from "react";

type Props = { value: string; label?: string };

export function CopyButton({ value, label = "Copier" }: Props) {
  const [copied, setCopied] = useState(false);

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
      {copied ? "Copié" : label}
    </button>
  );
}
