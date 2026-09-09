"use client";

import { useMemo, useState } from "react";
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
  const [detailFilter, setDetailFilter] = useState("");

  const rows = useMemo(
    () =>
      events.map((event) => {
        const actorLabel = event.actor_id
          ? (emails[event.actor_id] ?? event.actor_id.slice(0, 8))
          : t("console.activity.system");
        const resourceLabel = `${tResourceType(t, event.resource_type)} · ${event.resource_id.slice(0, 8)}`;
        return {
          event,
          whenLabel: formatUtc(event.created_at, locale),
          actionLabel: tAuditAction(t, event.action),
          actorLabel,
          resourceLabel,
          detailLabel: payloadSummary(event.payload, t),
        };
      }),
    [emails, events, locale, t],
  );

  const filtered = useMemo(() => {
    const when = whenFilter.trim().toLowerCase();
    const actor = actorFilter.trim().toLowerCase();
    const resource = resourceFilter.trim().toLowerCase();
    const detail = detailFilter.trim().toLowerCase();
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
      if (detail && !row.detailLabel.toLowerCase().includes(detail)) {
        return false;
      }
      return true;
    });
  }, [actionFilter, actorFilter, detailFilter, resourceFilter, rows, whenFilter]);

  const pageCount = Math.max(1, Math.ceil(filtered.length / pageSize));
  const currentPage = Math.min(page, pageCount);
  const from = filtered.length === 0 ? 0 : (currentPage - 1) * pageSize + 1;
  const to = Math.min(currentPage * pageSize, filtered.length);
  const pageRows = filtered.slice((currentPage - 1) * pageSize, currentPage * pageSize);
  const filtersActive = Boolean(
    whenFilter || actionFilter || actorFilter || resourceFilter || detailFilter,
  );

  function resetPage() {
    setPage(1);
  }

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
    setDetailFilter("");
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
              <th>{t("console.activity.when")}</th>
              <th>{t("console.activity.action")}</th>
              <th>{t("console.activity.actor")}</th>
              <th>{t("console.activity.resource")}</th>
              <th>{t("console.activity.detail")}</th>
            </tr>
            <tr className="rm-rich-table-filters">
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
                  value={detailFilter}
                  onChange={(event) => {
                    setDetailFilter(event.target.value);
                    resetPage();
                  }}
                  aria-label={t("console.activity.filterDetail")}
                  placeholder={t("console.activity.filterDetail")}
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
                <tr key={row.event.id}>
                  <td>{row.whenLabel}</td>
                  <td>{row.actionLabel}</td>
                  <td>{row.actorLabel}</td>
                  <td>
                    <code>{row.resourceLabel}</code>
                  </td>
                  <td>{row.detailLabel}</td>
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
