import { AppHeader } from "../../components/app-header";
import { getMe } from "../../lib/session";

export default async function AppLayout({ children }: { children: React.ReactNode }) {
  const me = await getMe();
  const orgName = me.ok ? me.data.organization.name : "Console";

  return (
    <>
      <AppHeader orgName={orgName} />
      <div className="rm-page">{children}</div>
    </>
  );
}
