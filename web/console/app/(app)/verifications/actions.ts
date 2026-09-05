"use server";

import {
  consoleApi,
  createVerification,
  getVerification,
  type Applicant,
  type ApiResult,
  type IssuedApiKey,
  type Verification,
} from "../../../lib/api";
import { sessionCookieHeader } from "../../../lib/session";

function blankToUndefined(value: FormDataEntryValue | null): string | undefined {
  if (typeof value !== "string") {
    return undefined;
  }
  const trimmed = value.trim();
  return trimmed.length === 0 ? undefined : trimmed;
}

export async function createVerificationAction(formData: FormData): Promise<ApiResult<Verification>> {
  const externalId = blankToUndefined(formData.get("external_id"));
  const applicant: Applicant = {
    first_name: blankToUndefined(formData.get("first_name")),
    last_name: blankToUndefined(formData.get("last_name")),
    email: blankToUndefined(formData.get("email")),
  };
  const hasApplicant = Boolean(applicant.first_name || applicant.last_name || applicant.email);
  return createVerification(await sessionCookieHeader(), {
    ...(externalId ? { external_id: externalId } : {}),
    ...(hasApplicant ? { applicant } : {}),
  });
}

export async function getVerificationAction(id: string): Promise<ApiResult<Verification>> {
  return getVerification(await sessionCookieHeader(), id);
}

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
