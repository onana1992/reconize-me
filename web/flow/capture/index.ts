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
/** Match `.rm-camera[data-kind="selfie"] .rm-camera-cutout` insets. */
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
  const minSharpness = kind === "selfie" ? MIN_SHARPNESS_SELFIE : MIN_SHARPNESS_DOCUMENT;
  if (kind === "selfie" && contrast < MIN_CONTRAST) {
    reasons.push("face_not_framed");
  }
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
  return { mean, contrast, sharpness: sharpestTile(luma, width, height) };
}

/** Une carte pâle reste envoyée quand une bande, le code ou la MRZ, est nette. */
function sharpestTile(luma: Float64Array, width: number, height: number): number {
  const tiles = 4;
  let best = 0;
  const tileW = Math.max(1, Math.floor(width / tiles));
  const tileH = Math.max(1, Math.floor(height / tiles));
  for (let ty = 0; ty < tiles; ty += 1) {
    const y0 = ty * tileH;
    const y1 = ty === tiles - 1 ? height : Math.min(height, y0 + tileH);
    for (let tx = 0; tx < tiles; tx += 1) {
      const x0 = tx * tileW;
      const x1 = tx === tiles - 1 ? width : Math.min(width, x0 + tileW);
      let edge = 0;
      let samples = 0;
      for (let y = Math.max(1, y0); y < Math.min(height - 1, y1); y += 1) {
        for (let x = Math.max(1, x0); x < Math.min(width - 1, x1); x += 1) {
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
      if (samples > 0) {
        best = Math.max(best, edge / samples);
      }
    }
  }
  return best;
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
