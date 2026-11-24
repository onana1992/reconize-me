"use server";

import { consoleApi, type Enrollment } from "../../../../lib/api";
import { sessionCookieHeader } from "../../../../lib/session";

export async function revokeEnrollmentAction(id: string) {
  return consoleApi<Enrollment>(
    `/v1/console/enrollments/${encodeURIComponent(id)}/revoke`,
    await sessionCookieHeader(),
    { method: "POST" },
  );
}
