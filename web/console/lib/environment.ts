import type { BadgeTone } from "@kyc/brand";
import type { Translate } from "../i18n";

export const ENVIRONMENTS = ["sandbox", "live"] as const;

export type ApiEnvironment = (typeof ENVIRONMENTS)[number];

export const DEFAULT_ENVIRONMENT: ApiEnvironment = "sandbox";

export const ENV_QUERY = "env";

export const ENV_COOKIE = "rm_console_env";

export function parseEnvironment(value: string | null | undefined): ApiEnvironment | undefined {
  if (value === "sandbox" || value === "live") {
    return value;
  }
  return undefined;
}

export function resolveEnvironment(value?: string | null): ApiEnvironment {
  return parseEnvironment(value) ?? DEFAULT_ENVIRONMENT;
}

export function environmentFromPrefix(prefix: string | null | undefined): ApiEnvironment {
  return prefix?.startsWith("ky_live_") ? "live" : "sandbox";
}

export function environmentTone(env: ApiEnvironment): BadgeTone {
  return env === "live" ? "warning" : "info";
}

export function tEnvironment(t: Translate, env: ApiEnvironment): string {
  return env === "live" ? t("console.env.live") : t("console.env.sandbox");
}

export function withEnvironment(href: string, env: ApiEnvironment): string {
  const [pathAndQuery, hash] = href.split("#");
  const [path, query] = pathAndQuery.split("?");
  const params = new URLSearchParams(query);
  params.set(ENV_QUERY, env);
  const search = params.toString();
  return `${path}?${search}${hash ? `#${hash}` : ""}`;
}

export function readEnvironmentCookie(): ApiEnvironment | undefined {
  if (typeof document === "undefined") {
    return undefined;
  }
  const match = document.cookie.match(new RegExp(`(?:^|; )${ENV_COOKIE}=([^;]*)`));
  return parseEnvironment(match?.[1]);
}

export function writeEnvironmentCookie(env: ApiEnvironment): void {
  document.cookie = `${ENV_COOKIE}=${env}; Path=/; SameSite=Lax; Max-Age=31536000`;
}
