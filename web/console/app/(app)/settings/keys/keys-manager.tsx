"use client";

import Link from "next/link";
import { useRouter } from "next/navigation";
import { useMemo, useState } from "react";
import { StatusBadge } from "@kyc/brand";
import { useT } from "../../../../i18n/client";
import { environmentFromPrefix, environmentTone, tEnvironment } from "../../../../lib/environment";
import type { ApiKeyItem } from "../../../../lib/api";
import { formatUtc } from "../../../../lib/status";
import { revokeApiKeyAction } from "../actions";

export function KeysManager({
  keys,
  canWrite,
  mode,
}: {
  keys: ApiKeyItem[];
  canWrite: boolean;
  mode: string;
}) {
  const t = useT();
  const router = useRouter();
  const [error, setError] = useState<string | null>(null);
  const live = mode === "live";

  const visible = useMemo(
    () =>
      [...keys].sort((a, b) => {
        if (a.revoked !== b.revoked) {
          return a.revoked ? 1 : -1;
        }
        return a.created_at < b.created_at ? 1 : -1;
      }),
    [keys],
  );

  async function revoke(id: string) {
    setError(null);
    const result = await revokeApiKeyAction(id);
    if (!result.ok) {
      setError(result.code === "forbidden" ? t("console.keys.revokeForbidden") : result.message);
      return;
    }
    router.refresh();
  }

  return (
    <>
      {error ? (
        <p role="alert" className="rm-alert">
          {error}
        </p>
      ) : null}
      {visible.length === 0 ? (
        <div className="rm-empty">
          <p>{live ? t("console.keys.emptyLive") : t("console.keys.emptySandbox")}</p>
        </div>
      ) : (
        <div className="rm-table-wrap">
          <table className="rm-table">
            <thead>
              <tr>
                <th>{t("console.env.label")}</th>
                <th>{t("console.keys.prefix")}</th>
                <th>{t("console.keys.created")}</th>
                <th>{t("console.keys.status")}</th>
                {canWrite ? <th></th> : null}
              </tr>
            </thead>
            <tbody>
              {visible.map((key) => {
                const env = environmentFromPrefix(key.key_prefix);
                return (
                  <tr key={key.id}>
                    <td>
                      <StatusBadge label={tEnvironment(t, env)} tone={environmentTone(env)} />
                    </td>
                    <td>
                      <code>{key.key_prefix}</code>
                    </td>
                    <td>{formatUtc(key.created_at)}</td>
                    <td>
                      <StatusBadge
                        label={key.revoked ? t("console.keys.revoked") : t("console.keys.active")}
                        tone={key.revoked ? "neutral" : "success"}
                      />
                    </td>
                    {canWrite ? (
                      <td>
                        {key.revoked ? null : (
                          <button type="button" data-variant="danger" onClick={() => revoke(key.id)}>
                            {t("console.keys.revoke")}
                          </button>
                        )}
                      </td>
                    ) : null}
                  </tr>
                );
              })}
            </tbody>
          </table>
        </div>
      )}
      {live ? (
        <p className="rm-lead">
          {t("console.keys.liveLocked")} <Link href="/settings/billing">{t("console.nav.billing")}</Link>
        </p>
      ) : null}
    </>
  );
}
