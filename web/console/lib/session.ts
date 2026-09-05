import { cookies } from "next/headers";
import { redirect } from "next/navigation";
import { getMeWithCookie, type ApiResult, type Me } from "./api";

export const SESSION_COOKIE = "rm_session";

export async function sessionCookieHeader(): Promise<string> {
  const jar = await cookies();
  const value = jar.get(SESSION_COOKIE)?.value;
  return value ? `${SESSION_COOKIE}=${value}` : "";
}

export async function getMe(): Promise<ApiResult<Me>> {
  const result = await getMeWithCookie(await sessionCookieHeader());
  if (!result.ok && result.status === 401) {
    redirect("/login");
  }
  return result;
}

export async function requireMe(): Promise<Me> {
  const result = await getMe();
  if (!result.ok) {
    redirect("/login");
  }
  return result.data;
}
