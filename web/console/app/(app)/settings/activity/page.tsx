import { PageHeader } from "../../../../components/page-header";
import { getT } from "../../../../i18n";
import { consoleApi, type AuditList, type Team } from "../../../../lib/api";
import { requireMe, sessionCookieHeader } from "../../../../lib/session";
import { ActivityTable } from "./activity-table";

export const dynamic = "force-dynamic";

export default async function ActivityPage() {
  const t = await getT();
  const me = await requireMe();
  const permissions = me.permissions ?? [];
  const canRead = permissions.includes("AUDIT_READ");
  const cookie = await sessionCookieHeader();
  const [audit, team] = canRead
    ? await Promise.all([
        consoleApi<AuditList>("/v1/console/audit?limit=100", cookie),
        consoleApi<Team>("/v1/console/team", cookie),
      ])
    : [null, null];
  const emails: Record<string, string> = {};
  if (team?.ok) {
    for (const member of team.data.members) {
      emails[member.id] = member.email;
    }
  }

  return (
    <main>
      <PageHeader eyebrow={t("console.settings.label")} title={t("console.nav.activity")} lead={t("console.activity.lead")} />
      {!canRead ? (
        <p role="alert" className="rm-alert">
          {t("console.activity.forbidden")}
        </p>
      ) : !audit?.ok ? (
        <p role="alert" className="rm-alert">
          {audit?.message ?? t("console.activity.loadError")}
        </p>
      ) : (
        <ActivityTable
          initialEvents={audit.data.events}
          initialCursor={audit.data.next_cursor}
          emails={emails}
        />
      )}
    </main>
  );
}
