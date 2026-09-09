import { cookies } from "next/headers";
import { redirect } from "next/navigation";
import { getMeWithCookie, type ApiResult, type Me } from "./api";

export const SESSION_COOKIE = "rm_session";

const AUTH_PATHS = ["/login", "/signup", "/forgot", "/reset", "/verify"];

export async function sessionCookieHeader(): Promise<string> {
  const jar = await cookies();
  const value = jar.get(SESSION_COOKIE)?.value;
  return value ? `${SESSION_COOKIE}=${value}` : "";
}

export function safeNextPath(next: string | undefined): string {
  if (!next || !next.startsWith("/") || next.startsWith("//")) {
    return "/";
  }
  const path = next.split("?")[0] ?? next;
  if (AUTH_PATHS.some((prefix) => path === prefix || path.startsWith(`${prefix}/`))) {
    return "/";
  }
  return next;
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

export async function redirectHomeIfSignedIn(next = "/"): Promise<void> {
  const header = await sessionCookieHeader();
  if (!header) {
    return;
  }
  const me = await getMeWithCookie(header);
  if (me.ok) {
    redirect(safeNextPath(next));
  }
}
