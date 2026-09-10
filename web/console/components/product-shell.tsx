import { Suspense, type ReactNode } from "react";
import type { ProductId } from "../lib/products";
import { ProductChrome } from "./product-chrome";
import { ProductTabsFallback } from "./product-tabs";

export function ProductShell({ product, children }: { product: ProductId; children: ReactNode }) {
  return (
    <Suspense fallback={<ProductTabsFallback>{children}</ProductTabsFallback>}>
      <ProductChrome product={product}>{children}</ProductChrome>
    </Suspense>
  );
}
