"use server";

import { consoleApi, type MediaUrl, type Verification, type VerificationList } from "../../../../lib/api";
import { sessionCookieHeader } from "../../../../lib/session";

export async function listVerificationsAction(
  status?: string,
  cursor?: string,
  limit = 100,
  integrationId?: string,
): Promise<Awaited<ReturnType<typeof consoleApi<VerificationList>>>> {
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
  return consoleApi<VerificationList>(`/v1/console/verifications?${query}`, await sessionCookieHeader());
}

export async function getVerificationAction(id: string) {
  return consoleApi<Verification>(`/v1/console/verifications/${encodeURIComponent(id)}`, await sessionCookieHeader());
}

export async function createVerificationAction(formData: FormData) {
  const firstName = String(formData.get("first_name") ?? "").trim();
  const lastName = String(formData.get("last_name") ?? "").trim();
  const email = String(formData.get("email") ?? "").trim();
  const externalId = String(formData.get("external_id") ?? "").trim();
  const scenario = String(formData.get("sandbox_scenario") ?? "approved").trim() || "approved";
  const integrationId = String(formData.get("integration_id") ?? "").trim();
  const applicant =
    firstName || lastName || email
      ? {
          first_name: firstName || undefined,
          last_name: lastName || undefined,
          email: email || undefined,
        }
      : undefined;
  return consoleApi<Verification>("/v1/console/verifications", await sessionCookieHeader(), {
    method: "POST",
    body: JSON.stringify({
      external_id: externalId || undefined,
      integration_id: integrationId || undefined,
      applicant,
      metadata: { sandbox_scenario: scenario },
    }),
  });
}

export async function cancelVerificationAction(id: string) {
  return consoleApi<Verification>(
    `/v1/console/verifications/${encodeURIComponent(id)}/cancel`,
    await sessionCookieHeader(),
    { method: "POST" },
  );
}

export async function reviewVerificationAction(id: string, decision: "approved" | "declined") {
  return consoleApi<Verification>(
    `/v1/console/verifications/${encodeURIComponent(id)}/review`,
    await sessionCookieHeader(),
    { method: "POST", body: JSON.stringify({ decision }) },
  );
}

export async function verificationMediaAction(id: string, kind: string) {
  return consoleApi<MediaUrl>(
    `/v1/console/verifications/${encodeURIComponent(id)}/media/${encodeURIComponent(kind)}`,
    await sessionCookieHeader(),
  );
}
