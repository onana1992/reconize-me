"use server";

import { submitConsent } from "../../../lib/api";

export async function submitConsentAction(token: string, decision: "accepted" | "declined") {
  await submitConsent(token, decision);
}
