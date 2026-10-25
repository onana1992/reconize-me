"use client";

import { useRouter } from "next/navigation";
import { useMemo, useState } from "react";
import { StatusBadge } from "@/brand";
import { useLocale, useT } from "../../../i18n/client";
import type { Screening } from "../../../lib/api";
import { formatUtc } from "../../../lib/status";
import { listScreeningsAction } from "./aml-actions";
import { outcomeLabel, outcomeTone } from "./aml-labels";

const PAGE_SIZES = [10, 25, 50] as const;
const OUTCOMES = ["clear", "potential_match", "confirmed_match", "failed"] as const;

export function AmlTable({
  initialItems,
  initialCursor,
}: {
  initialItems: Screening[];
  initialCursor: string | null;
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
  const [subjectFilter, setSubjectFilter] = useState("");
  const [outcomeFilter, setOutcomeFilter] = useState("");
  const [whenFilter, setWhenFilter] = useState("");
  const [idFilter, setIdFilter] = useState("");

  const rows = useMemo(
    () =>
      items.map((item) => {
        const subject = personName(item) || t("console.verifications.untitled");
        const outcome = item.status === "failed" ? "failed" : item.outcome ?? item.status;
        return {
          item,
          subject,
          outcome,
          outcomeLabel: outcomeLabel(t, outcome),
          whenLabel: formatUtc(item.created_at, locale),
          idLabel: item.id.slice(0, 8),
        };
      }),
    [items, locale, t],
  );

  const filtered = useMemo(() => {
    const subject = subjectFilter.trim().toLowerCase();
    const when = whenFilter.trim().toLowerCase();
    const id = idFilter.trim().toLowerCase();
    return rows.filter((row) => {
      if (subject && !row.subject.toLowerCase().includes(subject)) {
        return false;
      }
      if (outcomeFilter && row.outcome !== outcomeFilter) {
        return false;
      }
      if (when && !row.whenLabel.toLowerCase().includes(when)) {
        return false;
      }
      if (id && !row.item.id.toLowerCase().includes(id) && !row.idLabel.toLowerCase().includes(id)) {
        return false;
      }
      return true;
    });
  }, [idFilter, outcomeFilter, rows, subjectFilter, whenFilter]);

  const pageCount = Math.max(1, Math.ceil(filtered.length / pageSize));
  const currentPage = Math.min(page, pageCount);
  const from = filtered.length === 0 ? 0 : (currentPage - 1) * pageSize + 1;
  const to = Math.min(currentPage * pageSize, filtered.length);
  const pageRows = filtered.slice((currentPage - 1) * pageSize, currentPage * pageSize);
  const filtersActive = Boolean(subjectFilter || outcomeFilter || whenFilter || idFilter);

  function openRow(id: string) {
    router.push(`/aml/verifications/${id}`);
  }

  async function loadMore() {
    if (!cursor) {
      return;
    }
    setPending(true);
    setError(null);
    const result = await listScreeningsAction(cursor);
    setPending(false);
    if (!result.ok) {
      setError(result.message);
      return;
    }
    setItems((current) => merge(current, result.data.data));
    setCursor(result.data.next_cursor ?? null);
  }

  function clearFilters() {
    setSubjectFilter("");
    setOutcomeFilter("");
    setWhenFilter("");
    setIdFilter("");
    setPage(1);
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
              <th>{t("console.product.amlSubject")}</th>
              <th>{t("console.product.amlOutcome")}</th>
              <th>{t("console.product.amlHits")}</th>
              <th>{t("console.verifications.createdAt")}</th>
              <th>{t("console.verifications.id")}</th>
            </tr>
            <tr className="rm-rich-table-filters">
              <th>
                <input
                  type="search"
                  value={subjectFilter}
                  onChange={(event) => {
                    setSubjectFilter(event.target.value);
                    setPage(1);
                  }}
                  aria-label={t("console.product.amlFilterSubject")}
                  placeholder={t("console.product.amlFilterSubject")}
                />
              </th>
              <th>
                <select
                  value={outcomeFilter}
                  aria-label={t("console.product.amlFilterOutcome")}
                  onChange={(event) => {
                    setOutcomeFilter(event.target.value);
                    setPage(1);
                  }}
                >
                  <option value="">{t("console.product.amlAllOutcomes")}</option>
                  {OUTCOMES.map((value) => (
                    <option key={value} value={value}>
                      {outcomeLabel(t, value)}
                    </option>
                  ))}
                </select>
              </th>
              <th></th>
              <th>
                <input
                  type="search"
                  value={whenFilter}
                  onChange={(event) => {
                    setWhenFilter(event.target.value);
                    setPage(1);
                  }}
                  aria-label={t("console.verifications.filterWhen")}
                  placeholder={t("console.verifications.filterWhen")}
                />
              </th>
              <th>
                <input
                  type="search"
                  value={idFilter}
                  onChange={(event) => {
                    setIdFilter(event.target.value);
                    setPage(1);
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
                <td colSpan={5} className="rm-rich-table-empty">
                  {items.length === 0 ? t("console.product.amlEmpty") : t("console.verifications.emptyFiltered")}
                </td>
              </tr>
            ) : (
              pageRows.map((row) => (
                <tr
                  key={row.item.id}
                  className="rm-table-row"
                  tabIndex={0}
                  aria-label={`${row.subject}, ${row.outcomeLabel}, ${row.whenLabel}`}
                  onClick={() => openRow(row.item.id)}
                  onKeyDown={(event) => {
                    if (event.key === "Enter" || event.key === " ") {
                      event.preventDefault();
                      openRow(row.item.id);
                    }
                  }}
                >
                  <td>{row.subject}</td>
                  <td>
                    <StatusBadge label={row.outcomeLabel} tone={outcomeTone(row.outcome)} />
                  </td>
                  <td>{row.item.hit_count}</td>
                  <td>{row.whenLabel}</td>
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
          <button type="button" data-variant="secondary" onClick={clearFilters}>
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

function personName(item: Screening): string {
  return [item.subject?.first_name, item.subject?.middle_name, item.subject?.last_name].filter(Boolean).join(" ");
}

function merge(current: Screening[], incoming: Screening[]): Screening[] {
  const seen = new Set(current.map((item) => item.id));
  const next = [...current];
  for (const item of incoming) {
    if (!seen.has(item.id)) {
      next.push(item);
    }
  }
  return next;
}
