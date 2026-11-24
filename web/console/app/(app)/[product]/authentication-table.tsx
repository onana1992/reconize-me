"use client";

import { useRouter } from "next/navigation";
import { useMemo, useState } from "react";
import { StatusBadge } from "@/brand";
import { useLocale, useT } from "../../../i18n/client";
import type { AuthenticationSession } from "../../../lib/api";
import { integrationModeTone, tIntegrationMode } from "../../../lib/environment";
import {
  AUTHENTICATION_STATUSES,
  authenticationTone,
  formatUtc,
  tAuthenticationStatus,
} from "../../../lib/status";
import { listAuthenticationsAction } from "./authentications/actions";

const PAGE_SIZES = [10, 25, 50] as const;

export function AuthenticationTable({
  initialItems,
  initialCursor,
  integrationId,
  integrationNames = {},
}: {
  initialItems: AuthenticationSession[];
  initialCursor: string | null;
  integrationId?: string;
  integrationNames?: Record<string, string>;
}) {
  const t = useT();
  const locale = useLocale();
  const router = useRouter();
  const [items, setItems] = useState(initialItems);
  const [cursor, setCursor] = useState(initialCursor);
  const [pending, setPending] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [page, setPage] = useState(1);
  const [pageSize, setPageSize] = useState<(typeof PAGE_SIZES)[number]>(10);
  const [referenceFilter, setReferenceFilter] = useState("");
  const [whenFilter, setWhenFilter] = useState("");
  const [statusFilter, setStatusFilter] = useState("");
  const [idFilter, setIdFilter] = useState("");

  const rows = useMemo(
    () =>
      items.map((item) => {
        const reference = item.external_id?.trim() || "—";
        const integrationLabel = (item.integration_id && integrationNames[item.integration_id]) || "—";
        return {
          item,
          reference,
          integrationLabel,
          whenLabel: formatUtc(item.created_at, locale),
          statusLabel: tAuthenticationStatus(t, item.status),
          engine: item.engine?.trim() || "—",
          idLabel: item.id,
        };
      }),
    [integrationNames, items, locale, t],
  );

  const filtered = useMemo(() => {
    const reference = referenceFilter.trim().toLowerCase();
    const when = whenFilter.trim().toLowerCase();
    const id = idFilter.trim().toLowerCase();
    return rows.filter((row) => {
      if (reference && !row.reference.toLowerCase().includes(reference)) {
        return false;
      }
      if (when && !row.whenLabel.toLowerCase().includes(when) && !row.item.created_at.toLowerCase().includes(when)) {
        return false;
      }
      if (id && !row.item.id.toLowerCase().includes(id)) {
        return false;
      }
      return true;
    });
  }, [idFilter, referenceFilter, rows, whenFilter]);

  const pageCount = Math.max(1, Math.ceil(filtered.length / pageSize));
  const currentPage = Math.min(page, pageCount);
  const from = filtered.length === 0 ? 0 : (currentPage - 1) * pageSize + 1;
  const to = Math.min(currentPage * pageSize, filtered.length);
  const pageRows = filtered.slice((currentPage - 1) * pageSize, currentPage * pageSize);
  const filtersActive = Boolean(referenceFilter || whenFilter || statusFilter || idFilter);

  function resetPage() {
    setPage(1);
  }

  function openRow(id: string) {
    router.push(`/authentication/verifications/${id}`);
  }

  async function reload(nextStatus: string) {
    setPending(true);
    setError(null);
    const result = await listAuthenticationsAction(nextStatus || undefined, undefined, 100, integrationId);
    setPending(false);
    if (!result.ok) {
      setError(result.message);
      return;
    }
    setItems(result.data.data);
    setCursor(result.data.next_cursor ?? null);
    setPage(1);
  }

  async function loadMore() {
    if (!cursor) {
      return;
    }
    setPending(true);
    setError(null);
    const result = await listAuthenticationsAction(statusFilter || undefined, cursor, 100, integrationId);
    setPending(false);
    if (!result.ok) {
      setError(result.message);
      return;
    }
    setItems((current) => mergeItems(current, result.data.data));
    setCursor(result.data.next_cursor ?? null);
  }

  function clearFilters() {
    setReferenceFilter("");
    setWhenFilter("");
    setStatusFilter("");
    setIdFilter("");
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
              <th>{t("console.authentications.reference")}</th>
              <th>{t("console.authentications.integration")}</th>
              <th>{t("console.authentications.createdAt")}</th>
              <th>{t("console.authentications.statusLabel")}</th>
              <th>{t("console.authentications.engine")}</th>
              <th>{t("console.authentications.id")}</th>
            </tr>
            <tr className="rm-rich-table-filters">
              <th>
                <input
                  type="search"
                  value={referenceFilter}
                  onChange={(event) => {
                    setReferenceFilter(event.target.value);
                    resetPage();
                  }}
                  aria-label={t("console.authentications.filterReference")}
                  placeholder={t("console.authentications.filterReference")}
                />
              </th>
              <th></th>
              <th>
                <input
                  type="search"
                  value={whenFilter}
                  onChange={(event) => {
                    setWhenFilter(event.target.value);
                    resetPage();
                  }}
                  aria-label={t("console.authentications.filterWhen")}
                  placeholder={t("console.authentications.filterWhen")}
                />
              </th>
              <th>
                <select
                  value={statusFilter}
                  aria-label={t("console.authentications.filterStatus")}
                  onChange={(event) => {
                    const next = event.target.value;
                    setStatusFilter(next);
                    void reload(next);
                  }}
                >
                  <option value="">{t("console.activity.all")}</option>
                  {AUTHENTICATION_STATUSES.map((value) => (
                    <option key={value} value={value}>
                      {tAuthenticationStatus(t, value)}
                    </option>
                  ))}
                </select>
              </th>
              <th></th>
              <th>
                <input
                  type="search"
                  value={idFilter}
                  onChange={(event) => {
                    setIdFilter(event.target.value);
                    resetPage();
                  }}
                  aria-label={t("console.authentications.filterId")}
                  placeholder={t("console.authentications.filterId")}
                />
              </th>
            </tr>
          </thead>
          <tbody>
            {pageRows.length === 0 ? (
              <tr>
                <td colSpan={6} className="rm-rich-table-empty">
                  {items.length === 0
                    ? t("console.authentications.empty")
                    : t("console.authentications.emptyFiltered")}
                </td>
              </tr>
            ) : (
              pageRows.map((row) => (
                <tr
                  key={row.item.id}
                  className="rm-table-row"
                  tabIndex={0}
                  aria-label={`${row.reference}, ${row.statusLabel}, ${row.whenLabel}`}
                  onClick={() => openRow(row.item.id)}
                  onKeyDown={(event) => {
                    if (event.key === "Enter" || event.key === " ") {
                      event.preventDefault();
                      openRow(row.item.id);
                    }
                  }}
                >
                  <td>{row.reference}</td>
                  <td>
                    <span className="rm-int-cell">
                      {row.integrationLabel}
                      <StatusBadge
                        label={tIntegrationMode(t, row.item.integration_mode)}
                        tone={integrationModeTone(row.item.integration_mode)}
                      />
                    </span>
                  </td>
                  <td>{row.whenLabel}</td>
                  <td>
                    <StatusBadge label={row.statusLabel} tone={authenticationTone(row.item.status)} />
                  </td>
                  <td>
                    <code>{row.engine}</code>
                  </td>
                  <td>
                    <code>{row.idLabel}</code>
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
            {pending ? t("console.activity.loading") : t("console.authentications.loadMore")}
          </button>
        ) : null}
      </div>
    </div>
  );
}

function mergeItems(current: AuthenticationSession[], incoming: AuthenticationSession[]): AuthenticationSession[] {
  const seen = new Set(current.map((item) => item.id));
  const next = [...current];
  for (const item of incoming) {
    if (!seen.has(item.id)) {
      next.push(item);
    }
  }
  return next;
}
