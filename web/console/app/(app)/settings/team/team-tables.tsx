"use client";

import { useRouter } from "next/navigation";
import { useState } from "react";
import { StatusBadge } from "@kyc/brand";
import { useLocale, useT } from "../../../../i18n/client";
import { formatUtc } from "../../../../lib/status";
import { ALL_ROLES, ASSIGNABLE_ROLES, tRole } from "../../../../lib/labels";
import type { Team } from "../../../../lib/api";
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
  const activeOwners = members.filter((member) => member.role === "owner" && member.status !== "disabled").length;
  const roleOptions = canOwn ? ALL_ROLES : ASSIGNABLE_ROLES;

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
                  <tr key={member.id}>
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
    </>
  );
}
