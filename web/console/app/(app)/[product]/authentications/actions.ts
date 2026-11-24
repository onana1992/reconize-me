"use server";

import { consoleApi, type AuthenticationList, type AuthenticationSession } from "../../../../lib/api";
import { sessionCookieHeader } from "../../../../lib/session";

export async function listAuthenticationsAction(
  status?: string,
  cursor?: string,
  limit = 100,
  integrationId?: string,
): Promise<Awaited<ReturnType<typeof consoleApi<AuthenticationList>>>> {
  const query = new URLSearchParams();
  if (status) {
    query.set("status", status);
  }
  if (cursor) {
    query.set("cursor", cursor);
  }
  if (integrationId) {
    query.set("integration_id", integrationId);
  }
  query.set("limit", String(limit));
  return consoleApi<AuthenticationList>(`/v1/console/authentications?${query}`, await sessionCookieHeader());
}

export async function reviewAuthenticationAction(id: string, decision: "approved" | "declined") {
  return consoleApi<AuthenticationSession>(
    `/v1/console/authentications/${encodeURIComponent(id)}/review`,
    await sessionCookieHeader(),
    { method: "POST", body: JSON.stringify({ decision }) },
  );
}

export async function createAuthenticationAction(formData: FormData) {
  const externalId = String(formData.get("external_id") ?? "").trim();
  const integrationId = String(formData.get("integration_id") ?? "").trim();
  return consoleApi<AuthenticationSession>("/v1/console/authentications", await sessionCookieHeader(), {
    method: "POST",
    headers: { "Idempotency-Key": crypto.randomUUID() },
    body: JSON.stringify({
      external_id: externalId || undefined,
      integration_id: integrationId || undefined,
    }),
  });
}
