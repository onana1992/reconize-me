"use server";

import { consoleApi, type ApiResult } from "../../../../../../lib/api";
import { sessionCookieHeader } from "../../../../../../lib/session";

export type WebhookEndpoint = {
  url: string;
  secret_prefix: string;
  secret?: string | null;
  status: string;
};

export type WebhookDeliveryItem = {
  event_id: string;
  verification_id: string;
  status: string;
  attempt: number;
  http_status?: number | null;
  created_at: string;
};

export async function upsertWebhookAction(
  integrationId: string,
  url: string,
): Promise<ApiResult<WebhookEndpoint>> {
  return consoleApi(`/v1/console/integrations/${encodeURIComponent(integrationId)}/webhook`, await sessionCookieHeader(), {
    method: "PUT",
    body: JSON.stringify({ url }),
  });
}

export async function rotateWebhookAction(integrationId: string): Promise<ApiResult<WebhookEndpoint>> {
  return consoleApi(
    `/v1/console/integrations/${encodeURIComponent(integrationId)}/webhook/rotate`,
    await sessionCookieHeader(),
    { method: "POST", body: "{}" },
  );
}

export async function deleteWebhookAction(integrationId: string): Promise<ApiResult<void>> {
  return consoleApi(
    `/v1/console/integrations/${encodeURIComponent(integrationId)}/webhook`,
    await sessionCookieHeader(),
    { method: "DELETE" },
  );
}

export async function retryWebhookDeliveryAction(
  integrationId: string,
  eventId: string,
): Promise<ApiResult<void>> {
  return consoleApi(
    `/v1/console/integrations/${encodeURIComponent(integrationId)}/webhook/deliveries/${encodeURIComponent(eventId)}/retry`,
    await sessionCookieHeader(),
    { method: "POST", body: "{}" },
  );
}

export async function loadWebhook(
  integrationId: string,
): Promise<{ endpoint: WebhookEndpoint | null; deliveries: WebhookDeliveryItem[] }> {
  const cookie = await sessionCookieHeader();
  const endpoint = await consoleApi<WebhookEndpoint>(
    `/v1/console/integrations/${encodeURIComponent(integrationId)}/webhook`,
    cookie,
  );
  const deliveries = await consoleApi<{ deliveries: WebhookDeliveryItem[] }>(
    `/v1/console/integrations/${encodeURIComponent(integrationId)}/webhook/deliveries?limit=20`,
    cookie,
  );
  return {
    endpoint: endpoint.ok ? endpoint.data : null,
    deliveries: deliveries.ok ? deliveries.data.deliveries : [],
  };
}
