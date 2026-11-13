"use client";

import { useMemo, useState } from "react";
import type { Messages } from "../lib/messages";

/*
 * Couverture — compteurs, recherche, filtres région, tableau.
 */

export function HomeCoverage({ copy }: { copy: Messages["home"] }) {
  const [region, setRegion] = useState(copy.coverageFilters[0]?.key ?? "all");
  const [query, setQuery] = useState("");

  const rows = useMemo(() => {
    const needle = query.trim().toLowerCase();
    return copy.coverageRows.filter((row) => {
      if (region !== "all" && row.region !== region) {
        return false;
      }
      if (!needle) {
        return true;
      }
      return (
        row.country.toLowerCase().includes(needle) ||
        row.document.toLowerCase().includes(needle) ||
        row.reading.toLowerCase().includes(needle)
      );
    });
  }, [copy.coverageRows, query, region]);

  return (
    <section className="rm-band" data-tone="paper" aria-labelledby="home-coverage-title">
      <div className="rm-shell">
        <div className="rm-band-head">
          <h2 id="home-coverage-title" className="rm-title">
            {copy.coverageTitle}
          </h2>
          <p className="rm-lead">{copy.coverageLead}</p>
        </div>

        <ul className="rm-coverage-metrics">
          {copy.coverageMetrics.map((metric) => (
            <li key={metric.label}>
              <span className="rm-coverage-metric-value">{metric.value}</span>
              <span className="rm-coverage-metric-label">{metric.label}</span>
            </li>
          ))}
        </ul>

        <div className="rm-coverage-toolbar">
          <label className="rm-coverage-search">
            <span className="rm-sr-only">{copy.coverageSearchLabel}</span>
            <input
              type="search"
              value={query}
              onChange={(event) => setQuery(event.target.value)}
              placeholder={copy.coverageSearchPlaceholder}
              autoComplete="off"
            />
          </label>

          <div className="rm-coverage-filters" role="group" aria-label={copy.coverageFiltersLabel}>
            {copy.coverageFilters.map((filter) => (
              <button
                key={filter.key}
                type="button"
                className="rm-coverage-filter"
                aria-pressed={region === filter.key}
                onClick={() => setRegion(filter.key)}
              >
                {filter.label}
              </button>
            ))}
          </div>
        </div>

        {rows.length === 0 ? (
          <p className="rm-coverage-empty">{copy.coverageEmpty}</p>
        ) : (
          <div className="rm-coverage-table-wrap">
            <table className="rm-coverage-table">
              <thead>
                <tr>
                  <th scope="col">{copy.coverageColumns.country}</th>
                  <th scope="col">{copy.coverageColumns.document}</th>
                  <th scope="col">{copy.coverageColumns.reading}</th>
                </tr>
              </thead>
              <tbody>
                {rows.map((row) => (
                  <tr key={`${row.country}-${row.document}`}>
                    <th scope="row">{row.country}</th>
                    <td data-label={copy.coverageColumns.document}>{row.document}</td>
                    <td data-label={copy.coverageColumns.reading}>{row.reading}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </div>
    </section>
  );
}
