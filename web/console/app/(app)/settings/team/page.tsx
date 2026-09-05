import { PageHeader } from "../../../../components/page-header";
import { SettingsNav } from "../../../../components/settings-nav";
import { consoleApi, type Team } from "../../../../lib/api";
import { roleLabel } from "../../../../lib/labels";
import { requireMe, sessionCookieHeader } from "../../../../lib/session";
import { formatUtc } from "../../../../lib/status";
import { InviteForm } from "./invite-form";

export const dynamic = "force-dynamic";

export default async function TeamPage() {
  const me = await requireMe();
  const team = await consoleApi<Team>("/v1/console/team", await sessionCookieHeader());

  return (
    <main>
      <PageHeader eyebrow="Paramètres" title="Équipe" lead="Membres et invitations en cours." />
      <SettingsNav />
      {!team.ok ? (
        <p role="alert" className="rm-alert">
          {team.message}
        </p>
      ) : (
        <>
          <section className="rm-section">
            <h2>Membres</h2>
            <div className="rm-table-wrap">
              <table className="rm-table">
                <thead>
                  <tr>
                    <th>E-mail</th>
                    <th>Rôle</th>
                  </tr>
                </thead>
                <tbody>
                  {team.data.members.map((member) => (
                    <tr key={member.id}>
                      <td>{member.email}</td>
                      <td>{roleLabel(member.role)}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          </section>
          <section className="rm-section">
            <h2>Invitations</h2>
            {team.data.invites.length === 0 ? (
              <div className="rm-empty">
                <p>Aucune invitation en cours.</p>
              </div>
            ) : (
              <div className="rm-table-wrap">
                <table className="rm-table">
                  <thead>
                    <tr>
                      <th>E-mail</th>
                      <th>Expire</th>
                    </tr>
                  </thead>
                  <tbody>
                    {team.data.invites.map((invite) => (
                      <tr key={invite.id}>
                        <td>{invite.email}</td>
                        <td>{formatUtc(invite.expires_at)}</td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            )}
          </section>
          {me.role === "owner" ? (
            <section className="rm-section">
              <h2>Inviter</h2>
              <InviteForm />
            </section>
          ) : null}
        </>
      )}
    </main>
  );
}
