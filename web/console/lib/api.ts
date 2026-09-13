const API_BASE = process.env.API_BASE_URL ?? "http://localhost:8080";

export type Me = {
  email: string;
  first_name?: string | null;
  last_name?: string | null;
  role: string;
  permissions: string[];
  organization: { id: string; name: string; slug: string };
};

export type ApiKeyItem = {
  id: string;
  key_prefix: string;
  created_at: string;
  revoked: boolean;
};

export type IssuedApiKey = {
  id: string | null;
  key: string | null;
  key_prefix: string | null;
};

export type Team = {
  members: { id: string; email: string; role: string; status: string; created_at: string }[];
  invites: { id: string; email: string; role: string; expires_at: string }[];
};

export type AuditEvent = {
  id: number;
  action: string;
  actor_type: string;
  actor_id: string | null;
  resource_type: string;
  resource_id: string;
  payload: Record<string, unknown>;
  ip_address: string | null;
  created_at: string;
};

export type AuditList = {
  events: AuditEvent[];
  next_cursor: string | null;
};

export type ApiResult<T> =
  | { ok: true; data: T }
  | { ok: false; status: number; code: string; message: string };

function errorBody(status: number, code: string, message: string): ApiResult<never> {
  return { ok: false, status, code, message };
}

async function parseError(response: Response): Promise<ApiResult<never>> {
  const body = (await response.json().catch(() => ({}))) as {
    error?: { code?: string; message?: string };
  };
  return {
    ok: false,
    status: response.status,
    code: body.error?.code ?? "error",
    message: body.error?.message ?? "Une erreur s’est produite",
  };
}

async function readBody<T>(response: Response): Promise<ApiResult<T>> {
  if (response.status === 204) {
    return { ok: true, data: undefined as T };
  }
  const text = await response.text();
  if (!text) {
    return { ok: true, data: undefined as T };
  }
  return { ok: true, data: JSON.parse(text) as T };
}

export async function accountApi<T>(path: string, init?: RequestInit): Promise<ApiResult<T>> {
  try {
    const response = await fetch(`${API_BASE}${path}`, {
      ...init,
      cache: "no-store",
      headers: {
        "Content-Type": "application/json",
        ...(init?.headers ?? {}),
      },
    });
    if (!response.ok) {
      return parseError(response);
    }
    return readBody<T>(response);
  } catch {
    return errorBody(503, "dependency_unavailable", "API indisponible");
  }
}

export async function accountApiWithCookies<T>(
  path: string,
  init?: RequestInit,
): Promise<ApiResult<T> & { setCookie?: string | null }> {
  try {
    const response = await fetch(`${API_BASE}${path}`, {
      ...init,
      cache: "no-store",
      headers: {
        "Content-Type": "application/json",
        ...(init?.headers ?? {}),
      },
    });
    const setCookies =
      typeof response.headers.getSetCookie === "function" ? response.headers.getSetCookie() : [];
    const setCookie =
      setCookies.find((value) => value.startsWith("rm_session=")) ?? response.headers.get("set-cookie");
    if (!response.ok) {
      return { ...(await parseError(response)), setCookie };
    }
    return { ...(await readBody<T>(response)), setCookie };
  } catch {
    return errorBody(503, "dependency_unavailable", "API indisponible");
  }
}

async function consoleFetch(path: string, cookieHeader: string, init?: RequestInit): Promise<Response> {
  return fetch(`${API_BASE}${path}`, {
    ...init,
    cache: "no-store",
    headers: {
      "Content-Type": "application/json",
      Cookie: cookieHeader,
      ...(init?.headers ?? {}),
    },
  });
}

export async function consoleApi<T>(path: string, cookieHeader: string, init?: RequestInit): Promise<ApiResult<T>> {
  if (!cookieHeader) {
    return errorBody(401, "unauthorized", "Session expirée");
  }
  try {
    const response = await consoleFetch(path, cookieHeader, init);
    if (response.status === 401) {
      return errorBody(401, "unauthorized", "Session expirée");
    }
    if (response.status === 404) {
      return errorBody(404, "not_found", "Ressource introuvable");
    }
    if (!response.ok) {
      return parseError(response);
    }
    return readBody<T>(response);
  } catch {
    return errorBody(503, "dependency_unavailable", "API indisponible");
  }
}

export type Verification = {
  id: string;
  status: string;
  hosted_url?: string | null;
  expires_at: string;
  applicant?: { first_name?: string | null; last_name?: string | null; email?: string | null } | null;
  metadata?: Record<string, unknown> | null;
  decision?: string | null;
  decision_reasons?: string[] | null;
  signals?: { code: string; outcome: string; score?: number | null }[] | null;
  extracted_identity?: {
    first_name?: string;
    last_name?: string;
    birth_date?: string;
    document_type?: string;
    document_country?: string;
    document_number?: string;
  } | null;
  created_at: string;
  updated_at: string;
};

export type VerificationList = {
  items: Verification[];
  next_cursor?: string | null;
};

export type MediaUrl = {
  url: string;
  expires_at: string;
};

export type InvitePreview = {
  email: string;
  role: string;
  expires_at: string;
  organization_name: string;
  invited_by_name: string;
};

export function peekInvite(token: string): Promise<ApiResult<InvitePreview>> {
  return accountApi(`/v1/account/invites?token=${encodeURIComponent(token)}`);
}

export function getMeWithCookie(cookieHeader: string): Promise<ApiResult<Me>> {
  return consoleApi<Me>("/v1/console/me", cookieHeader);
}

export { API_BASE };
