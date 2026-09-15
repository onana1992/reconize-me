"use client";

import { CopyButton } from "../../../../../../components/copy-button";

export function Snippet({ label, value }: { label: string; value: string }) {
  return (
    <div className="rm-int-snippet">
      <div className="rm-int-snippet-head">
        <span>{label}</span>
        <CopyButton value={value} />
      </div>
      <pre>
        <code>{value}</code>
      </pre>
    </div>
  );
}
