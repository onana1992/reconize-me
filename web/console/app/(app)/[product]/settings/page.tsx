import { redirect } from "next/navigation";
import { parseProduct } from "../../../../lib/parse-product";

export default async function ProductSettingsRedirect({ params }: { params: Promise<{ product: string }> }) {
  const product = await parseProduct(params);
  redirect(`/${product}`);
}
