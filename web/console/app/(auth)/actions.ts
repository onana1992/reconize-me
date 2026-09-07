"use server";

import { cookies } from "next/headers";
import { redirect } from "next/navigation";
import { accountApi, accountApiWithCookies, type ApiResult, type IssuedApiKey } from "../../lib/api";
import { SESSION_COOKIE } from "../../lib/session";

async function setSession(setCookie: string | null | undefined) {
  if (!setCookie) {
    return;
  }
  const match = setCookie.match(/rm_session=([^;]+)/);
  if (!match) {
    return;
  }
  const jar = await cookies();
  jar.set(SESSION_COOKIE, match[1], {
    httpOnly: true,
    sameSite: "lax",
    path: "/",
    maxAge: 60 * 60 * 24 * 7,
  });
}

export async function signupAction(formData: FormData): Promise<ApiResult<{ user_id: string }>> {
  const invite = String(formData.get("invite_token") ?? "").trim();
  return accountApi("/v1/account/signup", {
    method: "POST",
    body: JSON.stringify({
      email: String(formData.get("email") ?? "").trim(),
      password: String(formData.get("password") ?? ""),
      organization_name: String(formData.get("organization_name") ?? "").trim(),
      first_name: String(formData.get("first_name") ?? "").trim(),
      last_name: String(formData.get("last_name") ?? "").trim(),
      ...(invite ? { invite_token: invite } : {}),
    }),
  });
}

export async function loginAction(formData: FormData): Promise<ApiResult<void>> {
  const result = await accountApiWithCookies<void>("/v1/account/login", {
    method: "POST",
    body: JSON.stringify({
      email: String(formData.get("email") ?? "").trim(),
      password: String(formData.get("password") ?? ""),
    }),
  });
  if (!result.ok) {
    return { ok: false, status: result.status, code: result.code, message: result.message };
  }
  await setSession(result.setCookie);
  return { ok: true, data: undefined };
}

export async function logoutAction(): Promise<void> {
  const jar = await cookies();
  const session = jar.get(SESSION_COOKIE)?.value;
  await accountApiWithCookies("/v1/account/logout", {
    method: "POST",
    headers: session ? { Cookie: `${SESSION_COOKIE}=${session}` } : {},
  });
  jar.delete(SESSION_COOKIE);
  redirect("/login");
}

export async function resendVerificationAction(formData: FormData): Promise<ApiResult<void>> {
  return accountApi("/v1/account/verify/resend", {
    method: "POST",
    body: JSON.stringify({ email: String(formData.get("email") ?? "").trim() }),
  });
}

export async function forgotAction(formData: FormData): Promise<ApiResult<void>> {
  return accountApi("/v1/account/password/forgot", {
    method: "POST",
    body: JSON.stringify({ email: String(formData.get("email") ?? "").trim() }),
  });
}

export async function resetAction(formData: FormData): Promise<ApiResult<void>> {
  return accountApi("/v1/account/password/reset", {
    method: "POST",
    body: JSON.stringify({
      token: String(formData.get("token") ?? ""),
      password: String(formData.get("password") ?? ""),
    }),
  });
}

export async function verifyEmail(token: string): Promise<ApiResult<IssuedApiKey>> {
  return accountApi(`/v1/account/verify?token=${encodeURIComponent(token)}`);
}
