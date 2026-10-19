import { recordWebhook, recentWebhooks, signatureMatches } from "../../../webhook-log";

export const runtime = "nodejs";

export async function GET() {
  return Response.json({ items: recentWebhooks() });
}

export async function POST(request: Request) {
  const raw = await request.text();
  const secret = process.env.RECOGNIZME_WEBHOOK_SECRET ?? "";
  const signatureOk = signatureMatches(secret, request.headers.get("x-recognizme-signature"), raw);
  let event: string | null = null;
  try {
    const parsed = JSON.parse(raw) as { type?: unknown };
    event = typeof parsed.type === "string" ? parsed.type : null;
  } catch {
    event = null;
  }
  recordWebhook({
    at: new Date().toISOString(),
    signatureOk,
    event,
    body: raw,
  });
  if (!signatureOk) {
    return new Response("invalid signature", { status: 401 });
  }
  return new Response("ok", { status: 200 });
}
