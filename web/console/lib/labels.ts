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
] as const;
