"use client";

import { useEffect, useMemo, useRef, useState } from "react";
import { useLocale, useT } from "../../../../i18n/client";
import type { AuditEvent } from "../../../../lib/api";
import { AUDIT_FILTERS, tAuditAction, tResourceType, tRole } from "../../../../lib/labels";
import { formatUtc } from "../../../../lib/status";
import { listAuditAction } from "../actions";

const PAGE_SIZES = [10, 25, 50] as const;

type Props = {
  initialEvents: AuditEvent[];
  initialCursor: string | null;
  emails: Record<string, string>;
};

export function ActivityTable({ initialEvents, initialCursor, emails }: Props) {
  const t = useT();
  const locale = useLocale();
  const dialogRef = useRef<HTMLDialogElement>(null);
  const [events, setEvents] = useState(initialEvents);
  const [cursor, setCursor] = useState(initialCursor);
  const [pending, setPending] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [page, setPage] = useState(1);
  const [pageSize, setPageSize] = useState<(typeof PAGE_SIZES)[number]>(10);
  const [whenFilter, setWhenFilter] = useState("");
  const [actionFilter, setActionFilter] = useState("");
  const [actorFilter, setActorFilter] = useState("");
  const [resourceFilter, setResourceFilter] = useState("");
  const [ipFilter, setIpFilter] = useState("");
  const [selectedId, setSelectedId] = useState<number | null>(null);

  const rows = useMemo(
    () =>
      events.map((event) => {
        const actorLabel = event.actor_id
          ? (emails[event.actor_id] ?? event.actor_id.slice(0, 8))
          : t("console.activity.system");
        const resourceLabel = `${tResourceType(t, event.resource_type)} · ${event.resource_id.slice(0, 8)}`;
        const ipLabel = event.ip_address?.trim() || "—";
        return {
          event,
          whenLabel: formatUtc(event.created_at, locale),
          actionLabel: tAuditAction(t, event.action),
          actorLabel,
          resourceLabel,
          ipLabel,
          detailLabel: payloadSummary(event.payload, t),
        };
      }),
    [emails, events, locale, t],
  );

  const filtered = useMemo(() => {
    const when = whenFilter.trim().toLowerCase();
    const actor = actorFilter.trim().toLowerCase();
    const resource = resourceFilter.trim().toLowerCase();
    const ip = ipFilter.trim().toLowerCase();
    return rows.filter((row) => {
      if (when && !row.whenLabel.toLowerCase().includes(when) && !row.event.created_at.toLowerCase().includes(when)) {
        return false;
      }
      if (actionFilter && row.event.action !== actionFilter) {
        return false;
      }
      if (actor && !row.actorLabel.toLowerCase().includes(actor)) {
        return false;
      }
      if (resource && !row.resourceLabel.toLowerCase().includes(resource)) {
        return false;
      }
      if (ip && !row.ipLabel.toLowerCase().includes(ip) && !(row.event.ip_address ?? "").toLowerCase().includes(ip)) {
        return false;
      }
      return true;
    });
  }, [actionFilter, actorFilter, ipFilter, resourceFilter, rows, whenFilter]);

  const pageCount = Math.max(1, Math.ceil(filtered.length / pageSize));
  const currentPage = Math.min(page, pageCount);
  const from = filtered.length === 0 ? 0 : (currentPage - 1) * pageSize + 1;
  const to = Math.min(currentPage * pageSize, filtered.length);
  const pageRows = filtered.slice((currentPage - 1) * pageSize, currentPage * pageSize);
  const filtersActive = Boolean(whenFilter || actionFilter || actorFilter || resourceFilter || ipFilter);
  const selected = selectedId == null ? null : (rows.find((row) => row.event.id === selectedId) ?? null);
  const selectedPayload = selected ? payloadPretty(selected.event.payload) : null;

  function resetPage() {
    setPage(1);
  }

  function openRow(id: number) {
    setSelectedId(id);
  }

  function closeDrawer() {
    dialogRef.current?.close();
  }

  useEffect(() => {
    const dialog = dialogRef.current;
    if (!dialog) {
      return;
    }
    if (selectedId == null) {
      if (dialog.open) {
        dialog.close();
      }
      return;
    }
    if (!dialog.open) {
      dialog.showModal();
    }
  }, [selectedId]);

  async function reload(nextAction: string) {
    setPending(true);
    setError(null);
    const result = await listAuditAction(nextAction || undefined, undefined, 100);
    setPending(false);
    if (!result.ok) {
      setError(result.message);
      return;
    }
    setEvents(result.data.events);
    setCursor(result.data.next_cursor);
    setPage(1);
  }

  async function loadMore() {
    if (!cursor) {
      return;
    }
    setPending(true);
    setError(null);
    const result = await listAuditAction(actionFilter || undefined, cursor, 100);
    setPending(false);
    if (!result.ok) {
      setError(result.message);
      return;
    }
    setEvents((current) => mergeEvents(current, result.data.events));
    setCursor(result.data.next_cursor);
  }

  function clearFilters() {
    setWhenFilter("");
    setActionFilter("");
    setActorFilter("");
    setResourceFilter("");
    setIpFilter("");
    setPage(1);
    void reload("");
  }

  return (
    <div className="rm-rich-table">
      {error ? (
        <p role="alert" className="rm-alert">
          {error}
        </p>
      ) : null}
      <div className="rm-table-wrap">
        <table className="rm-table">
          <thead>
            <tr>
              <th>{t("console.activity.actor")}</th>
              <th>{t("console.activity.when")}</th>
              <th>{t("console.activity.action")}</th>
              <th>{t("console.activity.resource")}</th>
              <th>{t("console.activity.ip")}</th>
            </tr>
            <tr className="rm-rich-table-filters">
              <th>
                <input
                  type="search"
                  value={actorFilter}
                  onChange={(event) => {
                    setActorFilter(event.target.value);
                    resetPage();
                  }}
                  aria-label={t("console.activity.filterActor")}
                  placeholder={t("console.activity.filterActor")}
                />
              </th>
              <th>
                <input
                  type="search"
                  value={whenFilter}
                  onChange={(event) => {
                    setWhenFilter(event.target.value);
                    resetPage();
                  }}
                  aria-label={t("console.activity.filterWhen")}
                  placeholder={t("console.activity.filterWhen")}
                />
              </th>
              <th>
                <select
                  value={actionFilter}
                  aria-label={t("console.activity.filterAction")}
                  onChange={(event) => {
                    const next = event.target.value;
                    setActionFilter(next);
                    void reload(next);
                  }}
                >
                  <option value="">{t("console.activity.all")}</option>
                  {AUDIT_FILTERS.map((value) => (
                    <option key={value} value={value}>
                      {tAuditAction(t, value)}
                    </option>
                  ))}
                </select>
              </th>
              <th>
                <input
                  type="search"
                  value={resourceFilter}
                  onChange={(event) => {
                    setResourceFilter(event.target.value);
                    resetPage();
                  }}
                  aria-label={t("console.activity.filterResource")}
                  placeholder={t("console.activity.filterResource")}
                />
              </th>
              <th>
                <input
                  type="search"
                  value={ipFilter}
                  onChange={(event) => {
                    setIpFilter(event.target.value);
                    resetPage();
                  }}
                  aria-label={t("console.activity.filterIp")}
                  placeholder={t("console.activity.filterIp")}
                />
              </th>
            </tr>
          </thead>
          <tbody>
            {pageRows.length === 0 ? (
              <tr>
                <td colSpan={5} className="rm-rich-table-empty">
                  {t("console.activity.empty")}
                </td>
              </tr>
            ) : (
              pageRows.map((row) => (
                <tr
                  key={row.event.id}
                  className="rm-table-row"
                  tabIndex={0}
                  aria-selected={selectedId === row.event.id}
                  aria-haspopup="dialog"
                  aria-label={`${row.actorLabel}, ${row.actionLabel}, ${row.whenLabel}`}
                  onClick={() => openRow(row.event.id)}
                  onKeyDown={(event) => {
                    if (event.key === "Enter" || event.key === " ") {
                      event.preventDefault();
                      openRow(row.event.id);
                    }
                  }}
                >
                  <td>{row.actorLabel}</td>
                  <td>{row.whenLabel}</td>
                  <td>{row.actionLabel}</td>
                  <td>
                    <code>{row.resourceLabel}</code>
                  </td>
                  <td>
                    <code>{row.ipLabel}</code>
                  </td>
                </tr>
              ))
            )}
          </tbody>
        </table>
      </div>
      <div className="rm-rich-table-foot">
        <p>
          {t("console.activity.showing")
            .replace("{from}", String(from))
            .replace("{to}", String(to))
            .replace("{total}", String(filtered.length))}
        </p>
        <label className="rm-rich-table-pagesize">
          <span>{t("console.activity.perPage")}</span>
          <select
            value={pageSize}
            onChange={(event) => {
              setPageSize(Number(event.target.value) as (typeof PAGE_SIZES)[number]);
              setPage(1);
            }}
          >
            {PAGE_SIZES.map((size) => (
              <option key={size} value={size}>
                {size}
              </option>
            ))}
          </select>
        </label>
        <div className="rm-pager">
          <button type="button" disabled={currentPage <= 1} onClick={() => setPage((value) => Math.max(1, value - 1))}>
            {t("console.activity.prev")}
          </button>
          <span>
            {t("console.activity.page")
              .replace("{page}", String(currentPage))
              .replace("{pages}", String(pageCount))}
          </span>
          <button
            type="button"
            disabled={currentPage >= pageCount}
            onClick={() => setPage((value) => Math.min(pageCount, value + 1))}
          >
            {t("console.activity.next")}
          </button>
        </div>
        {filtersActive ? (
          <button type="button" data-variant="secondary" onClick={clearFilters} disabled={pending}>
            {t("console.activity.clear")}
          </button>
        ) : null}
        {cursor ? (
          <button type="button" data-variant="secondary" onClick={() => void loadMore()} disabled={pending}>
            {pending ? t("console.activity.loading") : t("console.activity.loadMore")}
          </button>
        ) : null}
      </div>
      <dialog
        ref={dialogRef}
        className="rm-drawer"
        aria-labelledby="activity-drawer-title"
        onClose={() => setSelectedId(null)}
        onPointerDown={(event) => {
          if (event.target !== event.currentTarget) {
            return;
          }
          if (event.clientX === 0 && event.clientY === 0) {
            return;
          }
          const rect = event.currentTarget.getBoundingClientRect();
          const inside =
            event.clientX >= rect.left &&
            event.clientX <= rect.right &&
            event.clientY >= rect.top &&
            event.clientY <= rect.bottom;
          if (!inside) {
            closeDrawer();
          }
        }}
      >
        {selected ? (
          <>
            <div className="rm-drawer-head">
              <div>
                <p className="rm-drawer-kicker">{t("console.activity.eventDetail")}</p>
                <h2 id="activity-drawer-title">{selected.actionLabel}</h2>
              </div>
              <button type="button" className="rm-dialog-close" aria-label={t("common.close")} onClick={closeDrawer}>
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
            <dl className="rm-drawer-fields">
              <div>
                <dt>{t("console.activity.actor")}</dt>
                <dd>{selected.actorLabel}</dd>
              </div>
              <div>
                <dt>{t("console.activity.when")}</dt>
                <dd>{selected.whenLabel}</dd>
              </div>
              <div>
                <dt>{t("console.activity.action")}</dt>
                <dd>{selected.actionLabel}</dd>
              </div>
              <div>
                <dt>{t("console.activity.resource")}</dt>
                <dd>
                  <code>{selected.resourceLabel}</code>
                </dd>
              </div>
              <div>
                <dt>{t("console.activity.ip")}</dt>
                <dd>
                  <code>{selected.ipLabel}</code>
                </dd>
              </div>
              <div>
                <dt>{t("console.activity.detail")}</dt>
                <dd>{selected.detailLabel}</dd>
              </div>
              {selectedPayload ? (
                <div>
                  <dt>{t("console.activity.payload")}</dt>
                  <dd>
                    <pre className="rm-drawer-payload">{selectedPayload}</pre>
                  </dd>
                </div>
              ) : null}
            </dl>
          </>
        ) : null}
      </dialog>
    </div>
  );
}

function mergeEvents(current: AuditEvent[], incoming: AuditEvent[]): AuditEvent[] {
  const seen = new Set(current.map((event) => event.id));
  const next = [...current];
  for (const event of incoming) {
    if (!seen.has(event.id)) {
      next.push(event);
    }
  }
  return next;
}

function payloadSummary(
  payload: Record<string, unknown>,
  t: ReturnType<typeof useT>,
): string {
  const from = payload.from;
  const to = payload.to;
  if (typeof from === "string" && typeof to === "string") {
    return `${tRole(t, from)} → ${tRole(t, to)}`;
  }
  return "—";
}

function payloadPretty(payload: Record<string, unknown>): string | null {
  const keys = Object.keys(payload);
  if (keys.length === 0) {
    return null;
  }
  return JSON.stringify(payload, null, 2);
}
