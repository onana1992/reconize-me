"use client";

import { useRouter } from "next/navigation";
import { useState, type FormEvent } from "react";
import { StatusBadge } from "@kyc/brand";
import { useT } from "../../../../../i18n/client";
import {
  deleteWebhookAction,
  retryWebhookDeliveryAction,
  rotateWebhookAction,
  upsertWebhookAction,
  type WebhookDeliveryItem,
  type WebhookEndpoint,
} from "./webhook-actions";

export function WebhookSettingsForm({
  integrationId,
  initial,
  deliveries,
  canWrite,
}: {
  integrationId: string;
  initial: WebhookEndpoint | null;
  deliveries: WebhookDeliveryItem[];
  canWrite: boolean;
}) {
  const t = useT();
  const router = useRouter();
  const [url, setUrl] = useState(initial?.url ?? "");
  const [secret, setSecret] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [pending, setPending] = useState(false);

  async function onSave(event: FormEvent) {
    event.preventDefault();
    if (!canWrite) {
      return;
    }
    setPending(true);
    setError(null);
    const result = await upsertWebhookAction(integrationId, url);
    setPending(false);
    if (!result.ok) {
      setError(result.message);
      return;
    }
    if (result.data.secret) {
      setSecret(result.data.secret);
    }
    router.refresh();
  }

  async function onRotate() {
    if (!canWrite) {
      return;
    }
    setPending(true);
    const result = await rotateWebhookAction(integrationId);
    setPending(false);
    if (!result.ok) {
      setError(result.message);
      return;
    }
    setSecret(result.data.secret ?? null);
    router.refresh();
  }

  async function onDelete() {
    if (!canWrite) {
      return;
    }
    setPending(true);
    const result = await deleteWebhookAction(integrationId);
    setPending(false);
    if (!result.ok) {
      setError(result.message);
      return;
    }
    setSecret(null);
    setUrl("");
    router.refresh();
  }

  return (
    <div className="rm-int-webhook">
      <p className="rm-lead">{t("console.product.integrationsWebhooksBody")}</p>
      {canWrite ? (
        <form onSubmit={onSave} className="rm-form">
          <label>
            <span>{t("console.webhooks.url")}</span>
            <input
              name="url"
              type="url"
              required
              value={url}
              onChange={(e) => setUrl(e.target.value)}
              placeholder="https://example.com/hooks/idv"
            />
          </label>
          <button type="submit" disabled={pending}>
            {pending ? t("console.webhooks.saving") : t("console.webhooks.save")}
          </button>
        </form>
      ) : (
        <p className="rm-notice">{t("console.webhooks.readonly")}</p>
      )}

      {initial ? (
        <dl className="rm-int-dl">
          <div>
            <dt>{t("console.webhooks.prefix")}</dt>
            <dd>
              <code>{initial.secret_prefix}</code>
            </dd>
          </div>
          <div>
            <dt>{t("console.webhooks.status")}</dt>
            <dd>
              <StatusBadge label={initial.status} tone={initial.status === "active" ? "success" : "neutral"} />
            </dd>
          </div>
        </dl>
      ) : null}

      {secret ? (
        <p className="rm-notice" role="status">
          {t("console.webhooks.secretOnce")} <code>{secret}</code>
        </p>
      ) : null}

      {error ? (
        <p role="alert" className="rm-alert">
          {error}
        </p>
      ) : null}

      {canWrite && initial ? (
        <div className="rm-auth-links">
          <button type="button" data-variant="secondary" disabled={pending} onClick={onRotate}>
            {t("console.webhooks.rotate")}
          </button>{" "}
          <button type="button" data-variant="secondary" disabled={pending} onClick={onDelete}>
            {t("console.webhooks.delete")}
          </button>
        </div>
      ) : null}

      <h3>{t("console.webhooks.deliveries")}</h3>
      {deliveries.length === 0 ? (
        <p className="rm-lead">{t("console.webhooks.noDeliveries")}</p>
      ) : (
        <ul className="rm-int-dl">
          {deliveries.map((item) => (
            <li key={item.event_id}>
              <code>{item.event_id}</code> · {item.status} · attempt {item.attempt}
              {canWrite && item.status === "failed" ? (
                <>
                  {" "}
                  <button
                    type="button"
                    data-variant="secondary"
                    onClick={async () => {
                      await retryWebhookDeliveryAction(integrationId, item.event_id);
                      router.refresh();
                    }}
                  >
                    {t("console.webhooks.retry")}
                  </button>
                </>
              ) : null}
            </li>
          ))}
        </ul>
      )}
    </div>
  );
}
