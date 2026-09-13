import { FlowClient } from "./flow-client";

export const dynamic = "force-dynamic";

export default async function FlowPage({ params }: { params: Promise<{ token: string }> }) {
  const { token } = await params;
  return <FlowClient token={token} />;
}
