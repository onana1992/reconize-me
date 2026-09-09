import { PageHeader } from "../../../../components/page-header";
import { consoleApi, type ApiKeyItem } from "../../../../lib/api";
import { requireMe, sessionCookieHeader } from "../../../../lib/session";
import { KeysManager } from "./keys-manager";

export const dynamic = "force-dynamic";

export default async function KeysPage() {
  const me = await requireMe();
  const permissions = me.permissions ?? [];
  const canRead = permissions.includes("API_KEY_READ");
  const canWrite = permissions.includes("API_KEY_WRITE");
  const keys = canRead
    ? await consoleApi<ApiKeyItem[]>("/v1/console/api-keys", await sessionCookieHeader())
    : null;

  return (
    <main>
      <PageHeader
        eyebrow="Paramètres"
        title="Clés API"
        lead="Le secret n’est affiché qu’à l’émission."
      />
      {!canRead ? (
        <p role="alert" className="rm-alert">
          Votre rôle ne permet pas de voir les clés API.
        </p>
      ) : !keys?.ok ? (
        <p role="alert" className="rm-alert">
          {keys?.message ?? "Impossible de charger les clés."}
        </p>
      ) : (
        <KeysManager keys={keys.data} canWrite={canWrite} />
      )}
    </main>
  );
}
