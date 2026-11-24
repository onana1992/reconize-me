"use client";

import Link from "next/link";
import { useMemo, useState } from "react";
import { StatusBadge } from "@/brand";
import { useLocale, useT } from "../../../../i18n/client";
import type { Enrollment } from "../../../../lib/api";
import { formatUtc } from "../../../../lib/status";
import { RevokeEnrollment } from "./revoke-button";

const PAGE_SIZES = [10, 25, 50] as const;
const STATUSES = ["active", "retired", "revoked"] as const;

export function EnrollmentsTable({ items, canRevoke }: { items: Enrollment[]; canRevoke: boolean }) {
  const t = useT();
  const locale = useLocale();
  const [page, setPage] = useState(1);
  const [pageSize, setPageSize] = useState<(typeof PAGE_SIZES)[number]>(10);
  const [referenceFilter, setReferenceFilter] = useState("");
  const [statusFilter, setStatusFilter] = useState("");
  const [sourceFilter, setSourceFilter] = useState("");
  const [whenFilter, setWhenFilter] = useState("");
  const [idFilter, setIdFilter] = useState("");

  const rows = useMemo(
    () =>
      items.map((item) => ({
        item,
        reference: item.external_id?.trim() || "—",
        statusLabel: enrollmentStatusLabel(t, item.status),
        sourceLabel: sourceLabel(t, item.source),
        whenLabel: item.created_at ? formatUtc(item.created_at, locale) : "—",
        idLabel: item.id ?? "",
      })),
    [items, locale, t],
  );

  const filtered = useMemo(() => {
    const reference = referenceFilter.trim().toLowerCase();
    const source = sourceFilter.trim().toLowerCase();
    const when = whenFilter.trim().toLowerCase();
    const id = idFilter.trim().toLowerCase();
    return rows.filter((row) => {
      if (reference && !row.reference.toLowerCase().includes(reference)) {
        return false;
      }
      if (statusFilter && row.item.status !== statusFilter) {
        return false;
      }
      if (source && !row.sourceLabel.toLowerCase().includes(source) && !(row.item.source ?? "").toLowerCase().includes(source)) {
        return false;
      }
      if (when && !row.whenLabel.toLowerCase().includes(when) && !(row.item.created_at ?? "").toLowerCase().includes(when)) {
        return false;
      }
      if (id && !row.idLabel.toLowerCase().includes(id)) {
        return false;
      }
      return true;
    });
  }, [idFilter, referenceFilter, rows, sourceFilter, statusFilter, whenFilter]);

  const pageCount = Math.max(1, Math.ceil(filtered.length / pageSize));
  const currentPage = Math.min(page, pageCount);
  const from = filtered.length === 0 ? 0 : (currentPage - 1) * pageSize + 1;
  const to = Math.min(currentPage * pageSize, filtered.length);
  const pageRows = filtered.slice((currentPage - 1) * pageSize, currentPage * pageSize);
  const filtersActive = Boolean(referenceFilter || statusFilter || sourceFilter || whenFilter || idFilter);

  function resetPage() {
    setPage(1);
  }

  function clearFilters() {
    setReferenceFilter("");
    setStatusFilter("");
    setSourceFilter("");
    setWhenFilter("");
    setIdFilter("");
    setPage(1);
  }

  return (
    <div className="rm-rich-table">
      <div className="rm-table-wrap">
        <table className="rm-table">
          <thead>
            <tr>
              <th>{t("console.authentications.reference")}</th>
              <th>{t("console.authentications.statusLabel")}</th>
              <th>{t("console.authentications.source")}</th>
              <th>{t("console.authentications.createdAt")}</th>
              <th>{t("console.authentications.id")}</th>
              <th></th>
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
              <th>
                <select
                  value={statusFilter}
                  aria-label={t("console.authentications.filterStatus")}
                  onChange={(event) => {
                    setStatusFilter(event.target.value);
                    resetPage();
                  }}
                >
                  <option value="">{t("console.activity.all")}</option>
                  {STATUSES.map((value) => (
                    <option key={value} value={value}>
                      {enrollmentStatusLabel(t, value)}
                    </option>
                  ))}
                </select>
              </th>
              <th>
                <input
                  type="search"
                  value={sourceFilter}
                  onChange={(event) => {
                    setSourceFilter(event.target.value);
                    resetPage();
                  }}
                  aria-label={t("console.authentications.filterSource")}
                  placeholder={t("console.authentications.filterSource")}
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
                  aria-label={t("console.authentications.filterWhen")}
                  placeholder={t("console.authentications.filterWhen")}
                />
              </th>
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
              <th></th>
            </tr>
          </thead>
          <tbody>
            {pageRows.length === 0 ? (
              <tr>
                <td colSpan={6} className="rm-rich-table-empty">
                  {items.length === 0
                    ? t("console.authentications.enrollmentsEmpty")
                    : t("console.authentications.enrollmentsEmptyFiltered")}
                </td>
              </tr>
            ) : (
              pageRows.map((row) => (
                <tr key={row.item.id ?? row.reference}>
                  <td>{row.reference}</td>
                  <td>
                    <StatusBadge label={row.statusLabel} tone={enrollmentTone(row.item.status)} />
                  </td>
                  <td>
                    {row.item.source_verification_id ? (
                      <Link href={`/identity/verifications/${row.item.source_verification_id}`}>{row.sourceLabel}</Link>
                    ) : (
                      row.sourceLabel
                    )}
                  </td>
                  <td>{row.whenLabel}</td>
                  <td>
                    <code>{row.idLabel || "—"}</code>
                  </td>
                  <td>
                    {canRevoke && row.item.status === "active" && row.item.id ? <RevokeEnrollment id={row.item.id} /> : null}
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
          <button type="button" data-variant="secondary" onClick={clearFilters}>
            {t("console.activity.clear")}
          </button>
        ) : null}
      </div>
    </div>
  );
}

function enrollmentStatusLabel(t: ReturnType<typeof useT>, status: string): string {
  switch (status) {
    case "active":
      return t("console.authentications.enrollmentStatus.active");
    case "retired":
      return t("console.authentications.enrollmentStatus.retired");
    case "revoked":
      return t("console.authentications.enrollmentStatus.revoked");
    default:
      return status;
  }
}

function enrollmentTone(status: string): "success" | "danger" | "neutral" {
  if (status === "active") {
    return "success";
  }
  if (status === "revoked") {
    return "danger";
  }
  return "neutral";
}

function sourceLabel(t: ReturnType<typeof useT>, source?: string | null): string {
  if (!source) {
    return "—";
  }
  if (source === "idv") {
    return t("console.authentications.sourceIdv");
  }
  return source;
}
