import { notFound, redirect } from "next/navigation";
import { resolveEnvironment, withEnvironment } from "../../../../../lib/environment";
import { parseProduct } from "../../../../../lib/parse-product";

export const dynamic = "force-dynamic";

export default async function NewVerificationPage({
  params,
  searchParams,
}: {
  params: Promise<{ product: string }>;
  searchParams: Promise<{ env?: string }>;
}) {
  const productId = await parseProduct(params);
  if (productId !== "identity") {
    notFound();
  }
  const env = resolveEnvironment((await searchParams).env);
  redirect(withEnvironment("/identity", env));
}
