import type { Translate } from "../i18n";

export const ASSIGNABLE_ROLES = ["admin", "member", "readonly", "developer"] as const;

export const ALL_ROLES = ["owner", "admin", "member", "readonly", "developer"] as const;

export function roleLabel(role: string): string {
  if (role === "owner") {
    return "Propriétaire";
  }
  if (role === "admin") {
    return "Admin";
  }
  if (role === "member") {
    return "Membre";
  }
  if (role === "readonly") {
    return "Lecture seule";
  }
  if (role === "developer") {
    return "Développeur";
  }
  return role;
}

export function displayName(firstName?: string | null, lastName?: string | null, email = ""): string {
  const name = [firstName, lastName].filter((part) => part && part.trim()).join(" ").trim();
  return name || email;
}

export function initials(firstName?: string | null, lastName?: string | null, email = ""): string {
  const first = firstName?.trim().charAt(0);
  const last = lastName?.trim().charAt(0);
  if (first && last) {
    return (first + last).toUpperCase();
  }
  if (first) {
    return first.toUpperCase();
  }
  const local = email.trim().charAt(0);
  return (local || "U").toUpperCase();
}

export function tRole(t: Translate, role: string): string {
  switch (role) {
    case "owner":
      return t("console.role.owner");
    case "admin":
      return t("console.role.admin");
    case "member":
      return t("console.role.member");
    case "readonly":
      return t("console.role.readonly");
    case "developer":
      return t("console.role.developer");
    default:
      return role;
  }
}

export function tAuditAction(t: Translate, action: string): string {
  switch (action) {
    case "membership.invited":
      return t("console.activity.actionInvited");
    case "membership.invite_resent":
      return t("console.activity.actionInviteResent");
    case "membership.invite_cancelled":
      return t("console.activity.actionInviteCancelled");
    case "membership.removed":
      return t("console.activity.actionRemoved");
    case "membership.role_changed":
      return t("console.activity.actionRoleChanged");
    case "membership.disabled":
      return t("console.activity.actionDisabled");
    case "membership.enabled":
      return t("console.activity.actionEnabled");
    case "api_key.issued":
      return t("console.activity.actionKeyIssued");
    case "api_key.revoked":
      return t("console.activity.actionKeyRevoked");
    case "integration.created":
      return t("console.activity.actionIntegrationCreated");
    case "billing.checkout_created":
      return t("console.activity.actionCheckoutCreated");
    case "credit.topped_up":
      return t("console.activity.actionCreditToppedUp");
    case "credit.debited":
      return t("console.activity.actionCreditDebited");
    case "credit.topup_ignored":
      return t("console.activity.actionCreditTopupIgnored");
    case "verification.created":
      return t("console.activity.actionVerificationCreated");
    case "verification.cancelled":
      return t("console.activity.actionVerificationCancelled");
    case "verification.reviewed":
      return t("console.activity.actionVerificationReviewed");
    case "verification.completed":
      return t("console.activity.actionVerificationCompleted");
    case "verification.declined":
      return t("console.activity.actionVerificationDeclined");
    case "verification.expired":
      return t("console.activity.actionVerificationExpired");
    case "hosted_link.issued":
      return t("console.activity.actionLinkIssued");
    case "hosted_link.opened":
      return t("console.activity.actionLinkOpened");
    case "consent.accepted":
      return t("console.activity.actionConsentAccepted");
    case "consent.declined":
      return t("console.activity.actionConsentDeclined");
    case "screening.created":
      return t("console.activity.actionScreeningCreated");
    case "screening.failed":
      return t("console.activity.actionScreeningFailed");
    case "screening.disposition_set":
      return t("console.activity.actionScreeningDisposition");
    case "idv.policy.published":
      return t("console.activity.actionIdvPolicyPublished");
    case "aml_policy.published":
      return t("console.activity.actionAmlPolicyPublished");
    case "webhook.upserted":
      return t("console.activity.actionWebhookSaved");
    case "webhook.deleted":
      return t("console.activity.actionWebhookDeleted");
    case "webhook.rotated":
      return t("console.activity.actionWebhookRotated");
    case "webhook.delivered":
      return t("console.activity.actionWebhookDelivered");
    case "webhook.failed":
      return t("console.activity.actionWebhookFailed");
    case "user.login_failed":
      return t("console.activity.actionLoginFailed");
    case "user.registered":
      return t("console.activity.actionRegistered");
    case "user.email_verified":
      return t("console.activity.actionEmailVerified");
    default:
      return action;
  }
}

export function tResourceType(t: Translate, type: string): string {
  switch (type) {
    case "user":
      return t("console.activity.resourceUser");
    case "membership_invite":
      return t("console.activity.resourceInvite");
    case "api_key":
      return t("console.activity.resourceKey");
    case "integration":
      return t("console.activity.resourceIntegration");
    case "credit_ledger_entry":
      return t("console.activity.resourceCredit");
    case "organization":
      return t("console.activity.resourceOrg");
    case "verification":
      return t("console.activity.resourceVerification");
    case "idv_policy":
      return t("console.activity.resourceIdvPolicy");
    case "aml_policy":
      return t("console.activity.resourceAmlPolicy");
    case "screening":
      return t("console.activity.resourceScreening");
    case "webhook_endpoint":
      return t("console.activity.resourceWebhook");
    case "webhook_delivery":
      return t("console.activity.resourceWebhookDelivery");
    default:
      return type;
  }
}

export const AUDIT_FILTERS = [
  "membership.invited",
  "membership.invite_resent",
  "membership.invite_cancelled",
  "membership.removed",
  "membership.role_changed",
  "membership.disabled",
  "membership.enabled",
  "api_key.issued",
  "api_key.revoked",
  "integration.created",
  "user.login_failed",
  "user.registered",
  "user.email_verified",
  "verification.created",
  "verification.cancelled",
  "verification.reviewed",
  "verification.completed",
  "verification.declined",
  "verification.expired",
  "hosted_link.issued",
  "hosted_link.opened",
  "consent.accepted",
  "consent.declined",
  "screening.created",
  "screening.failed",
  "screening.disposition_set",
  "idv.policy.published",
  "aml_policy.published",
  "webhook.upserted",
  "webhook.deleted",
  "webhook.rotated",
  "webhook.delivered",
  "webhook.failed",
] as const;
