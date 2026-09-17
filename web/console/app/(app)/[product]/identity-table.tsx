"use client";

import { useRouter } from "next/navigation";
import { useMemo, useState } from "react";
import { StatusBadge } from "@kyc/brand";
import { useLocale, useT } from "../../../i18n/client";
import type { Verification } from "../../../lib/api";
import { integrationModeTone, tIntegrationMode } from "../../../lib/environment";
import { displayName } from "../../../lib/labels";
import { formatUtc, tVerificationStatus, verificationTone, VERIFICATION_STATUSES } from "../../../lib/status";
import { listVerificationsAction } from "./verifications/actions";

const PAGE_SIZES = [10, 25, 50] as const;

export function IdentityTable({
  initialItems,
  initialCursor,
  integrationId,
  integrationNames = {},
}: {
  initialItems: Verification[];
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
  const [applicantFilter, setApplicantFilter] = useState("");
  const [emailFilter, setEmailFilter] = useState("");
  const [whenFilter, setWhenFilter] = useState("");
  const [statusFilter, setStatusFilter] = useState("");
  const [idFilter, setIdFilter] = useState("");

  const rows = useMemo(
    () =>
      items.map((item) => {
        const applicant = displayName(item.applicant?.first_name, item.applicant?.last_name, item.applicant?.email ?? "")
          || t("console.verifications.untitled");
        const email = item.applicant?.email?.trim() || "—";
        const integrationLabel =
          (item.integration_id && integrationNames[item.integration_id]) || "—";
        return {
          item,
          applicant,
          email,
          integrationLabel,
          whenLabel: formatUtc(item.created_at, locale),
          statusLabel: tVerificationStatus(t, item.status),
          idLabel: item.id.slice(0, 8),
        };
      }),
    [integrationNames, items, locale, t],
  );

  const filtered = useMemo(() => {
    const applicant = applicantFilter.trim().toLowerCase();
    const email = emailFilter.trim().toLowerCase();
    const when = whenFilter.trim().toLowerCase();
    const id = idFilter.trim().toLowerCase();
    return rows.filter((row) => {
      if (applicant && !row.applicant.toLowerCase().includes(applicant)) {
        return false;
      }
      if (email && !row.email.toLowerCase().includes(email) && !(row.item.applicant?.email ?? "").toLowerCase().includes(email)) {
        return false;
      }
      if (when && !row.whenLabel.toLowerCase().includes(when) && !row.item.created_at.toLowerCase().includes(when)) {
        return false;
      }
      if (id && !row.item.id.toLowerCase().includes(id) && !row.idLabel.toLowerCase().includes(id)) {
        return false;
      }
      return true;
    });
  }, [applicantFilter, emailFilter, idFilter, rows, whenFilter]);

  const pageCount = Math.max(1, Math.ceil(filtered.length / pageSize));
  const currentPage = Math.min(page, pageCount);
  const from = filtered.length === 0 ? 0 : (currentPage - 1) * pageSize + 1;
  const to = Math.min(currentPage * pageSize, filtered.length);
  const pageRows = filtered.slice((currentPage - 1) * pageSize, currentPage * pageSize);
  const filtersActive = Boolean(applicantFilter || emailFilter || whenFilter || statusFilter || idFilter);

  function resetPage() {
    setPage(1);
  }

  function openRow(id: string) {
    router.push(`/identity/verifications/${id}`);
  }

  async function reload(nextStatus: string) {
    setPending(true);
    setError(null);
    const result = await listVerificationsAction(nextStatus || undefined, undefined, 100, integrationId);
    setPending(false);
    if (!result.ok) {
      setError(result.message);
      return;
    }
    setItems(result.data.items);
    setCursor(result.data.next_cursor ?? null);
    setPage(1);
  }

  async function loadMore() {
    if (!cursor) {
      return;
    }
    setPending(true);
    setError(null);
    const result = await listVerificationsAction(statusFilter || undefined, cursor, 100, integrationId);
    setPending(false);
    if (!result.ok) {
      setError(result.message);
      return;
    }
    setItems((current) => mergeItems(current, result.data.items));
    setCursor(result.data.next_cursor ?? null);
  }

  function clearFilters() {
    setApplicantFilter("");
    setEmailFilter("");
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
              <th>{t("console.verifications.applicant")}</th>
              <th>{t("console.verifications.email")}</th>
              <th>{t("console.verifications.integration")}</th>
              <th>{t("console.verifications.createdAt")}</th>
              <th>{t("console.verifications.statusLabel")}</th>
              <th>{t("console.verifications.id")}</th>
            </tr>
            <tr className="rm-rich-table-filters">
              <th>
                <input
                  type="search"
                  value={applicantFilter}
                  onChange={(event) => {
                    setApplicantFilter(event.target.value);
                    resetPage();
                  }}
                  aria-label={t("console.verifications.filterApplicant")}
                  placeholder={t("console.verifications.filterApplicant")}
                />
              </th>
              <th>
                <input
                  type="search"
                  value={emailFilter}
                  onChange={(event) => {
                    setEmailFilter(event.target.value);
                    resetPage();
                  }}
                  aria-label={t("console.verifications.filterEmail")}
                  placeholder={t("console.verifications.filterEmail")}
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
                  aria-label={t("console.verifications.filterWhen")}
                  placeholder={t("console.verifications.filterWhen")}
                />
              </th>
              <th>
                <select
                  value={statusFilter}
                  aria-label={t("console.verifications.filterStatus")}
                  onChange={(event) => {
                    const next = event.target.value;
                    setStatusFilter(next);
                    void reload(next);
                  }}
                >
                  <option value="">{t("console.activity.all")}</option>
                  {VERIFICATION_STATUSES.map((value) => (
                    <option key={value} value={value}>
                      {tVerificationStatus(t, value)}
                    </option>
                  ))}
                </select>
              </th>
              <th>
                <input
                  type="search"
                  value={idFilter}
                  onChange={(event) => {
                    setIdFilter(event.target.value);
                    resetPage();
                  }}
                  aria-label={t("console.verifications.filterId")}
                  placeholder={t("console.verifications.filterId")}
                />
              </th>
            </tr>
          </thead>
          <tbody>
            {pageRows.length === 0 ? (
              <tr>
                <td colSpan={6} className="rm-rich-table-empty">
                  {items.length === 0 ? t("console.verifications.empty") : t("console.verifications.emptyFiltered")}
                </td>
              </tr>
            ) : (
              pageRows.map((row) => (
                <tr
                  key={row.item.id}
                  className="rm-table-row"
                  tabIndex={0}
                  aria-label={`${row.applicant}, ${row.statusLabel}, ${row.whenLabel}`}
                  onClick={() => openRow(row.item.id)}
                  onKeyDown={(event) => {
                    if (event.key === "Enter" || event.key === " ") {
                      event.preventDefault();
                      openRow(row.item.id);
                    }
                  }}
                >
                  <td>{row.applicant}</td>
                  <td>{row.email}</td>
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
                    <StatusBadge label={row.statusLabel} tone={verificationTone(row.item.status)} />
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
            {pending ? t("console.activity.loading") : t("console.verifications.loadMore")}
          </button>
        ) : null}
      </div>
    </div>
  );
}

function mergeItems(current: Verification[], incoming: Verification[]): Verification[] {
  const seen = new Set(current.map((item) => item.id));
  const next = [...current];
  for (const item of incoming) {
    if (!seen.has(item.id)) {
      next.push(item);
    }
  }
  return next;
}
