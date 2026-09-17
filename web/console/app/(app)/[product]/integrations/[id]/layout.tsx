import { notFound } from "next/navigation";
import { parseProduct } from "../../../../../lib/parse-product";
import { PRODUCTS } from "../../../../../lib/products";
import { requireMe } from "../../../../../lib/session";
import { loadIntegration } from "../load";
import { IntegrationWorkspace } from "./workspace";

export const dynamic = "force-dynamic";

export default async function IntegrationLayout({
  children,
  params,
}: {
  children: React.ReactNode;
  params: Promise<{ product: string; id: string }>;
}) {
  const resolved = await params;
  const productId = await parseProduct(Promise.resolve({ product: resolved.product }));
  const product = PRODUCTS[productId];
  if (!product.metered) {
    notFound();
  }
  const [, result] = await Promise.all([requireMe(), loadIntegration(resolved.id)]);
  if (!result.ok) {
    notFound();
  }

  return (
    <IntegrationWorkspace productId={productId} integration={result.data}>
      {children}
    </IntegrationWorkspace>
  );
}
