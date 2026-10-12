import { ProductShell } from "../../../components/product-shell";
import { parseProduct } from "../../../lib/parse-product";

export const dynamic = "force-dynamic";

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
