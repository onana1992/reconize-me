import { ProductShell } from "../../../components/product-shell";
import { parseProduct } from "../../../lib/parse-product";
import { PRODUCT_IDS } from "../../../lib/products";

export const dynamic = "force-dynamic";

export function generateStaticParams() {
  return PRODUCT_IDS.map((product) => ({ product }));
}

export default async function ProductLayout({
  children,
  params,
}: {
  children: React.ReactNode;
  params: Promise<{ product: string }>;
}) {
  const product = await parseProduct(params);
  return <ProductShell product={product}>{children}</ProductShell>;
}
