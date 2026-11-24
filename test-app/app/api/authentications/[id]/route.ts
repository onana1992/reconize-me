const DEPLOYED_API_URL = "https://recogniz-me-api.onrender.com";

type ApiFailure = {
  error?: {
    code?: string;
    message?: string;
  };
};

type ApiSession = ApiFailure & {
  id?: string;
  status?: string;
  reasons?: string[] | null;
  match_score?: number | null;
  failure_reason?: string | null;
  decided_at?: string | null;
};

export async function GET(_request: Request, context: { params: Promise<{ id: string }> }) {
  const apiKey = process.env.RECOGNIZME_AUTH_API_KEY;
  if (!apiKey) {
    return Response.json({ error: "RECOGNIZME_AUTH_API_KEY manquante." }, { status: 500 });
  }

  const { id } = await context.params;
  if (!id) {
    return Response.json({ error: "id is required" }, { status: 400 });
  }

  const apiBase = (
    process.env.RECOGNIZME_AUTH_API_BASE ??
    process.env.RECOGNIZME_API_BASE ??
    DEPLOYED_API_URL
  ).replace(/\/$/, "");
  let response: Response;
  try {
    response = await fetch(`${apiBase}/v1/authentications/${encodeURIComponent(id)}`, {
      headers: {
        Authorization: `Bearer ${apiKey}`,
      },
      cache: "no-store",
    });
  } catch {
    return Response.json({ error: "API unreachable" }, { status: 502 });
  }

  const session = (await response.json().catch(() => ({}))) as ApiSession;
  if (!response.ok) {
    return Response.json(
      {
        error:
          session.error?.message ??
          (session.error?.code ? `Recogniz-Me: ${session.error.code}` : "Authentication not found"),
      },
      { status: response.status },
    );
  }

  return Response.json({
    id: session.id,
    status: session.status,
    reasons: session.reasons ?? null,
    match_score: session.match_score ?? null,
    failure_reason: session.failure_reason ?? null,
    decided_at: session.decided_at ?? null,
  });
}
