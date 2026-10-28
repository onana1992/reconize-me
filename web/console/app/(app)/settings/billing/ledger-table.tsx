"use client";

import { useMemo, useState } from "react";
import { useLocale, useT } from "../../../../i18n/client";
import type { LedgerEntry } from "../../../../lib/api";
import { formatMinor } from "../../../../lib/pricing";
import { formatUtc } from "../../../../lib/status";
import { listLedgerAction } from "../actions";

const PAGE_SIZES = [10, 25, 50] as const;

export function LedgerTable({
  initialEntries,
  initialCursor,
}: {
  initialEntries: LedgerEntry[];
  initialCursor: string | null;
}) {
  const t = useT();
  const locale = useLocale();
  const [entries, setEntries] = useState(initialEntries);
  const [cursor, setCursor] = useState(initialCursor);
  const [pending, setPending] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [page, setPage] = useState(1);
  const [pageSize, setPageSize] = useState<(typeof PAGE_SIZES)[number]>(10);

  const rows = useMemo(
    () =>
      entries.map((entry) => ({
        entry,
        whenLabel: formatUtc(entry.created_at, locale),
        typeLabel: entry.entry_type === "topup" ? t("console.billing.entryTopup") : t("console.billing.entryDebit"),
        amountLabel: formatMinor(locale, entry.amount_minor),
        balanceLabel: formatMinor(locale, entry.balance_after_minor),
      })),
    [entries, locale, t],
  );

  const pageCount = Math.max(1, Math.ceil(rows.length / pageSize));
  const currentPage = Math.min(page, pageCount);
  const from = rows.length === 0 ? 0 : (currentPage - 1) * pageSize + 1;
  const to = Math.min(currentPage * pageSize, rows.length);
  const pageRows = rows.slice((currentPage - 1) * pageSize, currentPage * pageSize);

  async function loadMore() {
    if (!cursor) {
      return;
    }
    setPending(true);
    setError(null);
    const result = await listLedgerAction(cursor, 100);
    setPending(false);
    if (!result.ok) {
      setError(result.message || t("console.billing.loadError"));
      return;
    }
    setEntries((current) => mergeEntries(current, result.data.ledger.entries));
    setCursor(result.data.ledger.next_cursor);
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
              <th>{t("console.billing.when")}</th>
              <th>{t("console.billing.type")}</th>
              <th>{t("console.billing.amount")}</th>
              <th>{t("console.billing.balance")}</th>
            </tr>
          </thead>
          <tbody>
            {pageRows.map((row) => (
              <tr key={row.entry.id}>
                <td>{row.whenLabel}</td>
                <td>{row.typeLabel}</td>
                <td>{row.amountLabel}</td>
                <td>{row.balanceLabel}</td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
      <div className="rm-rich-table-foot">
        <p>
          {t("console.activity.showing")
            .replace("{from}", String(from))
            .replace("{to}", String(to))
            .replace("{total}", String(rows.length))}
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
        {cursor ? (
          <button type="button" data-variant="secondary" onClick={() => void loadMore()} disabled={pending}>
            {pending ? t("console.activity.loading") : t("console.billing.loadMore")}
          </button>
        ) : null}
      </div>
    </div>
  );
}

function mergeEntries(current: LedgerEntry[], incoming: LedgerEntry[]): LedgerEntry[] {
  const seen = new Set(current.map((entry) => entry.id));
  const next = [...current];
  for (const entry of incoming) {
    if (!seen.has(entry.id)) {
      next.push(entry);
    }
  }
  return next;
}
