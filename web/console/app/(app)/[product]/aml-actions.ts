"use server";

import { consoleApi, type ApiResult, type Screening, type ScreeningList } from "../../../lib/api";
import { sessionCookieHeader } from "../../../lib/session";

export async function createScreeningAction(formData: FormData): Promise<ApiResult<Screening>> {
  const firstName = String(formData.get("first_name") ?? "").trim();
  const lastName = String(formData.get("last_name") ?? "").trim();
  const dateOfBirth = String(formData.get("date_of_birth") ?? "").trim();
  const nationality = String(formData.get("nationality") ?? "").trim();
  return consoleApi("/v1/console/screenings", await sessionCookieHeader(), {
    method: "POST",
    body: JSON.stringify({
      integration_id: String(formData.get("integration_id") ?? ""),
      first_name: firstName,
      last_name: lastName,
      ...(dateOfBirth ? { date_of_birth: dateOfBirth } : {}),
      ...(nationality ? { nationality } : {}),
    }),
  });
}

export async function listScreeningsAction(cursor?: string): Promise<ApiResult<ScreeningList>> {
  const params = new URLSearchParams({ limit: "50" });
  if (cursor) {
    params.set("cursor", cursor);
  }
  return consoleApi(`/v1/console/screenings?${params.toString()}`, await sessionCookieHeader());
}

export async function qualifyHitAction(formData: FormData): Promise<ApiResult<Screening>> {
  const screeningId = String(formData.get("screening_id") ?? "");
  const hitId = String(formData.get("hit_id") ?? "");
  return consoleApi(
    `/v1/console/screenings/${encodeURIComponent(screeningId)}/hits/${encodeURIComponent(hitId)}/disposition`,
    await sessionCookieHeader(),
    {
      method: "POST",
      body: JSON.stringify({
        disposition: String(formData.get("disposition") ?? ""),
        reason: String(formData.get("reason") ?? ""),
        note: String(formData.get("note") ?? ""),
      }),
    },
  );
}

export async function publishAmlPolicyAction(formData: FormData): Promise<ApiResult<unknown>> {
  const categories = formData.getAll("categories").map((item) => String(item));
  return consoleApi("/v1/console/aml/policy", await sessionCookieHeader(), {
    method: "POST",
    body: JSON.stringify({
      review_threshold: Number(formData.get("review_threshold")),
      confirm_threshold: Number(formData.get("confirm_threshold")),
      categories,
      attach_to_identity: formData.get("attach_to_identity") === "on",
      on_match: String(formData.get("on_match") ?? "review"),
      integration_id: optionalId(formData.get("integration_id")),
      test_integration_id: optionalId(formData.get("test_integration_id")),
      live_integration_id: optionalId(formData.get("live_integration_id")),
    }),
  });
}

function optionalId(value: FormDataEntryValue | null) {
  const text = String(value ?? "").trim();
  return text || null;
}
