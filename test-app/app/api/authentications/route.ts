import { rewriteHostedUrl } from "@/lib/hosted-url";

const DEPLOYED_API_URL = "https://recogniz-me-api.onrender.com";

type ClientBody = {
  externalId?: string;
};

type ApiFailure = {
  error?: {
    code?: string;
    message?: string;
  };
};

type ApiSession = ApiFailure & {
  id?: string;
  hosted_url?: string;
  status?: string;
};

export async function POST(request: Request) {
  const apiKey = process.env.RECOGNIZME_AUTH_API_KEY;
  if (!apiKey) {
    return Response.json({ error: "RECOGNIZME_AUTH_API_KEY manquante." }, { status: 500 });
  }

  const body = (await request.json().catch(() => ({}))) as ClientBody;
  const apiBase = (
    process.env.RECOGNIZME_AUTH_API_BASE ??
    process.env.RECOGNIZME_API_BASE ??
    DEPLOYED_API_URL
  ).replace(/\/$/, "");
  const externalId = emptyToUndefined(body.externalId);
  if (!externalId) {
    return Response.json({ error: "external_id is required" }, { status: 400 });
  }

  let response: Response;
  try {
    response = await fetch(`${apiBase}/v1/authentications`, {
      method: "POST",
      headers: {
        Authorization: `Bearer ${apiKey}`,
        "Content-Type": "application/json",
        "Idempotency-Key": idempotencyKey(externalId),
      },
      body: JSON.stringify({
        external_id: externalId,
      }),
    });
  } catch {
    return Response.json({ error: "API unreachable" }, { status: 502 });
  }

  const session = (await response.json().catch(() => ({}))) as ApiSession;
  if (!response.ok || !session.hosted_url || !session.id) {
    return Response.json(
      {
        error:
          session.error?.message ??
          (session.error?.code ? `Recogniz-Me: ${session.error.code}` : "Recogniz-Me a refusé la session."),
      },
      { status: response.ok ? 502 : response.status },
    );
  }
  return Response.json({
    id: session.id,
    hosted_url: rewriteHostedUrl(session.hosted_url),
    status: session.status ?? "created",
  });
}

function emptyToUndefined(value: string | undefined): string | undefined {
  const trimmed = value?.trim();
  return trimmed ? trimmed : undefined;
}

function idempotencyKey(externalId: string): string {
  const usable =
    externalId.length >= 8 &&
    externalId.length <= 64 &&
    [...externalId].every((ch) => {
      const code = ch.charCodeAt(0);
      return code >= 32 && code <= 126;
    });
  return usable ? `auth-${externalId}` : crypto.randomUUID();
}
