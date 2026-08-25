export type QualityIssue =
  | "blur"
  | "too_dark"
  | "too_bright"
  | "glare"
  | "cropped"
  | "too_small"
  | "low_resolution";

export interface CaptureQuality {
  usable: boolean;
  issues: QualityIssue[];
}

/**
 * Client-side quality gates before upload (Sprint 2).
 * Placeholder: always usable until camera heuristics land.
 */
export function assessDocumentFrame(_image: ImageData): CaptureQuality {
    return { usable: true, issues: [] };
}
