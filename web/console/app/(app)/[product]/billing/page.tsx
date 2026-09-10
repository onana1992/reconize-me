import { redirect } from "next/navigation";

export default function ProductBillingRedirect() {
  redirect("/settings/billing");
}
