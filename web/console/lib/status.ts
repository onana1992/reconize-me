import type { BadgeTone } from "@kyc/brand";

export const STATUS_LABELS: Record<string, string> = {
  created: "Créée",
  pending_consent: "Consentement",
  pending_applicant: "En attente de capture",
  declined: "Refusée",
  expired: "Expirée",
  cancelled: "Annulée",
};

const STATUS_TONES: Record<string, BadgeTone> = {
  created: "info",
  pending_consent: "warning",
  pending_applicant: "progress",
  declined: "danger",
  expired: "neutral",
  cancelled: "neutral",
};

export function statusLabel(status: string): string {
  return STATUS_LABELS[status] ?? status;
}

export function statusTone(status: string): BadgeTone {
  return STATUS_TONES[status] ?? "neutral";
}

export function formatUtc(iso: string): string {
  const date = new Date(iso);
  if (Number.isNaN(date.getTime())) {
    return iso;
  }
  return new Intl.DateTimeFormat("fr-FR", {
    dateStyle: "short",
    timeStyle: "medium",
    timeZone: "UTC",
  }).format(date) + " UTC";
}
