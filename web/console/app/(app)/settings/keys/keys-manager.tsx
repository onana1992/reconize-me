"use client";

import Link from "next/link";
import { useRouter } from "next/navigation";
import { useMemo, useState } from "react";
import { StatusBadge } from "@kyc/brand";
import { SecretBlock } from "../../../../components/secret-block";
import { useT } from "../../../../i18n/client";
import {
  environmentFromPrefix,
  environmentTone,
  tEnvironment,
  type ApiEnvironment,
} from "../../../../lib/environment";
import type { ApiKeyItem, IssuedApiKey } from "../../../../lib/api";
import { formatUtc } from "../../../../lib/status";
import { createApiKeyAction, revokeApiKeyAction } from "../actions";

export function KeysManager({
  keys,
  canWrite,
  environment,
  liveEnabled = false,
}: {
  keys: ApiKeyItem[];
  canWrite: boolean;
  environment: ApiEnvironment;
  liveEnabled?: boolean;
}) {
  const t = useT();
  const router = useRouter();
  const [issued, setIssued] = useState<IssuedApiKey | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [pending, setPending] = useState(false);

  const visible = useMemo(
    () =>
      keys
        .filter((key) => environmentFromPrefix(key.key_prefix) === environment)
        .sort((a, b) => {
          if (a.revoked !== b.revoked) {
            return a.revoked ? 1 : -1;
          }
          return a.created_at < b.created_at ? 1 : -1;
        }),
    [keys, environment],
  );

  async function createSandboxKey() {
    setPending(true);
    setError(null);
    const result = await createApiKeyAction();
    setPending(false);
    if (!result.ok) {
      setError(result.code === "forbidden" ? t("console.keys.issueForbidden") : result.message);
      return;
    }
    setIssued(result.data);
    router.refresh();
  }

  async function revoke(id: string) {
    setError(null);
    const result = await revokeApiKeyAction(id);
    if (!result.ok) {
      setError(result.code === "forbidden" ? t("console.keys.revokeForbidden") : result.message);
      return;
    }
    router.refresh();
  }

  const issuedEnv = environmentFromPrefix(issued?.key ?? issued?.key_prefix);
  const showIssued = issued?.key && issuedEnv === environment;

  return (
    <>
      {showIssued && issued?.key ? (
        <SecretBlock
          title={t("console.keys.issuedTitle").replace("{env}", tEnvironment(t, issuedEnv))}
          description={t("console.keys.issuedBody")}
          value={issued.key}
          copyLabel={t("common.copyKey")}
        />
      ) : null}
      {canWrite && environment === "sandbox" ? (
        <p className="rm-actions">
          <button type="button" onClick={createSandboxKey} disabled={pending}>
            {pending ? t("console.keys.issuing") : t("console.keys.issueSandbox")}
          </button>
        </p>
      ) : null}
      {canWrite && environment === "live" ? (
        <>
          <p className="rm-actions">
            <button
              type="button"
              disabled={!liveEnabled}
              title={liveEnabled ? undefined : t("console.keys.liveLocked")}
              aria-describedby={liveEnabled ? undefined : "rm-live-key-hint"}
            >
              {t("console.keys.issueLive")}
            </button>
          </p>
          {!liveEnabled ? (
            <p id="rm-live-key-hint" className="rm-lead">
              {t("console.keys.liveLocked")}{" "}
              <Link href="/settings/billing">{t("console.nav.billing")}</Link>
            </p>
          ) : null}
        </>
      ) : null}
      {error ? (
        <p role="alert" className="rm-alert">
          {error}
        </p>
      ) : null}
      {visible.length === 0 ? (
        <div className="rm-empty">
          <p>{environment === "live" ? t("console.keys.emptyLive") : t("console.keys.emptySandbox")}</p>
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
    </>
  );
}
