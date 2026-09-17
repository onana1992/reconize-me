import { redirect } from "next/navigation";
import { parseProduct } from "../../../../../../lib/parse-product";
import { integrationTabHref } from "../../../../../../lib/products";

export default async function IntegrationInstallRedirect({
  params,
}: {
  params: Promise<{ product: string; id: string }>;
}) {
  const resolved = await params;
  const productId = await parseProduct(Promise.resolve({ product: resolved.product }));
  redirect(integrationTabHref(productId, resolved.id, "install"));
}
