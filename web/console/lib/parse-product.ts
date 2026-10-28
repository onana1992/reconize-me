import { notFound } from "next/navigation";
import { isProductId, type ProductId } from "./products";

export async function parseProduct(params: Promise<{ product: string }>): Promise<ProductId> {
  const { product } = await params;
  if (!isProductId(product) || product === "biometrics") {
    notFound();
  }
  return product;
}
