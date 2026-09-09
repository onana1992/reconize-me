"use client";

import { useRouter } from "next/navigation";
import { useState } from "react";
import { StatusBadge } from "@kyc/brand";
import { SecretBlock } from "../../../../components/secret-block";
import { createApiKeyAction, revokeApiKeyAction } from "../actions";
import type { ApiKeyItem, IssuedApiKey } from "../../../../lib/api";
import { formatUtc } from "../../../../lib/status";

export function KeysManager({ keys, canWrite }: { keys: ApiKeyItem[]; canWrite: boolean }) {
  const router = useRouter();
  const [issued, setIssued] = useState<IssuedApiKey | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [pending, setPending] = useState(false);

  async function createKey() {
    setPending(true);
    setError(null);
    const result = await createApiKeyAction();
    setPending(false);
    if (!result.ok) {
      setError(result.code === "forbidden" ? "Vous n’avez pas le droit d’émettre une clé." : result.message);
      return;
    }
    setIssued(result.data);
    router.refresh();
  }

  async function revoke(id: string) {
    setError(null);
    const result = await revokeApiKeyAction(id);
    if (!result.ok) {
      setError(result.code === "forbidden" ? "Vous n’avez pas le droit de révoquer une clé." : result.message);
      return;
    }
    router.refresh();
  }

  return (
    <>
      {issued?.key ? (
        <SecretBlock
          title="Nouvelle clé"
          description="Copiez-la maintenant. Elle ne sera plus montrée."
          value={issued.key}
        />
      ) : null}
      {canWrite ? (
        <p>
          <button type="button" onClick={createKey} disabled={pending}>
            {pending ? "Émission…" : "Émettre une clé ky_test_"}
          </button>
        </p>
      ) : null}
      {error ? (
        <p role="alert" className="rm-alert">
          {error}
        </p>
      ) : null}
      {keys.length === 0 ? (
        <div className="rm-empty">
          <p>Aucune clé API pour cette organisation.</p>
        </div>
      ) : (
        <div className="rm-table-wrap">
          <table className="rm-table">
            <thead>
              <tr>
                <th>Préfixe</th>
                <th>Créée</th>
                <th>État</th>
                {canWrite ? <th></th> : null}
              </tr>
            </thead>
            <tbody>
              {keys.map((key) => (
                <tr key={key.id}>
                  <td>
                    <code>{key.key_prefix}</code>
                  </td>
                  <td>{formatUtc(key.created_at)}</td>
                  <td>
                    <StatusBadge label={key.revoked ? "Révoquée" : "Active"} tone={key.revoked ? "neutral" : "success"} />
                  </td>
                  {canWrite ? (
                    <td>
                      {key.revoked ? null : (
                        <button type="button" data-variant="danger" onClick={() => revoke(key.id)}>
                          Révoquer
                        </button>
                      )}
                    </td>
                  ) : null}
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </>
  );
}
