/*
 * Badge de statut : le ton colore, le libellé informe.
 * Jamais d'information portée par la seule couleur (CDC §7.4).
 */

export type BadgeTone = "neutral" | "info" | "progress" | "success" | "warning" | "danger";

export type StatusBadgeProps = {
  label: string;
  tone?: BadgeTone;
};

export function StatusBadge({ label, tone = "neutral" }: StatusBadgeProps) {
  return (
    <span className="rm-badge" data-tone={tone}>
      {label}
    </span>
  );
}
