"use server";

import { consoleApi, type ApiResult, type IssuedApiKey } from "../../../lib/api";
import { sessionCookieHeader } from "../../../lib/session";

export async function createApiKeyAction(): Promise<ApiResult<IssuedApiKey>> {
  return consoleApi("/v1/console/api-keys", await sessionCookieHeader(), { method: "POST" });
}

export async function revokeApiKeyAction(id: string): Promise<ApiResult<void>> {
  return consoleApi(`/v1/console/api-keys/${encodeURIComponent(id)}/revoke`, await sessionCookieHeader(), {
    method: "POST",
  });
}

export async function inviteMemberAction(formData: FormData): Promise<ApiResult<void>> {
  return consoleApi("/v1/console/team/invites", await sessionCookieHeader(), {
    method: "POST",
    body: JSON.stringify({ email: String(formData.get("email") ?? "").trim() }),
  });
}

export async function changePasswordAction(formData: FormData): Promise<ApiResult<void>> {
  return consoleApi("/v1/console/account/password", await sessionCookieHeader(), {
    method: "POST",
    body: JSON.stringify({
      current_password: String(formData.get("current_password") ?? ""),
      new_password: String(formData.get("new_password") ?? ""),
    }),
  });
}
