"use server";

import { createVerification, getVerification, type Applicant, type ApiResult, type Verification } from "../../lib/api";

function blankToUndefined(value: FormDataEntryValue | null): string | undefined {
  if (typeof value !== "string") {
    return undefined;
  }
  const trimmed = value.trim();
  return trimmed.length === 0 ? undefined : trimmed;
}

export async function createVerificationAction(formData: FormData): Promise<ApiResult<Verification>> {
  const externalId = blankToUndefined(formData.get("external_id"));
  const applicant: Applicant = {
    first_name: blankToUndefined(formData.get("first_name")),
    last_name: blankToUndefined(formData.get("last_name")),
    email: blankToUndefined(formData.get("email")),
  };
  const hasApplicant = Boolean(applicant.first_name || applicant.last_name || applicant.email);
  return createVerification({
    ...(externalId ? { external_id: externalId } : {}),
    ...(hasApplicant ? { applicant } : {}),
  });
}

export async function getVerificationAction(id: string): Promise<ApiResult<Verification>> {
  return getVerification(id);
}
