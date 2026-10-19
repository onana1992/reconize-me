import type { BadgeTone } from "@/brand";
import type { Translate } from "../i18n";

export function formatUtc(iso: string, locale = "fr"): string {
  const date = new Date(iso);
  if (Number.isNaN(date.getTime())) {
    return iso;
  }
  return (
    new Intl.DateTimeFormat(locale, {
      dateStyle: "short",
      timeStyle: "medium",
      timeZone: "UTC",
    }).format(date) + " UTC"
  );
}

export const VERIFICATION_STATUSES = [
  "created",
  "pending_consent",
  "pending_applicant",
  "document",
  "recapture_requested",
  "selfie",
  "processing",
  "review",
  "approved",
  "declined",
  "expired",
  "cancelled",
] as const;

const STATUS_TONE: Record<string, BadgeTone> = {
  created: "info",
  pending_consent: "progress",
  pending_applicant: "progress",
  document: "progress",
  recapture_requested: "warning",
  selfie: "progress",
  processing: "progress",
  review: "warning",
  approved: "success",
  declined: "danger",
  expired: "neutral",
  cancelled: "neutral",
};

export function verificationTone(status: string): BadgeTone {
  return STATUS_TONE[status] ?? "neutral";
}

export function tVerificationStatus(t: Translate, status: string): string {
  switch (status) {
    case "created":
      return t("console.verifications.status.created");
    case "pending_consent":
      return t("console.verifications.status.pendingConsent");
    case "pending_applicant":
      return t("console.verifications.status.pendingApplicant");
    case "document":
      return t("console.verifications.status.document");
    case "recapture_requested":
      return t("console.verifications.status.recapture");
    case "selfie":
      return t("console.verifications.status.selfie");
    case "processing":
      return t("console.verifications.status.processing");
    case "review":
      return t("console.verifications.status.review");
    case "approved":
      return t("console.verifications.status.approved");
    case "declined":
      return t("console.verifications.status.declined");
    case "expired":
      return t("console.verifications.status.expired");
    case "cancelled":
      return t("console.verifications.status.cancelled");
    default:
      return status;
  }
}

export function canCancelVerification(status: string): boolean {
  return ["created", "pending_consent", "pending_applicant", "document", "recapture_requested", "selfie"].includes(
    status,
  );
}

export function decisionTone(decision: string): BadgeTone {
  if (decision === "approved") {
    return "success";
  }
  if (decision === "declined") {
    return "danger";
  }
  if (decision === "review") {
    return "warning";
  }
  return "neutral";
}

export function signalOutcomeTone(outcome: string): BadgeTone {
  if (outcome === "pass") {
    return "success";
  }
  if (outcome === "fail") {
    return "danger";
  }
  if (outcome === "unavailable") {
    return "warning";
  }
  return "neutral";
}

export function tDecision(t: Translate, decision: string): string {
  switch (decision) {
    case "approved":
      return t("console.verifications.decisions.approved");
    case "declined":
      return t("console.verifications.decisions.declined");
    case "review":
      return t("console.verifications.decisions.review");
    default:
      return decision;
  }
}

export function tSignalOutcome(t: Translate, outcome: string): string {
  switch (outcome) {
    case "pass":
      return t("console.verifications.outcomes.pass");
    case "fail":
      return t("console.verifications.outcomes.fail");
    case "unavailable":
      return t("console.verifications.outcomes.unavailable");
    default:
      return outcome;
  }
}

export function tSignalCode(t: Translate, code: string): string {
  switch (code) {
    case "authenticity_stub_pass":
      return t("console.verifications.signalCodes.authenticity");
    case "liveness_pass":
      return t("console.verifications.signalCodes.livenessPass");
    case "face_match_pass":
      return t("console.verifications.signalCodes.faceMatchPass");
    case "liveness_fail":
      return t("console.verifications.signalCodes.livenessFail");
    case "face_match_fail":
      return t("console.verifications.signalCodes.faceMatchFail");
    case "unsupported_document":
      return t("console.verifications.signalCodes.unsupportedDocument");
    case "document_expired":
      return t("console.verifications.signalCodes.documentExpired");
    case "mrz_unavailable":
      return t("console.verifications.signalCodes.mrzUnavailable");
    case "provider_unavailable":
      return t("console.verifications.signalCodes.providerUnavailable");
    case "invalid_model_json":
      return t("console.verifications.signalCodes.invalidModelJson");
    case "IMAGE_TOO_BLURRY":
      return t("console.verifications.signalCodes.imageTooBlurry");
    case "TOO_SMALL":
      return t("console.verifications.signalCodes.tooSmall");
    case "TOO_LARGE":
      return t("console.verifications.signalCodes.tooLarge");
    case "LOW_LIGHT":
      return t("console.verifications.signalCodes.lowLight");
    case "OVEREXPOSED":
      return t("console.verifications.signalCodes.overexposed");
    case "INVALID_IMAGE":
      return t("console.verifications.signalCodes.invalidImage");
    case "DOCUMENT_SIDE_MISMATCH":
      return t("console.verifications.signalCodes.documentSideMismatch");
    case "DOCUMENT_UNKNOWN":
      return t("console.verifications.signalCodes.documentUnknown");
    case "MISSING_REQUIRED_FIELD":
      return t("console.verifications.signalCodes.missingRequiredField");
    case "MRZ_MISMATCH":
      return t("console.verifications.signalCodes.mrzMismatch");
    case "LOW_OCR_CONFIDENCE":
      return t("console.verifications.signalCodes.lowOcrConfidence");
    case "SUSPICIOUS_INDICATOR":
      return t("console.verifications.signalCodes.suspiciousIndicator");
    case "LOW_CONFIDENCE":
      return t("console.verifications.signalCodes.lowConfidence");
    case "DOCUMENT_MISMATCH":
      return t("console.verifications.signalCodes.documentMismatch");
    case "face_match_borderline":
      return t("console.verifications.signalCodes.faceMatchBorderline");
    case "capture_attempts_exceeded":
      return t("console.verifications.signalCodes.captureAttemptsExceeded");
    case "consent_declined":
      return t("console.verifications.signalCodes.consentDeclined");
    case "document_fields_incomplete":
      return t("console.verifications.signalCodes.documentFieldsIncomplete");
    default:
      return code;
  }
}

export function tDocumentCode(t: Translate, code: string): string {
  switch (code) {
    case "CANADA_PERMANENT_RESIDENT":
      return t("console.rules.nameCanadaPermanentResident");
    case "QUEBEC_DRIVER_LICENSE":
      return t("console.rules.nameQuebecDriverLicense");
    case "PASSPORT_TD3":
      return t("console.verifications.documentTypes.passport");
    default:
      return tDocumentType(t, code);
  }
}

export function tDocumentType(t: Translate, code: string): string {
  switch (code) {
    case "driving_license":
      return t("console.verifications.documentTypes.driving_license");
    case "passport":
      return t("console.verifications.documentTypes.passport");
    case "residence_permit":
      return t("console.verifications.documentTypes.residence_permit");
    case "unknown":
      return t("console.verifications.documentTypes.unknown");
    default:
      return code;
  }
}

export function tSandboxScenario(t: Translate, scenario: string): string {
  switch (scenario) {
    case "approved":
      return t("console.verifications.scenarioApproved");
    case "unsupported":
      return t("console.verifications.scenarioUnsupported");
    case "expired":
      return t("console.verifications.scenarioExpired");
    case "liveness_fail":
      return t("console.verifications.scenarioLiveness");
    case "mismatch":
      return t("console.verifications.scenarioMismatch");
    case "review":
      return t("console.verifications.scenarioReview");
    default:
      return scenario;
  }
}
