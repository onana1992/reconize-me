import { StatusBadge } from "@kyc/brand";
import { PageHeader } from "../../../../components/page-header";
import { getLocale, getT } from "../../../../i18n";
import { consoleApi, type Billing } from "../../../../lib/api";
import { formatCount, formatMinor } from "../../../../lib/pricing";
import { requireMe, sessionCookieHeader } from "../../../../lib/session";
import { formatUtc } from "../../../../lib/status";
import { CheckoutButton } from "./checkout-button";

export const dynamic = "force-dynamic";

export default async function OrgBillingPage({
  searchParams,
}: {
  searchParams: Promise<{ checkout?: string }>;
}) {
  const me = await requireMe();
  const t = await getT();
  const locale = await getLocale();
  const canRead = (me.permissions ?? []).includes("BILLING_READ");
  const canWrite = (me.permissions ?? []).includes("BILLING_WRITE");
  const checkout = (await searchParams).checkout;
  const billing = canRead
    ? await consoleApi<Billing>("/v1/console/billing", await sessionCookieHeader())
    : null;
  const data = billing?.ok ? billing.data : null;
  const balance = data?.balance_minor ?? me.balance_minor ?? 0;
  const liveUnlocked = data?.live_unlocked ?? me.live_unlocked ?? false;
  const packs = data?.packs ?? [5000, 10000, 25000, 50000];
  const identity = data?.usage.find((row) => row.product === "identity");
  const entries = data?.ledger.entries ?? [];

  return (
    <main className="rm-dash">
      <PageHeader
        eyebrow={t("console.settings.label")}
        title={t("console.nav.billing")}
        lead={t("console.billing.lead")}
      />
      {checkout === "success" ? <p className="rm-lead">{t("console.billing.checkoutSuccess")}</p> : null}
      {checkout === "cancel" ? <p className="rm-lead">{t("console.billing.checkoutCancel")}</p> : null}
      {canRead && billing && !billing.ok ? (
        <p role="alert" className="rm-alert">
          {billing.message}
        </p>
      ) : null}
      <section className="rm-kpi-grid" aria-label={t("console.billing.balance")}>
        <article className="rm-card rm-kpi">
          <p className="rm-eyebrow">{t("console.billing.balance")}</p>
          <p className="rm-kpi-value">{formatMinor(locale, balance)}</p>
          <p className="rm-lead">{t("console.billing.balanceHint")}</p>
        </article>
        <article className="rm-card rm-kpi">
          <p className="rm-eyebrow">{t("console.billing.liveStatus")}</p>
          <p className="rm-kpi-value">
            <StatusBadge
              label={liveUnlocked ? t("console.billing.liveOpen") : t("console.billing.liveLocked")}
              tone={liveUnlocked ? "success" : "warning"}
            />
          </p>
          <p className="rm-lead">{liveUnlocked ? t("console.billing.liveOpenHint") : t("console.billing.liveLockedHint")}</p>
        </article>
      </section>
      <section className="rm-section">
        <h2>{t("console.billing.topup")}</h2>
        <p className="rm-lead">{t("console.billing.topupLead")}</p>
        <p className="rm-lead">{t("console.billing.topupOwner")}</p>
        <p className="rm-lead">{t("console.billing.provisional")}</p>
        <div className="rm-link-grid">
          {packs.map((amount) => (
            <article key={amount} className="rm-card">
              <h3>{formatMinor(locale, amount)}</h3>
              <p className="rm-lead">{t("console.billing.topupPackHint")}</p>
              {canWrite ? <CheckoutButton packMinor={amount} /> : null}
            </article>
          ))}
        </div>
        {canWrite ? null : <p className="rm-lead">{t("console.billing.topupForbidden")}</p>}
      </section>
      <section className="rm-section">
        <h2>{t("console.billing.consumption")}</h2>
        <p className="rm-lead">{t("console.billing.consumptionLead")}</p>
        <div className="rm-card">
          <p>
            {t("console.nav.idv")} · {t("console.env.sandbox")} {formatCount(locale, identity?.sandbox_count ?? 0)} ·{" "}
            {t("console.env.live")} {formatCount(locale, identity?.live_count ?? 0)} · {t("console.billing.debit")}{" "}
            {formatMinor(locale, identity?.live_debit_minor ?? 0)}
          </p>
          <p className="rm-lead">
            {t("console.billing.unit")}: {formatMinor(locale, data?.unit_amount_minor ?? 900)}
          </p>
        </div>
      </section>
      <section className="rm-section">
        <h2>{t("console.billing.ledger")}</h2>
        {!canRead ? (
          <p className="rm-lead">{t("console.billing.topupForbidden")}</p>
        ) : entries.length === 0 ? (
          <div className="rm-empty">
            <p>{t("console.billing.ledgerEmpty")}</p>
          </div>
        ) : (
          <div className="rm-table-wrap">
            <table className="rm-table">
              <thead>
                <tr>
                  <th>{t("console.billing.when")}</th>
                  <th>{t("console.billing.type")}</th>
                  <th>{t("console.billing.amount")}</th>
                  <th>{t("console.billing.balance")}</th>
                </tr>
              </thead>
              <tbody>
                {entries.map((row) => (
                  <tr key={row.id}>
                    <td>{formatUtc(row.created_at, locale)}</td>
                    <td>
                      {row.entry_type === "topup" ? t("console.billing.entryTopup") : t("console.billing.entryDebit")}
                    </td>
                    <td>{formatMinor(locale, row.amount_minor)}</td>
                    <td>{formatMinor(locale, row.balance_after_minor)}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </section>
    </main>
  );
}
