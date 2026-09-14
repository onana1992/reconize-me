"use server";

import {
  consoleApi,
  type ApiResult,
  type AuditList,
  type IntegrationResponse,
} from "../../../lib/api";
import { sessionCookieHeader } from "../../../lib/session";

export async function createIntegrationAction(formData: FormData): Promise<ApiResult<IntegrationResponse>> {
  const name = String(formData.get("name") ?? "").trim();
  const mode = String(formData.get("mode") ?? "test").trim() || "test";
  return consoleApi("/v1/console/integrations", await sessionCookieHeader(), {
    method: "POST",
    body: JSON.stringify({
      name,
      mode,
    }),
  });
}

export async function revokeApiKeyAction(id: string): Promise<ApiResult<void>> {
  return consoleApi(`/v1/console/api-keys/${encodeURIComponent(id)}/revoke`, await sessionCookieHeader(), {
    method: "POST",
  });
}

export async function inviteMemberAction(formData: FormData): Promise<ApiResult<void>> {
  return consoleApi("/v1/console/team/invites", await sessionCookieHeader(), {
    method: "POST",
    body: JSON.stringify({
      email: String(formData.get("email") ?? "").trim(),
      role: String(formData.get("role") ?? "member").trim() || "member",
    }),
  });
}

export async function resendInviteAction(id: string): Promise<ApiResult<void>> {
  return consoleApi(`/v1/console/team/invites/${encodeURIComponent(id)}/resend`, await sessionCookieHeader(), {
    method: "POST",
  });
}

export async function cancelInviteAction(id: string): Promise<ApiResult<void>> {
  return consoleApi(`/v1/console/team/invites/${encodeURIComponent(id)}`, await sessionCookieHeader(), {
    method: "DELETE",
  });
}

export async function removeMemberAction(userId: string): Promise<ApiResult<void>> {
  return consoleApi(`/v1/console/team/members/${encodeURIComponent(userId)}`, await sessionCookieHeader(), {
    method: "DELETE",
  });
}

export async function changeMemberRoleAction(userId: string, role: string): Promise<ApiResult<void>> {
  return consoleApi(`/v1/console/team/members/${encodeURIComponent(userId)}`, await sessionCookieHeader(), {
    method: "PATCH",
    body: JSON.stringify({ role }),
  });
}

export async function transferOwnershipAction(userId: string): Promise<ApiResult<void>> {
  return consoleApi("/v1/console/team/transfer", await sessionCookieHeader(), {
    method: "POST",
    body: JSON.stringify({ user_id: userId }),
  });
}

export async function disableMemberAction(userId: string): Promise<ApiResult<void>> {
  return consoleApi(`/v1/console/team/members/${encodeURIComponent(userId)}/disable`, await sessionCookieHeader(), {
    method: "POST",
  });
}

export async function enableMemberAction(userId: string): Promise<ApiResult<void>> {
  return consoleApi(`/v1/console/team/members/${encodeURIComponent(userId)}/enable`, await sessionCookieHeader(), {
    method: "POST",
  });
}

export async function listAuditAction(
  action?: string,
  cursor?: string,
  limit = 100,
): Promise<ApiResult<AuditList>> {
  const query = new URLSearchParams();
  if (action) {
    query.set("action", action);
  }
  if (cursor) {
    query.set("cursor", cursor);
  }
  query.set("limit", String(limit));
  return consoleApi(`/v1/console/audit?${query.toString()}`, await sessionCookieHeader());
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
