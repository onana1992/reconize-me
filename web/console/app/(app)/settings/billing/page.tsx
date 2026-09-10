import { PageHeader } from "../../../../components/page-header";
import { getLocale, getT } from "../../../../i18n";
import { CREDIT_PACKS, formatMoney } from "../../../../lib/pricing";
import { requireMe } from "../../../../lib/session";

export default async function OrgBillingPage() {
  const me = await requireMe();
  const t = await getT();
  const locale = await getLocale();
  const canWrite = (me.permissions ?? []).includes("BILLING_WRITE");
  const balance = 0;

  return (
    <main className="rm-dash">
      <PageHeader
        eyebrow={t("console.settings.label")}
        title={t("console.nav.billing")}
        lead={t("console.billing.lead")}
      />
      <section className="rm-kpi-grid" aria-label={t("console.billing.balance")}>
        <article className="rm-card rm-kpi">
          <p className="rm-eyebrow">{t("console.billing.balance")}</p>
          <p className="rm-kpi-value">{formatMoney(locale, balance)}</p>
          <p className="rm-lead">{t("console.billing.balanceHint")}</p>
        </article>
      </section>
      <section className="rm-section">
        <h2>{t("console.billing.topup")}</h2>
        <p className="rm-lead">{t("console.billing.topupLead")}</p>
        <div className="rm-link-grid">
          {CREDIT_PACKS.map((amount) => (
            <article key={amount} className="rm-card">
              <h3>{formatMoney(locale, amount)}</h3>
              <p className="rm-lead">{t("console.billing.topupPackHint")}</p>
              {canWrite ? (
                <p>
                  <button type="button" disabled>
                    {t("console.billing.topupCard")}
                  </button>
                </p>
              ) : null}
            </article>
          ))}
        </div>
        {canWrite ? <p className="rm-lead">{t("console.billing.topupOwner")}</p> : <p className="rm-lead">{t("console.billing.topupForbidden")}</p>}
      </section>
      <section className="rm-section">
        <h2>{t("console.billing.ledger")}</h2>
        <div className="rm-empty">
          <p>{t("console.billing.ledgerEmpty")}</p>
        </div>
      </section>
    </main>
  );
}
