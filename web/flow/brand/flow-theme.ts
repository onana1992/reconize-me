import type { CSSProperties } from "react";

/**
 * Thème du parcours candidat. Chaque valeur peut être remplacée au build par
 * une variable NEXT_PUBLIC_FLOW_* sans toucher au code des écrans.
 */
export type FlowTheme = {
  /** Nom affiché à côté du logo dans l'en-tête. */
  name: string;
  accent: string;
  accentHover: string;
  accentActive: string;
  accentSubtle: string;
  /** Fond du cadre caméra et des surfaces sombres. */
  stage: string;
  /** Rayon des cartes et du cadre caméra, en pixels. */
  radius: number;
};

export const flowTheme: FlowTheme = {
  name: process.env.NEXT_PUBLIC_FLOW_NAME || "Recogniz-Me",
  accent: process.env.NEXT_PUBLIC_FLOW_ACCENT || "#0f7a4d",
  accentHover: process.env.NEXT_PUBLIC_FLOW_ACCENT_HOVER || "#0b6340",
  accentActive: process.env.NEXT_PUBLIC_FLOW_ACCENT_ACTIVE || "#084d32",
  accentSubtle: process.env.NEXT_PUBLIC_FLOW_ACCENT_SUBTLE || "#eef7f2",
  stage: process.env.NEXT_PUBLIC_FLOW_STAGE || "#08130f",
  radius: Number(process.env.NEXT_PUBLIC_FLOW_RADIUS || 18),
};

export function flowThemeStyle(theme: FlowTheme = flowTheme): CSSProperties {
  return {
    "--rm-accent": theme.accent,
    "--rm-accent-hover": theme.accentHover,
    "--rm-accent-active": theme.accentActive,
    "--rm-accent-subtle": theme.accentSubtle,
    "--rm-link": theme.accentHover,
    "--rm-focus": theme.accentActive,
    "--rm-flow-stage": theme.stage,
    "--rm-flow-radius": `${theme.radius}px`,
  } as CSSProperties;
}
