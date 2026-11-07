import { StatusBadge } from "@/brand";
import type { getT } from "../../../../../i18n";
import type { Verification } from "../../../../../lib/api";

type Translate = Awaited<ReturnType<typeof getT>>;
type Face = NonNullable<Verification["authenticity"]>[number];

const ALERTS = [
  "splicing",
  "text_alteration",
  "compression_inconsistency",
  "screen_replay",
  "printed_copy",
] as const;

const REVIEW_AT = 0.55;
const STRONG_AT = 0.8;

export function AuthenticityReport({ faces, t }: { faces: Face[]; t: Translate }) {
  const ordered = [...faces].sort((left, right) => sideRank(left.side) - sideRank(right.side));
  return (
    <section className="rm-vcase-card">
      <h2>{t("console.verifications.authenticityTitle")}</h2>
      <p className="rm-hint">{t("console.verifications.authenticityLead")}</p>
      <div className="rm-vcase-auth" data-count={ordered.length === 1 ? "1" : undefined}>
        {ordered.map((face) => (
          <FaceReport key={face.side} face={face} t={t} />
        ))}
      </div>
    </section>
  );
}

function FaceReport({ face, t }: { face: Face; t: Translate }) {
  const alerts = ALERTS.map((code) => face.alerts?.find((alert) => alert.code === code) ?? { code });
  return (
    <div>
      <h3>{sideLabel(t, face.side)}</h3>
      {face.forensic_usable === false ? (
        <p className="rm-vcase-empty">{t("console.verifications.authenticityUnusable")}</p>
      ) : (
        <>
          <dl className="rm-vcase-list">
            <Row label={t("console.verifications.authenticityScore")} value={formatScore(face.authenticity_score)} />
            <Row label={t("console.verifications.tamperingScore")} value={formatScore(face.tampering_score)} />
            <Row label={t("console.verifications.presentationScore")} value={formatScore(face.presentation_score)} />
            <Row label={t("console.verifications.engineVersion")} value={face.engine_version?.trim() || "—"} />
          </dl>
          <ul className="rm-vcase-checks">
            {alerts.map((alert) => {
              const band = bandOf(alert.score);
              return (
                <li key={alert.code} data-outcome={bandOutcome(band)}>
                  <span className="rm-vcase-check-dot" aria-hidden="true" />
                  <span className="rm-vcase-check-name">
                    {alertLabel(t, alert.code)}
                    <small>
                      {[familyLabel(t, alert.family), alert.score == null ? "" : formatScore(alert.score)]
                        .filter((part) => part.length > 0)
                        .join(" · ")}
                    </small>
                  </span>
                  <StatusBadge label={t(`console.verifications.authenticityBands.${band}`)} tone={bandTone(band)} />
                </li>
              );
            })}
          </ul>
        </>
      )}
    </div>
  );
}

function Row({ label, value }: { label: string; value: string }) {
  return (
    <div>
      <dt>{label}</dt>
      <dd>{value}</dd>
    </div>
  );
}

function sideRank(side: string): number {
  if (side === "FRONT") return 0;
  if (side === "BACK") return 1;
  return 2;
}

function sideLabel(t: Translate, side: string): string {
  if (side === "FRONT") return t("console.verifications.authenticityFront");
  if (side === "BACK") return t("console.verifications.authenticityBack");
  return side;
}

function alertLabel(t: Translate, code: string): string {
  switch (code) {
    case "splicing":
    case "text_alteration":
    case "compression_inconsistency":
    case "screen_replay":
    case "printed_copy":
      return t(`console.verifications.authenticityAlerts.${code}`);
    default:
      return code;
  }
}

function familyLabel(t: Translate, family?: string | null): string {
  if (family === "digital_forgery" || family === "presentation_spoof") {
    return t(`console.verifications.authenticityFamilies.${family}`);
  }
  return family?.trim() || "";
}

function bandOf(score?: number | null): "none" | "clear" | "review" | "strong" {
  if (score == null || Number.isNaN(score)) return "none";
  if (score >= STRONG_AT) return "strong";
  if (score >= REVIEW_AT) return "review";
  return "clear";
}

function bandOutcome(band: ReturnType<typeof bandOf>): string {
  if (band === "strong") return "fail";
  if (band === "review") return "unavailable";
  if (band === "clear") return "pass";
  return "unavailable";
}

function bandTone(band: ReturnType<typeof bandOf>): "success" | "danger" | "warning" | "neutral" {
  if (band === "strong") return "danger";
  if (band === "review") return "warning";
  if (band === "clear") return "success";
  return "neutral";
}

function formatScore(score?: number | null): string {
  if (score == null || Number.isNaN(score)) return "—";
  return score.toFixed(3);
}
