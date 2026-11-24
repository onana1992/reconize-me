import { notFound } from "next/navigation";
import { PageHeader } from "../../../../components/page-header";
import { getT } from "../../../../i18n";
import { consoleApi, type Enrollment } from "../../../../lib/api";
import { parseProduct } from "../../../../lib/parse-product";
import { requireMe, sessionCookieHeader } from "../../../../lib/session";
import { EnrollmentsTable } from "./enrollments-table";

export const dynamic = "force-dynamic";

export default async function EnrollmentsPage({ params }: { params: Promise<{ product: string }> }) {
  const productId = await parseProduct(params);
  if (productId !== "authentication") {
    notFound();
  }
  const [me, t, cookie] = await Promise.all([requireMe(), getT(), sessionCookieHeader()]);
  const canRead = me.permissions.includes("VERIFICATION_READ");
  const canRevoke = me.permissions.includes("VERIFICATION_WRITE");
  const result = canRead ? await consoleApi<Enrollment[]>("/v1/console/enrollments", cookie) : null;

  return (
    <main className="rm-idv">
      <PageHeader title={t("console.product.tab.enrollments")} />
      {!canRead ? (
        <div className="rm-idv-empty">
          <p className="rm-idv-empty-title">{t("console.authentications.locked")}</p>
        </div>
      ) : result && !result.ok ? (
        <div className="rm-idv-empty" role="alert">
          <p className="rm-idv-empty-title">{t("console.authentications.enrollmentsError")}</p>
        </div>
      ) : result?.ok ? (
        <EnrollmentsTable items={result.data} canRevoke={canRevoke} />
      ) : null}
    </main>
  );
}
