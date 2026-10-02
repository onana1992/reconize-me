"use client";

import { useRouter } from "next/navigation";
import { useEffect, useRef, useState } from "react";
import { StatusBadge } from "@kyc/brand";
import { useLocale, useT } from "../../../../i18n/client";
import { formatUtc } from "../../../../lib/status";
import { ALL_ROLES, ASSIGNABLE_ROLES, displayName, initials, tRole } from "../../../../lib/labels";
import type { Team, TeamMember } from "../../../../lib/api";
import { InviteButton } from "./invite-form";
import {
  cancelInviteAction,
  changeMemberRoleAction,
  disableMemberAction,
  enableMemberAction,
  removeMemberAction,
  resendInviteAction,
  transferOwnershipAction,
} from "../actions";

export function TeamTables({
  members,
  invites,
  currentEmail,
  canWrite,
  canOwn,
}: {
  members: Team["members"];
  invites: Team["invites"];
  currentEmail: string;
  canWrite: boolean;
  canOwn: boolean;
}) {
  const t = useT();
  const locale = useLocale();
  const router = useRouter();
  const [error, setError] = useState<string | null>(null);
  const [pending, setPending] = useState<string | null>(null);
  const [selectedId, setSelectedId] = useState<string | null>(null);
  const dialogRef = useRef<HTMLDialogElement>(null);
  const activeOwners = members.filter((member) => member.role === "owner" && member.status !== "disabled").length;
  const roleOptions = canOwn ? ALL_ROLES : ASSIGNABLE_ROLES;
  const selected = members.find((member) => member.id === selectedId) ?? null;

  useEffect(() => {
    const dialog = dialogRef.current;
    if (!dialog) {
      return;
    }
    if (!selected) {
      if (dialog.open) {
        dialog.close();
      }
      return;
    }
    if (!dialog.open) {
      dialog.showModal();
    }
  }, [selected]);

  async function run(key: string, action: () => Promise<{ ok: boolean; code?: string; message?: string }>) {
    setPending(key);
    setError(null);
    const result = await action();
    setPending(null);
    if (!result.ok) {
      if (result.code === "forbidden") {
        setError(t("console.team.forbidden"));
      } else if (result.code === "last_owner") {
        setError(t("console.team.lastOwner"));
      } else if (result.code === "cannot_disable_self") {
        setError(t("console.team.cannotDisableSelf"));
      } else if (result.code === "cannot_change_own_role") {
        setError(t("console.team.cannotChangeOwnRole"));
      } else if (result.code === "cannot_remove_self") {
        setError(t("console.team.cannotRemoveSelf"));
      } else {
        setError(result.message ?? t("common.error"));
      }
      return;
    }
    router.refresh();
  }

  function openMember(member: TeamMember) {
    setSelectedId(member.id);
  }

  function closeMember() {
    dialogRef.current?.close();
  }

  return (
    <>
      {error ? (
        <p role="alert" className="rm-alert">
          {error}
        </p>
      ) : null}
      <section className="rm-section">
        <h2>{t("console.team.members")}</h2>
        <div className="rm-table-wrap">
          <table className="rm-table">
            <thead>
              <tr>
                <th>{t("common.email")}</th>
                <th>{t("console.home.roleLabel")}</th>
                <th>{t("console.team.status")}</th>
                {canWrite ? <th>{t("common.action")}</th> : null}
              </tr>
            </thead>
            <tbody>
              {members.map((member) => {
                const self = member.email === currentEmail;
                const lastOwner = member.role === "owner" && member.status !== "disabled" && activeOwners <= 1;
                const ownerTarget = member.role === "owner";
                const canEditRole = canWrite && !self && (!ownerTarget || canOwn) && !lastOwner;
                const canRemove = canWrite && !self && !lastOwner && (!ownerTarget || canOwn);
                const canToggle = canWrite && !self && (!ownerTarget || canOwn) && !lastOwner;
                const canTransfer = canOwn && !self && member.status !== "disabled" && member.role !== "owner";
                return (
                  <tr
                    key={member.id}
                    className="rm-table-row"
                    tabIndex={0}
                    aria-haspopup="dialog"
                    aria-selected={selectedId === member.id}
                    aria-label={displayName(member.first_name, member.last_name, member.email)}
                    onClick={(event) => {
                      if (isInteractiveTarget(event.target)) {
                        return;
                      }
                      openMember(member);
                    }}
                    onKeyDown={(event) => {
                      if (event.target !== event.currentTarget) {
                        return;
                      }
                      if (event.key === "Enter" || event.key === " ") {
                        event.preventDefault();
                        openMember(member);
                      }
                    }}
                  >
                    <td>{member.email}</td>
                    <td>
                      {canEditRole ? (
                        <select
                          aria-label={t("console.team.roleOf").replace("{email}", member.email)}
                          value={member.role}
                          disabled={pending === `role:${member.id}`}
                          onChange={(event) => {
                            const next = event.target.value;
                            void run(`role:${member.id}`, () => changeMemberRoleAction(member.id, next));
                          }}
                        >
                          {roleOptions.map((role) => (
                            <option key={role} value={role}>
                              {tRole(t, role)}
                            </option>
                          ))}
                        </select>
                      ) : (
                        tRole(t, member.role)
                      )}
                    </td>
                    <td>
                      <StatusBadge
                        label={member.status === "disabled" ? t("console.team.inactive") : t("console.team.active")}
                        tone={member.status === "disabled" ? "warning" : "success"}
                      />
                    </td>
                    {canWrite ? (
                      <td>
                        <div className="rm-table-actions">
                        {canTransfer ? (
                          <button
                            type="button"
                            data-variant="secondary"
                            disabled={pending === `xfer:${member.id}`}
                            onClick={() => {
                              if (!window.confirm(t("console.team.transferConfirm").replace("{email}", member.email))) {
                                return;
                              }
                              void run(`xfer:${member.id}`, () => transferOwnershipAction(member.id));
                            }}
                          >
                            {pending === `xfer:${member.id}` ? t("console.team.transferring") : t("console.team.transfer")}
                          </button>
                        ) : null}
                        {canToggle ? (
                          member.status === "disabled" ? (
                            <button
                              type="button"
                              disabled={pending === `enable:${member.id}`}
                              onClick={() => void run(`enable:${member.id}`, () => enableMemberAction(member.id))}
                            >
                              {pending === `enable:${member.id}` ? t("console.team.enabling") : t("console.team.enable")}
                            </button>
                          ) : (
                            <button
                              type="button"
                              data-variant="secondary"
                              disabled={pending === `disable:${member.id}`}
                              onClick={() => {
                                if (!window.confirm(t("console.team.disableConfirm").replace("{email}", member.email))) {
                                  return;
                                }
                                void run(`disable:${member.id}`, () => disableMemberAction(member.id));
                              }}
                            >
                              {pending === `disable:${member.id}` ? t("console.team.disabling") : t("console.team.disable")}
                            </button>
                          )
                        ) : null}
                        {canRemove ? (
                          <button
                            type="button"
                            data-variant="danger"
                            disabled={pending === `remove:${member.id}`}
                            onClick={() => {
                              if (!window.confirm(t("console.team.removeConfirm").replace("{email}", member.email))) {
                                return;
                              }
                              void run(`remove:${member.id}`, () => removeMemberAction(member.id));
                            }}
                          >
                            {pending === `remove:${member.id}` ? t("console.team.removing") : t("console.team.remove")}
                          </button>
                        ) : null}
                        </div>
                      </td>
                    ) : null}
                  </tr>
                );
              })}
            </tbody>
          </table>
        </div>
      </section>
      <section className="rm-section">
        <div className="rm-section-head">
          <h2>{t("console.team.invites")}</h2>
          {canWrite ? <InviteButton /> : null}
        </div>
        {invites.length === 0 ? (
          <div className="rm-empty">
            <p>{t("console.team.emptyInvites")}</p>
          </div>
        ) : (
          <div className="rm-table-wrap">
            <table className="rm-table">
              <thead>
                <tr>
                  <th>{t("common.email")}</th>
                  <th>{t("console.home.roleLabel")}</th>
                  <th>{t("console.team.expires")}</th>
                  {canWrite ? <th>{t("common.action")}</th> : null}
                </tr>
              </thead>
              <tbody>
                {invites.map((invite) => (
                  <tr key={invite.id}>
                    <td>{invite.email}</td>
                    <td>{tRole(t, invite.role)}</td>
                    <td>{formatUtc(invite.expires_at, locale)}</td>
                    {canWrite ? (
                      <td>
                        <div className="rm-table-actions">
                          <button
                            type="button"
                            disabled={pending === `resend:${invite.id}`}
                            onClick={() => void run(`resend:${invite.id}`, () => resendInviteAction(invite.id))}
                          >
                            {pending === `resend:${invite.id}` ? t("console.team.sending") : t("console.team.resend")}
                          </button>
                          <button
                            type="button"
                            data-variant="danger"
                            disabled={pending === `cancel:${invite.id}`}
                            onClick={() => void run(`cancel:${invite.id}`, () => cancelInviteAction(invite.id))}
                          >
                            {pending === `cancel:${invite.id}` ? t("console.team.cancelling") : t("common.cancel")}
                          </button>
                        </div>
                      </td>
                    ) : null}
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </section>
      <dialog
        ref={dialogRef}
        className="rm-dialog"
        aria-labelledby="member-profile-title"
        onClose={() => setSelectedId(null)}
        onClick={(event) => {
          if (event.target === event.currentTarget) {
            closeMember();
          }
        }}
      >
        {selected ? <MemberProfile member={selected} self={selected.email === currentEmail} onClose={closeMember} /> : null}
      </dialog>
    </>
  );
}

function MemberProfile({
  member,
  self,
  onClose,
}: {
  member: TeamMember;
  self: boolean;
  onClose: () => void;
}) {
  const t = useT();
  const locale = useLocale();
  const name = displayName(member.first_name, member.last_name, member.email);
  const avatar = initials(member.first_name, member.last_name, member.email);
  const role = tRole(t, member.role);
  const inactive = member.status === "disabled";
  const firstName = member.first_name?.trim() || t("console.account.empty");
  const lastName = member.last_name?.trim() || t("console.account.empty");

  return (
    <>
      <div className="rm-dialog-head">
        <h2 id="member-profile-title">{t("console.team.profileTitle")}</h2>
        <button type="button" className="rm-dialog-close" aria-label={t("common.close")} onClick={onClose}>
          <svg width="14" height="14" viewBox="0 0 14 14" aria-hidden="true">
            <path
              d="M3 3l8 8M11 3l-8 8"
              fill="none"
              stroke="currentColor"
              strokeWidth="1.5"
              strokeLinecap="round"
            />
          </svg>
        </button>
      </div>
      <article className="rm-account-hero">
        <span className="rm-shell-initial rm-account-avatar" aria-hidden="true">
          {avatar}
        </span>
        <div className="rm-account-hero-body">
          <p className="rm-account-name">{name}</p>
          <p className="rm-lead">{member.email}</p>
          <p className="rm-account-hero-tags">
            <StatusBadge label={role} tone="info" />
            <StatusBadge
              label={inactive ? t("console.team.inactive") : t("console.team.active")}
              tone={inactive ? "warning" : "success"}
            />
            {self ? <StatusBadge label={t("console.team.you")} tone="neutral" /> : null}
          </p>
        </div>
      </article>
      <div className="rm-details">
        <div>
          <span>{t("console.account.firstName")}</span>
          <strong>{firstName}</strong>
        </div>
        <div>
          <span>{t("console.account.lastName")}</span>
          <strong>{lastName}</strong>
        </div>
        <div>
          <span>{t("console.account.email")}</span>
          <strong>{member.email}</strong>
        </div>
        <div>
          <span>{t("console.account.role")}</span>
          <strong>{role}</strong>
        </div>
        <div>
          <span>{t("console.team.status")}</span>
          <strong>{inactive ? t("console.team.inactive") : t("console.team.active")}</strong>
        </div>
        <div>
          <span>{t("console.team.memberSince")}</span>
          <strong>{formatUtc(member.created_at, locale)}</strong>
        </div>
      </div>
    </>
  );
}

function isInteractiveTarget(target: EventTarget | null): boolean {
  return target instanceof Element && Boolean(target.closest("button, select, option, a, input, textarea, label"));
}
