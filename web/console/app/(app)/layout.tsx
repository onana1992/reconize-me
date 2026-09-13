import { AppShell } from "../../components/app-shell";
import { getMe } from "../../lib/session";

export default async function AppLayout({ children }: { children: React.ReactNode }) {
  const me = await getMe();
  const orgName = me.ok ? me.data.organization.name : "Console";
  const email = me.ok ? me.data.email : "";
  const firstName = me.ok ? (me.data.first_name ?? "") : "";
  const lastName = me.ok ? (me.data.last_name ?? "") : "";
  const role = me.ok ? me.data.role : "";
  const permissions = me.ok ? (me.data.permissions ?? []) : [];

  return (
    <AppShell
      orgName={orgName}
      email={email}
      firstName={firstName}
      lastName={lastName}
      role={role}
      permissions={permissions}
    >
      {children}
    </AppShell>
  );
}
