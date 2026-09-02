const API_BASE = process.env.API_BASE_URL ?? "http://localhost:8080";

export type FlowSession = {
  verification_id: string;
  status: string;
  consent_text_version: string;
  expires_at: string;
};

export async function fetchFlowSession(token: string): Promise<
  | { ok: true; session: FlowSession }
  | { ok: false; status: number; code?: string }
> {
  try {
    const response = await fetch(`${API_BASE}/v1/flow/${encodeURIComponent(token)}`, {
      cache: "no-store",
    });
    if (!response.ok) {
      const body = (await response.json().catch(() => ({}))) as { error?: { code?: string } };
      return { ok: false, status: response.status, code: body.error?.code };
    }
    return { ok: true, session: (await response.json()) as FlowSession };
  } catch {
    return { ok: false, status: 503, code: "dependency_unavailable" };
  }
}

export async function submitConsent(token: string, decision: "accepted" | "declined"): Promise<void> {
  try {
    const response = await fetch(`${API_BASE}/v1/flow/${encodeURIComponent(token)}/consent`, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ decision }),
      cache: "no-store",
    });
    if (!response.ok) {
      throw new Error("consent_failed");
    }
  } catch {
    throw new Error("consent_failed");
  }
}
