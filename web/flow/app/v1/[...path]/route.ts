import { NextRequest, NextResponse } from "next/server";

export const dynamic = "force-dynamic";
export const runtime = "nodejs";

const API_BASE = process.env.API_BASE_URL ?? "http://127.0.0.1:8080";

async function proxy(request: NextRequest, path: string[]) {
  const incoming = new URL(request.url);
  const target = `${API_BASE}/v1/${path.map(encodeURIComponent).join("/")}${incoming.search}`;
  const headers = new Headers();
  for (const name of ["content-type", "authorization", "user-agent", "x-forwarded-for", "x-request-id"]) {
    const value = request.headers.get(name);
    if (value) {
      headers.set(name, value);
    }
  }
  const method = request.method.toUpperCase();
  const init: RequestInit = { method, headers, cache: "no-store", redirect: "manual" };
  if (method !== "GET" && method !== "HEAD") {
    init.body = await request.arrayBuffer();
  }
  let upstream: Response;
  try {
    upstream = await fetch(target, init);
  } catch {
    return NextResponse.json({ error: { code: "network", message: "API unreachable" } }, { status: 502 });
  }
  const out = new Headers();
  for (const name of ["content-type", "cache-control", "x-request-id"]) {
    const value = upstream.headers.get(name);
    if (value) {
      out.set(name, value);
    }
  }
  return new NextResponse(upstream.body, { status: upstream.status, headers: out });
}

type Ctx = { params: Promise<{ path: string[] }> };

export async function GET(request: NextRequest, ctx: Ctx) {
  return proxy(request, (await ctx.params).path);
}

export async function POST(request: NextRequest, ctx: Ctx) {
  return proxy(request, (await ctx.params).path);
}

export async function PUT(request: NextRequest, ctx: Ctx) {
  return proxy(request, (await ctx.params).path);
}

export async function PATCH(request: NextRequest, ctx: Ctx) {
  return proxy(request, (await ctx.params).path);
}

export async function DELETE(request: NextRequest, ctx: Ctx) {
  return proxy(request, (await ctx.params).path);
}

export async function OPTIONS(request: NextRequest, ctx: Ctx) {
  return proxy(request, (await ctx.params).path);
}
