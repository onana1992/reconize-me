import { cache } from "react";
import { consoleApi, type IntegrationResponse } from "../../../../lib/api";
import { sessionCookieHeader } from "../../../../lib/session";

export const loadIntegration = cache(async (id: string) => {
  return consoleApi<IntegrationResponse>(
    `/v1/console/integrations/${encodeURIComponent(id)}`,
    await sessionCookieHeader(),
  );
});
