const API_BASE = process.env.API_BASE_URL ?? "http://localhost:8080";
const API_KEY = process.env.KYC_API_KEY ?? "";

export type Applicant = {
  first_name?: string | null;
  last_name?: string | null;
  email?: string | null;
};

export type Verification = {
  id: string;
  external_id?: string | null;
  status: string;
  applicant?: Applicant | null;
  hosted_url: string | null;
  expires_at: string;
  created_at: string;
  updated_at: string;
};

export type ApiResult<T> =
  | { ok: true; data: T }
  | { ok: false; status: number; code: string; message: string };

async function api<T>(path: string, init?: RequestInit): Promise<ApiResult<T>> {
  if (!API_KEY) {
    return {
      ok: false,
      status: 401,
      code: "unauthorized",
      message: "Session expirée",
    };
  }
  try {
    const response = await fetch(`${API_BASE}${path}`, {
      ...init,
      cache: "no-store",
      headers: {
        Authorization: `Bearer ${API_KEY}`,
        "Content-Type": "application/json",
        ...(init?.headers ?? {}),
      },
    });
    if (response.status === 401) {
      return { ok: false, status: 401, code: "unauthorized", message: "Session expirée" };
    }
    if (response.status === 404) {
      return { ok: false, status: 404, code: "not_found", message: "Vérification introuvable" };
    }
    if (!response.ok) {
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
    if (response.status === 204) {
      return { ok: true, data: undefined as T };
    }
    return { ok: true, data: (await response.json()) as T };
  } catch {
    return { ok: false, status: 503, code: "dependency_unavailable", message: "API indisponible" };
  }
}

export function createVerification(body: {
  external_id?: string;
  applicant?: Applicant;
}): Promise<ApiResult<Verification>> {
  return api<Verification>("/v1/verifications", {
    method: "POST",
    body: JSON.stringify(body),
  });
}

export function getVerification(id: string): Promise<ApiResult<Verification>> {
  return api<Verification>(`/v1/verifications/${encodeURIComponent(id)}`);
}
