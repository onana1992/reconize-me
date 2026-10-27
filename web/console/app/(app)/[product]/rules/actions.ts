"use server";

import { consoleApi, type IdvPolicy } from "../../../../lib/api";
import { sessionCookieHeader } from "../../../../lib/session";

export async function publishPolicyAction(body: {
  profile: IdvPolicy["profile"];
  document_codes: string[];
  flow: {
    link_ttl_seconds: number;
    max_capture_attempts: number;
    max_liveness_attempts: number;
  };
  screening: IdvPolicy["screening"];
}) {
  return consoleApi<IdvPolicy>("/v1/console/identity/policy", await sessionCookieHeader(), {
    method: "POST",
    body: JSON.stringify(body),
  });
}
