import Link from "next/link";
import { StatusBadge } from "@kyc/brand";
import { PageHeader } from "../../components/page-header";
import { getT } from "../../i18n";
import { requireMe } from "../../lib/session";

export default async function HomePage() {
  const me = await requireMe();
  const t = await getT();
  const roleKey =
    me.role === "owner"
      ? "console.role.owner"
      : me.role === "admin"
        ? "console.role.admin"
        : me.role === "readonly"
          ? "console.role.readonly"
          : me.role === "developer"
            ? "console.role.developer"
            : me.role === "member"
              ? "console.role.member"
              : null;
  const role = roleKey ? t(roleKey) : me.role;
  const planLabel = me.organization.plan === "sandbox" ? t("console.plan.sandbox") : me.organization.plan;
  const signedIn = t("console.home.signedIn").replace("{email}", me.email).replace("{role}", role);
  const permissions = me.permissions ?? [];
  const canReadKeys = permissions.includes("API_KEY_READ");
  const canReadVerifications = permissions.includes("VERIFICATION_READ");

  return (
    <main className="rm-dash">
      <PageHeader eyebrow={me.organization.slug} title={me.organization.name} lead={signedIn} />
      {me.role === "owner" ? <p className="rm-banner">{t("console.home.twoFactor")}</p> : null}
      <section className="rm-kpi-grid" aria-label={t("console.nav.overview")}>
        <article className="rm-card rm-kpi">
          <p className="rm-eyebrow">{t("console.home.planLabel")}</p>
          <p className="rm-kpi-value">
            <StatusBadge label={planLabel} tone="info" />
          </p>
        </article>
        <article className="rm-card rm-kpi">
          <p className="rm-eyebrow">{t("console.home.usageLabel")}</p>
          <p className="rm-kpi-value">{t("console.home.usageValue")}</p>
          <p className="rm-lead">{t("console.home.usageHint")}</p>
        </article>
        <article className="rm-card rm-kpi">
          <p className="rm-eyebrow">{t("console.home.roleLabel")}</p>
          <p className="rm-kpi-value">{role}</p>
        </article>
      </section>
      <section className="rm-section">
        <h2>{t("console.home.orgTitle")}</h2>
        <div className="rm-link-grid">
          {canReadKeys ? (
            <Link href="/settings/keys" className="rm-card rm-link-card">
              <h3>{t("console.nav.keys")}</h3>
              <p>{t("console.home.keysLead")}</p>
            </Link>
          ) : null}
          <Link href="/settings/team" className="rm-card rm-link-card">
            <h3>{t("console.nav.team")}</h3>
            <p>{t("console.home.teamLead")}</p>
          </Link>
        </div>
      </section>
      <section className="rm-section">
        <h2>{t("console.home.platformTitle")}</h2>
        <p className="rm-lead">{t("console.home.platformLead")}</p>
        <div className="rm-link-grid">
          {canReadVerifications ? (
            <div className="rm-card rm-link-card" data-soon="true" title={t("console.home.waitlist")}>
              <p className="rm-card-meta">
                <StatusBadge label={t("console.nav.soon")} tone="neutral" />
              </p>
              <h3>{t("console.home.idvTitle")}</h3>
              <p>{t("console.home.idvCardLead")}</p>
            </div>
          ) : null}
          <div className="rm-card rm-link-card" data-soon="true" title={t("console.home.waitlist")}>
            <p className="rm-card-meta">
              <StatusBadge label={t("console.nav.soon")} tone="neutral" />
            </p>
            <h3>{t("console.nav.biometric")}</h3>
            <p>{t("console.home.biometricLead")}</p>
          </div>
          <div className="rm-card rm-link-card" data-soon="true" title={t("console.home.waitlist")}>
            <p className="rm-card-meta">
              <StatusBadge label={t("console.nav.soon")} tone="neutral" />
            </p>
            <h3>{t("console.nav.aml")}</h3>
            <p>{t("console.home.amlLead")}</p>
          </div>
        </div>
      </section>
    </main>
  );
}
