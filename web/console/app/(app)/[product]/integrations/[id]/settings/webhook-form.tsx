"use client";

import { useRouter } from "next/navigation";
import { useState, type FormEvent } from "react";
import { StatusBadge, type BadgeTone } from "@/brand";
import { CopyButton } from "../../../../../../components/copy-button";
import { useLocale, useT } from "../../../../../../i18n/client";
import type { Translate } from "../../../../../../i18n";
import { formatUtc } from "../../../../../../lib/status";
import {
  deleteWebhookAction,
  retryWebhookDeliveryAction,
  rotateWebhookAction,
  upsertWebhookAction,
  type WebhookDeliveryItem,
  type WebhookEndpoint,
} from "./webhook-actions";
import { WebhookHandlerSnippet } from "./webhook-handler";

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
  const locale = useLocale();
  const router = useRouter();
  const [url, setUrl] = useState(initial?.url ?? "");
  const [secret, setSecret] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [pending, setPending] = useState(false);
  const endpointStatus = initial
    ? endpointLabel(initial.status, t)
    : { label: t("console.webhooks.notConfigured"), tone: "neutral" as const };

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
    <div className="rm-set-webhook">
      <header className="rm-set-head">
        <div>
          <h2>{t("console.product.integrationsWebhooks")}</h2>
          <p className="rm-lead">{t("console.product.integrationsWebhooksBody")}</p>
        </div>
        <StatusBadge label={endpointStatus.label} tone={endpointStatus.tone} />
      </header>

      {canWrite ? (
        <form onSubmit={onSave} className="rm-set-url">
          <label>
            <span>{t("console.webhooks.url")}</span>
            <input
              name="url"
              type="url"
              required
              value={url}
              onChange={(event) => setUrl(event.target.value)}
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

      {secret ? (
        <div className="rm-set-secret rm-set-reveal" role="status">
          <div>
            <strong>{t("console.webhooks.secretOnce")}</strong>
            <code>{secret}</code>
          </div>
          <CopyButton value={secret} />
        </div>
      ) : null}

      {error ? (
        <p role="alert" className="rm-alert">
          {error}
        </p>
      ) : null}

      {initial ? (
        <div className="rm-set-secret">
          <div>
            <strong>{t("console.webhooks.prefix")}</strong>
            <code>{initial.secret_prefix}</code>
          </div>
          <CopyButton value={initial.secret_prefix} />
        </div>
      ) : null}

      {canWrite && initial ? (
        <div className="rm-set-actions">
          <button type="button" data-variant="secondary" disabled={pending} onClick={onRotate}>
            {t("console.webhooks.rotate")}
          </button>
          <button type="button" data-variant="danger" disabled={pending} onClick={onDelete}>
            {t("console.webhooks.delete")}
          </button>
        </div>
      ) : null}

      <WebhookHandlerSnippet />

      <section className="rm-set-deliveries">
        <h3>{t("console.webhooks.deliveries")}</h3>
        {deliveries.length === 0 ? (
          <p className="rm-set-empty">{t("console.webhooks.noDeliveries")}</p>
        ) : (
          <ul className="rm-set-delivery-list">
            {deliveries.map((item) => {
              const delivery = deliveryLabel(item.status, t);
              return (
                <li key={item.event_id} className="rm-set-delivery">
                  <div>
                    <code title={item.event_id}>{item.event_id}</code>
                    <span className="rm-set-delivery-meta">
                      {formatUtc(item.created_at, locale)}
                      {item.http_status ? ` · HTTP ${item.http_status}` : ""}
                    </span>
                  </div>
                  <StatusBadge label={delivery.label} tone={delivery.tone} />
                  <span className="rm-set-attempt">
                    {t("console.webhooks.attempt").replace("{n}", String(item.attempt))}
                  </span>
                  {canWrite && item.status === "failed" ? (
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
                  ) : (
                    <span />
                  )}
                </li>
              );
            })}
          </ul>
        )}
      </section>
    </div>
  );
}

function endpointLabel(status: string, t: Translate): { label: string; tone: BadgeTone } {
  if (status === "active") {
    return { label: t("console.webhooks.statusActive"), tone: "success" };
  }
  if (status === "disabled") {
    return { label: t("console.webhooks.statusDisabled"), tone: "neutral" };
  }
  return { label: status, tone: "neutral" };
}

function deliveryLabel(status: string, t: Translate): { label: string; tone: BadgeTone } {
  if (status === "delivered") {
    return { label: t("console.webhooks.deliveryDelivered"), tone: "success" };
  }
  if (status === "failed") {
    return { label: t("console.webhooks.deliveryFailed"), tone: "danger" };
  }
  if (status === "pending") {
    return { label: t("console.webhooks.deliveryPending"), tone: "warning" };
  }
  return { label: status, tone: "neutral" };
}
