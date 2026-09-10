import { FlowClient } from "./flow-client";

export const dynamic = "force-dynamic";

export default async function FlowPage({ params }: { params: Promise<{ token: string }> }) {
  const { token } = await params;
  const apiBase = process.env.API_BASE_URL ?? "http://localhost:8080";
  return <FlowClient token={token} apiBase={apiBase} />;
}
