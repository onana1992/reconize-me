import type { ReactNode } from "react";

export function ProductTabsFallback({ children }: { children: ReactNode }) {
  return (
    <>
      <div className="rm-product-bar" data-env="sandbox" aria-hidden="true" />
      <div className="rm-page" data-env="sandbox">
        <div className="rm-dash">{children}</div>
      </div>
    </>
  );
}
