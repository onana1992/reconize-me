import type { ReactNode } from "react";

export function ProductTabsFallback({ children }: { children: ReactNode }) {
  return (
    <>
      <div className="rm-product-bar" aria-hidden="true" />
      <div className="rm-page">
        <div className="rm-dash">{children}</div>
      </div>
    </>
  );
}
