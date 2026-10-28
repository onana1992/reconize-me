import { StatusBadge } from "@/brand";
import { PageHeader } from "../../../../components/page-header";
import { getLocale, getT, type Locale } from "../../../../i18n";
import { consoleApi, type Billing } from "../../../../lib/api";
import { formatCount, formatMinor } from "../../../../lib/pricing";
import { requireMe, sessionCookieHeader } from "../../../../lib/session";
import { CheckoutButton } from "./checkout-button";
import { LedgerTable } from "./ledger-table";

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
    ? await consoleApi<Billing>("/v1/console/billing?limit=100", await sessionCookieHeader())
    : null;
  const data = billing?.ok ? billing.data : null;
  const balance = data?.balance_minor ?? me.balance_minor ?? 0;
  const liveUnlocked = data?.live_unlocked ?? me.live_unlocked ?? false;
  const packs = data?.packs ?? [5000, 10000, 25000, 50000];
  const identity = data?.usage.find((row) => row.product === "identity");
  const aml = data?.usage.find((row) => row.product === "aml");
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
          <p className="rm-lead">{t("console.billing.liveHint")}</p>
        </article>
      </section>
      <section className="rm-section">
        <h2>{t("console.billing.topup")}</h2>
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
        <article className="rm-card rm-usage">
          <ul className="rm-usage-rates">
            <li>
              <div>
                <h3>{t("console.billing.priceIdentity")}</h3>
                <p className="rm-lead">{t("console.billing.priceIdentityWhen")}</p>
              </div>
              <p className="rm-usage-price">
                {formatMinor(locale, data?.identity_amount_minor ?? data?.unit_amount_minor ?? 80)}
              </p>
            </li>
            <li>
              <div>
                <h3>{t("console.billing.priceIdentityScreening")}</h3>
                <p className="rm-lead">{t("console.billing.priceIdentityScreeningWhen")}</p>
              </div>
              <p className="rm-usage-price">
                {formatMinor(locale, data?.identity_screening_amount_minor ?? 130)}
              </p>
            </li>
            <li>
              <div>
                <h3>{t("console.billing.priceAml")}</h3>
                <p className="rm-lead">{t("console.billing.priceAmlWhen")}</p>
              </div>
              <p className="rm-usage-price">{formatMinor(locale, data?.aml_amount_minor ?? 50)}</p>
            </li>
          </ul>
          <div className="rm-usage-meter">
            <UsageFigures
              title={t("console.nav.idv")}
              sandboxLabel={t("console.env.sandbox")}
              liveLabel={t("console.env.live")}
              debitLabel={t("console.billing.debit")}
              locale={locale}
              sandbox={identity?.sandbox_count ?? 0}
              live={identity?.live_count ?? 0}
              debit={identity?.live_debit_minor ?? 0}
            />
            <UsageFigures
              title={t("console.nav.aml")}
              sandboxLabel={t("console.env.sandbox")}
              liveLabel={t("console.env.live")}
              debitLabel={t("console.billing.debit")}
              locale={locale}
              sandbox={aml?.sandbox_count ?? 0}
              live={aml?.live_count ?? 0}
              debit={aml?.live_debit_minor ?? 0}
            />
          </div>
        </article>
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
          <LedgerTable initialEntries={entries} initialCursor={data?.ledger.next_cursor ?? null} />
        )}
      </section>
    </main>
  );
}

function UsageFigures({
  title,
  sandboxLabel,
  liveLabel,
  debitLabel,
  locale,
  sandbox,
  live,
  debit,
}: {
  title: string;
  sandboxLabel: string;
  liveLabel: string;
  debitLabel: string;
  locale: Locale;
  sandbox: number;
  live: number;
  debit: number;
}) {
  return (
    <div>
      <h3>{title}</h3>
      <dl>
        <div>
          <dt>{sandboxLabel}</dt>
          <dd>{formatCount(locale, sandbox)}</dd>
        </div>
        <div>
          <dt>{liveLabel}</dt>
          <dd>{formatCount(locale, live)}</dd>
        </div>
        <div>
          <dt>{debitLabel}</dt>
          <dd>{formatMinor(locale, debit)}</dd>
        </div>
      </dl>
    </div>
  );
}
