export type Assessment = {
  usable: boolean;
  reasons: string[];
};

const MIN_SHORT_SIDE = 720;
const DARK = 40;
const BRIGHT = 230;
const MIN_CONTRAST = 18;
const MIN_SHARPNESS = 12;

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
  const { mean, contrast, sharpness } = metrics(image);
  if (mean < DARK) {
    reasons.push("too_dark");
  }
  if (mean > BRIGHT) {
    reasons.push("too_bright");
  }
  if (contrast < MIN_CONTRAST) {
    reasons.push(kind === "selfie" ? "face_not_framed" : "too_blurry");
  }
  if (sharpness < MIN_SHARPNESS) {
    reasons.push("too_blurry");
  }
  return { usable: reasons.length === 0, reasons: [...new Set(reasons)] };
}

function metrics(image: ImageData): { mean: number; contrast: number; sharpness: number } {
  const { data, width, height } = image;
  let sum = 0;
  let count = 0;
  let edge = 0;
  const luma: number[] = new Array(width * height);
  for (let y = 0; y < height; y += 1) {
    for (let x = 0; x < width; x += 1) {
      const i = (y * width + x) * 4;
      const value = 0.299 * data[i] + 0.587 * data[i + 1] + 0.114 * data[i + 2];
      luma[y * width + x] = value;
      sum += value;
      count += 1;
    }
  }
  const mean = count === 0 ? 0 : sum / count;
  let variance = 0;
  for (const value of luma) {
    const d = value - mean;
    variance += d * d;
  }
  const contrast = count === 0 ? 0 : Math.sqrt(variance / count);
  for (let y = 1; y < height - 1; y += 2) {
    for (let x = 1; x < width - 1; x += 2) {
      const c = luma[y * width + x];
      const lap =
        Math.abs(c - luma[y * width + (x - 1)]) +
        Math.abs(c - luma[y * width + (x + 1)]) +
        Math.abs(c - luma[(y - 1) * width + x]) +
        Math.abs(c - luma[(y + 1) * width + x]);
      edge += lap / 4;
    }
  }
  const samples = Math.max(1, Math.floor(((width - 2) * (height - 2)) / 4));
  return { mean, contrast, sharpness: edge / samples };
}
