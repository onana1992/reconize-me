"use client";

import { useState } from "react";

type Props = { url: string };

export function CopyLinkButton({ url }: Props) {
  const [copied, setCopied] = useState(false);

  async function copy() {
    try {
      await navigator.clipboard.writeText(url);
      setCopied(true);
      window.setTimeout(() => setCopied(false), 2000);
    } catch {
      setCopied(false);
    }
  }

  return (
    <button type="button" data-variant="secondary" onClick={copy} aria-label="Copier le lien hosted flow">
      {copied ? "Copié" : "Copier le lien"}
    </button>
  );
}
