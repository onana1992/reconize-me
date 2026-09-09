import { PageHeader } from "../../../../components/page-header";
import { getT } from "../../../../i18n";
import { consoleApi, type Team } from "../../../../lib/api";
import { requireMe, sessionCookieHeader } from "../../../../lib/session";
import { TeamTables } from "./team-tables";

export const dynamic = "force-dynamic";

export default async function TeamPage() {
  const t = await getT();
  const me = await requireMe();
  const permissions = me.permissions ?? [];
  const canWrite = permissions.includes("TEAM_WRITE");
  const canOwn = permissions.includes("OWNERSHIP");
  const team = await consoleApi<Team>("/v1/console/team", await sessionCookieHeader());

  return (
    <main>
      <PageHeader eyebrow={t("console.settings.label")} title={t("console.nav.team")} lead={t("console.team.lead")} />
      {!team.ok ? (
        <p role="alert" className="rm-alert">
          {t("console.team.loadError")}
        </p>
      ) : (
        <TeamTables
          members={team.data.members}
          invites={team.data.invites}
          currentEmail={me.email}
          canWrite={canWrite}
          canOwn={canOwn}
        />
      )}
    </main>
  );
}
