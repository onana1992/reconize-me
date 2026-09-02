/*
 * Logo Recogniz-Me : sceau (tuile + cercle) + R dont la jambe est une coche.
 * Le sceau dit « reconnu / vérifié », pas « surveillé » : ni œil, ni empreinte.
 * Les couleurs viennent des tokens, jamais d'un hex passé en prop.
 */

export type LogoTone = "accent" | "inverse" | "mono";

const TILE: Record<LogoTone, string> = {
  accent: "var(--rm-accent)",
  inverse: "var(--rm-surface)",
  mono: "transparent",
};

const GLYPH: Record<LogoTone, string> = {
  accent: "var(--rm-text-on-accent)",
  inverse: "var(--rm-accent)",
  mono: "currentColor",
};

export type LogoProps = {
  /** Hauteur et largeur du sceau, en pixels. Lisible dès 16. */
  size?: number;
  tone?: LogoTone;
  /** Titre accessible. Omis quand le logo accompagne déjà le nom en texte. */
  title?: string;
};

/** P géométrique + coche à la place de la jambe — un seul path, viewBox 32. */
const GLYPH_PATH =
  "M10.7 22.15L10.7 9.55A6.45 3.8 0 0 1 10.7 17.15M12.35 17.05L15.45 21.35L22.05 11.95";

export function Logo({ size = 28, tone = "accent", title }: LogoProps) {
  const tile = TILE[tone];
  const glyph = GLYPH[tone];

  return (
    <svg
      className="rm-logo-mark"
      width={size}
      height={size}
      viewBox="0 0 32 32"
      role={title ? "img" : "presentation"}
      aria-hidden={title ? undefined : true}
      aria-label={title}
    >
      <rect
        width="32"
        height="32"
        rx="9"
        fill={tile}
        stroke={tone === "mono" ? "currentColor" : "none"}
        strokeWidth={tone === "mono" ? 1.5 : 0}
      />
      <circle cx="16" cy="16" r="10.5" fill="none" stroke={glyph} strokeOpacity="0.35" strokeWidth="1.5" />
      <path
        d={GLYPH_PATH}
        fill="none"
        stroke={glyph}
        strokeWidth="2.6"
        strokeLinecap="round"
        strokeLinejoin="round"
      />
    </svg>
  );
}

export type WordmarkProps = LogoProps & {
  /** `mark` masque le nom : réservé aux espaces contraints (favicon, mobile). */
  variant?: "full" | "mark";
};

export function Wordmark({ size = 28, tone = "accent", variant = "full" }: WordmarkProps) {
  if (variant === "mark") {
    return <Logo size={size} tone={tone} title="Recogniz-Me" />;
  }

  return (
    <>
      <Logo size={size} tone={tone} />
      <span>Recogniz-Me</span>
    </>
  );
}
