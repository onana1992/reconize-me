import { PageHeader } from "../../../../components/page-header";
import { SettingsNav } from "../../../../components/settings-nav";
import { consoleApi, type ApiKeyItem } from "../../../../lib/api";
import { requireMe, sessionCookieHeader } from "../../../../lib/session";
import { KeysManager } from "./keys-manager";

export const dynamic = "force-dynamic";

export default async function KeysPage() {
  const me = await requireMe();
  const keys = await consoleApi<ApiKeyItem[]>("/v1/console/api-keys", await sessionCookieHeader());

  return (
    <main>
      <PageHeader
        eyebrow="Paramètres"
        title="Clés API"
        lead="Le secret n’est affiché qu’à l’émission."
      />
      <SettingsNav />
      {!keys.ok ? (
        <p role="alert" className="rm-alert">
          {keys.message}
        </p>
      ) : (
        <KeysManager keys={keys.data} isOwner={me.role === "owner"} />
      )}
    </main>
  );
}
