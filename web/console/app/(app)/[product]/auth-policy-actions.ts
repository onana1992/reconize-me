"use server";

import { consoleApi, type ApiResult, type AuthPolicy } from "../../../lib/api";
import { sessionCookieHeader } from "../../../lib/session";

export async function publishAuthPolicyAction(formData: FormData): Promise<ApiResult<AuthPolicy>> {
  return consoleApi("/v1/console/authentication/policy", await sessionCookieHeader(), {
    method: "POST",
    body: JSON.stringify({
      integration_id: String(formData.get("integration_id") ?? ""),
      approval_threshold: formData.get("approval_threshold"),
      review_threshold: formData.get("review_threshold"),
      on_borderline: String(formData.get("on_borderline") ?? "review"),
      max_capture_attempts: Number(formData.get("max_capture_attempts") ?? 3),
    }),
  });
}
