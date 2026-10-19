import { createHmac, timingSafeEqual } from "crypto";

export type ReceivedWebhook = {
  at: string;
  signatureOk: boolean;
  event: string | null;
  body: string;
};

type Store = { items: ReceivedWebhook[] };

const globalStore = globalThis as typeof globalThis & { __recognizmeWebhooks?: Store };

function store(): Store {
  if (!globalStore.__recognizmeWebhooks) {
    globalStore.__recognizmeWebhooks = { items: [] };
  }
  return globalStore.__recognizmeWebhooks;
}

export function recentWebhooks(): ReceivedWebhook[] {
  return store().items;
}

export function recordWebhook(item: ReceivedWebhook) {
  const items = store().items;
  items.unshift(item);
  items.splice(20);
}

export function signatureMatches(secret: string, header: string | null, rawBody: string): boolean {
  if (!secret || !header) {
    return false;
  }
  const match = /^t=(\d+),v1=([0-9a-f]+)$/.exec(header.trim());
  if (!match) {
    return false;
  }
  const timestamp = Number(match[1]);
  const now = Math.floor(Date.now() / 1000);
  if (!Number.isFinite(timestamp) || Math.abs(now - timestamp) > 300) {
    return false;
  }
  const expected = createHmac("sha256", secret).update(`${timestamp}.${rawBody}`).digest("hex");
  const actual = Buffer.from(match[2], "utf8");
  const computed = Buffer.from(expected, "utf8");
  if (actual.length !== computed.length) {
    return false;
  }
  return timingSafeEqual(actual, computed);
}
