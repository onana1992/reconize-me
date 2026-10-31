import { redirect } from "next/navigation";
import { parseProduct } from "../../../../lib/parse-product";

export default async function ProductRulesPage({ params }: { params: Promise<{ product: string }> }) {
  const productId = await parseProduct(params);
  redirect(`/${productId}/integrations`);
}
