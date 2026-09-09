import Link from "next/link";
import { StatusBadge } from "@kyc/brand";
import { PageHeader } from "../../../../components/page-header";
import { getT } from "../../../../i18n";
import { displayName, initials, tRole } from "../../../../lib/labels";
import { requireMe } from "../../../../lib/session";
import { SecurityList } from "./security-list";

export default async function AccountPage() {
  const me = await requireMe();
  const t = await getT();
  const name = displayName(me.first_name, me.last_name, me.email);
  const avatar = initials(me.first_name, me.last_name, me.email);
  const role = tRole(t, me.role);
  const plan = me.organization.plan === "sandbox" ? t("console.plan.sandbox") : me.organization.plan;
  const firstName = me.first_name?.trim() || t("console.account.empty");
  const lastName = me.last_name?.trim() || t("console.account.empty");
  const permissions = me.permissions ?? [];
  const canReadKeys = permissions.includes("API_KEY_READ");
  const canReadAudit = permissions.includes("AUDIT_READ");

  return (
    <main className="rm-dash">
      <PageHeader
        eyebrow={t("console.settings.label")}
        title={t("console.nav.account")}
        lead={t("console.account.lead")}
      />
      <article className="rm-account-hero">
        <span className="rm-shell-initial rm-account-avatar" aria-hidden="true">
          {avatar}
        </span>
        <div className="rm-account-hero-body">
          <p className="rm-account-name">{name}</p>
          <p className="rm-lead">{me.email}</p>
          <p className="rm-account-hero-tags">
            <StatusBadge label={role} tone="info" />
            <StatusBadge label={plan} tone="neutral" />
          </p>
        </div>
      </article>
      <section className="rm-section">
        <h2>{t("console.account.profile")}</h2>
        <div className="rm-details">
          <div>
            <span>{t("console.account.firstName")}</span>
            <strong>{firstName}</strong>
          </div>
          <div>
            <span>{t("console.account.lastName")}</span>
            <strong>{lastName}</strong>
          </div>
          <div>
            <span>{t("console.account.email")}</span>
            <strong>{me.email}</strong>
          </div>
          <div>
            <span>{t("console.account.role")}</span>
            <strong>{role}</strong>
          </div>
        </div>
      </section>
      <section className="rm-section">
        <h2>{t("console.account.security")}</h2>
        <SecurityList />
      </section>
      <section className="rm-section">
        <h2>{t("console.account.organization")}</h2>
        <div className="rm-details">
          <div>
            <span>{t("console.account.orgName")}</span>
            <strong>{me.organization.name}</strong>
          </div>
          <div>
            <span>{t("console.account.slug")}</span>
            <strong>{me.organization.slug}</strong>
          </div>
          <div>
            <span>{t("console.home.planLabel")}</span>
            <strong>{plan}</strong>
          </div>
        </div>
        <div className="rm-link-grid">
          <Link href="/settings/team" className="rm-card rm-link-card">
            <h3>{t("console.nav.team")}</h3>
            <p>{t("console.home.teamLead")}</p>
          </Link>
          {canReadKeys ? (
            <Link href="/settings/keys" className="rm-card rm-link-card">
              <h3>{t("console.nav.keys")}</h3>
              <p>{t("console.home.keysLead")}</p>
            </Link>
          ) : null}
          {canReadAudit ? (
            <Link href="/settings/activity" className="rm-card rm-link-card">
              <h3>{t("console.nav.activity")}</h3>
              <p>{t("console.account.activityLead")}</p>
            </Link>
          ) : null}
        </div>
      </section>
    </main>
  );
}
