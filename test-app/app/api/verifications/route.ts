const DEPLOYED_API_URL = "https://recogniz-me-api.onrender.com";

type ClientBody = {
  externalId?: string;
  firstName?: string;
  lastName?: string;
  email?: string;
};

type ApiFailure = {
  error?: {
    message?: string;
  };
};

type ApiSession = ApiFailure & {
  hosted_url?: string;
};

export async function POST(request: Request) {
  const apiKey = process.env.RECOGNIZME_API_KEY;
  if (!apiKey) {
    return Response.json({ error: "RECOGNIZME_API_KEY manquante." }, { status: 500 });
  }

  const body = (await request.json().catch(() => ({}))) as ClientBody;
  const apiBase = (process.env.RECOGNIZME_API_BASE ?? DEPLOYED_API_URL).replace(/\/$/, "");
  const externalId = emptyToUndefined(body.externalId);

  let response: Response;
  try {
    response = await fetch(`${apiBase}/v1/verifications`, {
      method: "POST",
      headers: {
        Authorization: `Bearer ${apiKey}`,
        "Content-Type": "application/json",
        "Idempotency-Key": idempotencyKey(externalId),
      },
      body: JSON.stringify({
        external_id: externalId,
        applicant: {
          first_name: emptyToUndefined(body.firstName),
          last_name: emptyToUndefined(body.lastName),
          email: emptyToUndefined(body.email),
        },
      }),
    });
  } catch {
    return Response.json({ error: "API unreachable" }, { status: 502 });
  }

  const session = (await response.json().catch(() => ({}))) as ApiSession;
  if (!response.ok || !session.hosted_url) {
    return Response.json(
      { error: session.error?.message ?? "Recogniz-Me a refusé la session." },
      { status: response.ok ? 502 : response.status },
    );
  }
  return Response.json({ hosted_url: session.hosted_url });
}

function emptyToUndefined(value: string | undefined): string | undefined {
  const trimmed = value?.trim();
  return trimmed ? trimmed : undefined;
}

function idempotencyKey(externalId: string | undefined): string {
  const usable =
    typeof externalId === "string" &&
    externalId.length >= 8 &&
    externalId.length <= 64 &&
    [...externalId].every((ch) => {
      const code = ch.charCodeAt(0);
      return code >= 32 && code <= 126;
    });
  return usable ? externalId : crypto.randomUUID();
}
