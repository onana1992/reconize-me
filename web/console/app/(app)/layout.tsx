import { AppShell } from "../../components/app-shell";
import { getMe } from "../../lib/session";

export default async function AppLayout({ children }: { children: React.ReactNode }) {
  const me = await getMe();
  const orgName = me.ok ? me.data.organization.name : "Console";
  const orgSlug = me.ok ? me.data.organization.slug : "";
  const email = me.ok ? me.data.email : "";
  const role = me.ok ? me.data.role : "";
  const plan = me.ok ? me.data.organization.plan : "";

  return (
    <AppShell orgName={orgName} orgSlug={orgSlug} email={email} role={role} plan={plan}>
      {children}
    </AppShell>
  );
}
