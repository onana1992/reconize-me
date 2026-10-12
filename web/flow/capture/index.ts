export type Assessment = {
  usable: boolean;
  reasons: string[];
};

/** Floor for a usable still. Browser camera preview is often VGA; 720 rejected almost every live frame. */
const MIN_SHORT_SIDE = 480;
const DARK = 22;
const BRIGHT = 250;
const MIN_CONTRAST = 8;
const MIN_SHARPNESS_DOCUMENT = 4;
/** Faces are mostly smooth skin; document-level 4 rejected every live selfie. */
const MIN_SHARPNESS_SELFIE = 0.6;
/** Match `.rm-flow-stage[data-kind="selfie"] .rm-flow-guide` insets. */
const SELFIE_INSET_X = 0.22;
const SELFIE_INSET_Y = 0.16;
/** Sample ~this many pixels on the short side so 12MP stills are not scored as blurry. */
const ANALYSIS_SHORT_SIDE = 320;

export function assessDocumentFrame(image: ImageData): Assessment {
  return assess(image, "document");
}

export function assessSelfieFrame(image: ImageData): Assessment {
  return assess(image, "selfie");
}

function assess(image: ImageData, kind: "document" | "selfie"): Assessment {
  const reasons: string[] = [];
  const shortSide = Math.min(image.width, image.height);
  if (shortSide < MIN_SHORT_SIDE) {
    reasons.push("too_small");
  }
  const { mean, contrast, sharpness } = metrics(image, kind);
  if (mean < DARK) {
    reasons.push("too_dark");
  }
  if (mean > BRIGHT) {
    reasons.push("too_bright");
  }
  if (contrast < MIN_CONTRAST) {
    reasons.push(kind === "selfie" ? "face_not_framed" : "too_blurry");
  }
  const minSharpness = kind === "selfie" ? MIN_SHARPNESS_SELFIE : MIN_SHARPNESS_DOCUMENT;
  if (sharpness < minSharpness) {
    reasons.push("too_blurry");
  }
  return { usable: reasons.length === 0, reasons: [...new Set(reasons)] };
}

function metrics(image: ImageData, kind: "document" | "selfie"): { mean: number; contrast: number; sharpness: number } {
  const insetX = kind === "selfie" ? SELFIE_INSET_X : 0;
  const insetY = kind === "selfie" ? SELFIE_INSET_Y : 0;
  const { luma, width, height } = sampleLuma(image, insetX, insetY);
  const count = luma.length;
  if (count === 0) {
    return { mean: 0, contrast: 0, sharpness: 0 };
  }
  let sum = 0;
  for (const value of luma) {
    sum += value;
  }
  const mean = sum / count;
  let variance = 0;
  for (const value of luma) {
    const d = value - mean;
    variance += d * d;
  }
  const contrast = Math.sqrt(variance / count);
  let edge = 0;
  let samples = 0;
  for (let y = 1; y < height - 1; y += 1) {
    for (let x = 1; x < width - 1; x += 1) {
      const c = luma[y * width + x];
      const lap =
        Math.abs(c - luma[y * width + (x - 1)]) +
        Math.abs(c - luma[y * width + (x + 1)]) +
        Math.abs(c - luma[(y - 1) * width + x]) +
        Math.abs(c - luma[(y + 1) * width + x]);
      edge += lap / 4;
      samples += 1;
    }
  }
  return { mean, contrast, sharpness: samples === 0 ? 0 : edge / samples };
}

function sampleLuma(
  image: ImageData,
  insetX: number,
  insetY: number,
): { luma: Float64Array; width: number; height: number } {
  const srcW = image.width;
  const srcH = image.height;
  const data = image.data;
  const x0 = Math.floor(srcW * insetX);
  const y0 = Math.floor(srcH * insetY);
  const regionW = Math.max(1, srcW - 2 * x0);
  const regionH = Math.max(1, srcH - 2 * y0);
  const step = Math.max(1, Math.round(Math.min(regionW, regionH) / ANALYSIS_SHORT_SIDE));
  const width = Math.max(1, Math.floor(regionW / step));
  const height = Math.max(1, Math.floor(regionH / step));
  const luma = new Float64Array(width * height);
  for (let gy = 0; gy < height; gy += 1) {
    const y = Math.min(srcH - 1, y0 + gy * step);
    for (let gx = 0; gx < width; gx += 1) {
      const x = Math.min(srcW - 1, x0 + gx * step);
      const i = (y * srcW + x) * 4;
      luma[gy * width + gx] = 0.299 * data[i] + 0.587 * data[i + 1] + 0.114 * data[i + 2];
    }
  }
  return { luma, width, height };
}
