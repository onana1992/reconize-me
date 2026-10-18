"use client";

import { assessDocumentFrame, assessSelfieFrame } from "@/capture";
import dynamic from "next/dynamic";
import { useCallback, useEffect, useRef, useState, type ChangeEvent } from "react";
import { FlowShell, Notice, StateScreen, type FlowStep } from "../../../components/flow-shell";
import {
  BarcodeIcon,
  ClockIcon,
  CornersIcon,
  FaceIcon,
  GlassesIcon,
  IdCardIcon,
  LinkOffIcon,
  ShieldIcon,
  SparkOffIcon,
  SunIcon,
  UploadIcon,
  WifiOffIcon,
} from "../../../components/icons";
import { useT, type MessageKey } from "../../../i18n/client";

const LivenessCheck = dynamic(() => import("./liveness-check"), { ssr: false });

type Session = {
  verification_id: string;
  status: string;
  consent_text_version: string;
  expires_at: string;
  next: string;
};

type CaptureNext = "capture_document" | "capture_document_back" | "capture_selfie";

const BUSY_HINTS: MessageKey[] = ["capture.uploading", "capture.analyzing"];

export function FlowClient({ token }: { token: string }) {
  const t = useT();
  const [session, setSession] = useState<Session | null>(null);
  const [error, setError] = useState<"invalid" | "expired" | "network" | null>(null);
  const [busy, setBusy] = useState(false);
  const [hint, setHint] = useState<MessageKey | null>(null);
  const [liveCamera, setLiveCamera] = useState(false);
  const [refused, setRefused] = useState(false);
  const videoRef = useRef<HTMLVideoElement>(null);
  const streamRef = useRef<MediaStream | null>(null);

  useEffect(() => {
    setLiveCamera(Boolean(navigator.mediaDevices?.getUserMedia));
  }, []);

  const load = useCallback(async () => {
    try {
      const response = await fetch(`/v1/flow/${encodeURIComponent(token)}`, { cache: "no-store" });
      if (response.status === 404) {
        setError("invalid");
        return;
      }
      if (response.status === 410) {
        setError("expired");
        return;
      }
      if (!response.ok) {
        setError("invalid");
        return;
      }
      setError(null);
      setSession((await response.json()) as Session);
    } catch {
      setError("network");
    }
  }, [token]);

  useEffect(() => {
    void load();
  }, [load]);

  const facing = facingFor(session?.next);
  const facingRef = useRef(facing);
  facingRef.current = facing;

  useEffect(() => {
    if (!facing || !liveCamera) {
      stopCamera();
      return;
    }
    const current = streamRef.current?.getVideoTracks()[0];
    if (current && current.readyState === "live" && trackMatchesFacing(current, facing)) {
      bindStream(videoRef.current, streamRef.current);
      return;
    }
    let cancelled = false;
    stopCamera();
    openCaptureStream(facing)
      .then((stream) => {
        if (cancelled || facingRef.current !== facing) {
          stream.getTracks().forEach((track) => track.stop());
          return;
        }
        streamRef.current = stream;
        bindStream(videoRef.current, stream);
      })
      .catch(() => {
        if (!cancelled) {
          setHint("capture.cameraError");
        }
      });
    return () => {
      cancelled = true;
      if (facingRef.current !== facing) {
        stopCamera();
      }
    };
  }, [liveCamera, facing]);

  useEffect(() => () => stopCamera(), []);

  useEffect(() => {
    if (session?.next !== "wait") {
      return;
    }
    const id = window.setInterval(() => void load(), 1500);
    return () => window.clearInterval(id);
  }, [session?.next, load]);

  function stopCamera() {
    streamRef.current?.getTracks().forEach((track) => track.stop());
    streamRef.current = null;
  }

  async function consent(decision: "accepted" | "declined") {
    setBusy(true);
    setHint(null);
    try {
      const query = new URLSearchParams({ decision });
      const response = await fetch(`/v1/flow/${encodeURIComponent(token)}/consent?${query}`, {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ decision }),
        cache: "no-store",
      });
      if (response.status === 409) {
        await load();
        return;
      }
      if (!response.ok) {
        setHint("consent.error");
        return;
      }
      const body = (await response.json()) as { status: string; next: string };
      setRefused(decision === "declined");
      setSession((current) => (current ? { ...current, status: body.status, next: body.next } : current));
    } catch {
      setError("network");
    } finally {
      setBusy(false);
    }
  }

  async function submitFrame(kind: "selfie" | "document", image: ImageData, blob: Blob) {
    const assessment = kind === "selfie" ? assessSelfieFrame(image) : assessDocumentFrame(image);
    if (!assessment.usable) {
      setHint(hintForReasons(assessment.reasons));
      setBusy(false);
      return;
    }

    setBusy(true);
    setHint("capture.uploading");
    try {
      const prefix = kind === "selfie" ? "selfie" : "document";
      const upload = await fetch(`/v1/flow/${encodeURIComponent(token)}/${prefix}/uploads`, {
        method: "POST",
      });
      if (!upload.ok) {
        setHint("capture.uploadError");
        return;
      }
      const signed = (await upload.json()) as { upload_url: string; attempt: number };
      const put = await fetch(sameOriginApiUrl(signed.upload_url), {
        method: "PUT",
        headers: { "Content-Type": "image/jpeg" },
        body: blob,
      });
      if (!put.ok) {
        setHint("capture.uploadError");
        return;
      }
      setHint("capture.analyzing");
      const complete = await fetch(`/v1/flow/${encodeURIComponent(token)}/${prefix}/complete`, {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ attempt: signed.attempt }),
      });
      if (!complete.ok) {
        setHint("capture.uploadError");
        return;
      }
      const result = (await complete.json()) as {
        status: string;
        next: string;
        accepted: boolean;
        reason?: string;
      };
      if (!result.accepted) {
        setHint(hintForServerReason(result.reason));
      } else {
        setHint(null);
      }
      setSession((current) =>
        current ? { ...current, status: result.status, next: result.next } : current,
      );
    } catch {
      setError("network");
    } finally {
      setBusy(false);
    }
  }

  async function capture() {
    if (busy) {
      return;
    }
    setBusy(true);
    const kind = session?.next === "capture_selfie" ? "selfie" : "document";
    bindStream(videoRef.current, streamRef.current);
    const video = videoRef.current;
    if (video?.paused) {
      try {
        await video.play();
      } catch {
        /* la frame suivante est lue depuis le canvas si la lecture est refusée */
      }
    }
    const track = streamRef.current?.getVideoTracks()[0];
    const still = track && track.readyState === "live" ? await stillFromTrack(track) : null;
    if (still) {
      await submitFrame(kind, still.image, still.blob);
      return;
    }
    if (!video || video.videoWidth === 0) {
      setHint("capture.cameraError");
      setBusy(false);
      return;
    }
    const canvas = document.createElement("canvas");
    const scale = Math.min(1, PHOTO_LONG_SIDE / Math.max(video.videoWidth, video.videoHeight));
    canvas.width = Math.max(1, Math.round(video.videoWidth * scale));
    canvas.height = Math.max(1, Math.round(video.videoHeight * scale));
    const ctx = canvas.getContext("2d");
    if (!ctx) {
      setHint("capture.cameraError");
      setBusy(false);
      return;
    }
    ctx.imageSmoothingEnabled = true;
    ctx.imageSmoothingQuality = "high";
    ctx.drawImage(video, 0, 0, canvas.width, canvas.height);
    try {
      const { image, blob } = await frameFromCanvas(canvas);
      await submitFrame(kind, image, blob);
    } catch {
      setHint("capture.cameraError");
      setBusy(false);
    }
  }

  async function onPick(event: ChangeEvent<HTMLInputElement>) {
    const file = event.target.files?.[0];
    event.target.value = "";
    if (!file) {
      return;
    }
    const kind = session?.next === "capture_selfie" ? "selfie" : "document";
    try {
      const { image, blob } = await frameFromFile(file);
      await submitFrame(kind, image, blob);
    } catch {
      setHint("capture.decodeError");
    }
  }

  if (error) {
    return (
      <FlowShell step={null}>
        <ErrorScreen kind={error} onRetry={() => void load()} />
      </FlowShell>
    );
  }

  if (!session) {
    return (
      <FlowShell step={null}>
        <StateScreen tone="progress" title={t("shell.loading")} />
      </FlowShell>
    );
  }

  const next = session.next;

  if (next === "consent") {
    return (
      <FlowShell step="consent">
        <ConsentScreen busy={busy} hint={hint} onDecide={(decision) => void consent(decision)} />
      </FlowShell>
    );
  }

  if (next === "capture_document" || next === "capture_document_back" || next === "capture_selfie") {
    return (
      <FlowShell step={stepFor(next)}>
        <CaptureScreen
          next={next}
          busy={busy}
          hint={hint}
          liveCamera={liveCamera}
          videoRef={videoRef}
          onCapture={() => void capture()}
          onPick={(event) => void onPick(event)}
        />
      </FlowShell>
    );
  }

  if (next === "liveness") {
    return (
      <FlowShell step="face">
        <LivenessCheck token={token} onDone={() => void load()} />
      </FlowShell>
    );
  }

  if (next === "wait") {
    return (
      <FlowShell step="done">
        <StateScreen tone="progress" title={t("outcome.wait")} lead={t("outcome.waitLead")} />
      </FlowShell>
    );
  }

  if (session.status === "declined" && refused) {
    return (
      <FlowShell step={null}>
        <StateScreen tone="neutral" icon={<ShieldIcon size={30} />} title={t("outcome.declined")} lead={t("outcome.declinedLead")} />
      </FlowShell>
    );
  }

  if (session.status === "declined") {
    return (
      <FlowShell step="done">
        <StateScreen tone="neutral" icon={<ShieldIcon size={30} />} title={t("outcome.closed")} lead={t("outcome.closedLead")} />
      </FlowShell>
    );
  }

  return (
    <FlowShell step="done">
      <StateScreen tone="success" title={t("outcome.done")} lead={t("outcome.doneLead")} />
    </FlowShell>
  );
}

function stepFor(next: CaptureNext): FlowStep {
  return next === "capture_selfie" ? "face" : "document";
}

function ErrorScreen({ kind, onRetry }: { kind: "invalid" | "expired" | "network"; onRetry: () => void }) {
  const t = useT();
  if (kind === "expired") {
    return <StateScreen tone="danger" icon={<ClockIcon size={30} />} title={t("errors.expired")} lead={t("errors.expiredLead")} />;
  }
  if (kind === "invalid") {
    return <StateScreen tone="danger" icon={<LinkOffIcon size={30} />} title={t("errors.invalid")} lead={t("errors.invalidLead")} />;
  }
  return (
    <StateScreen tone="danger" icon={<WifiOffIcon size={30} />} title={t("errors.network")} lead={t("errors.networkLead")}>
      <div className="rm-flow-cta">
        <button type="button" className="rm-btn-primary" onClick={onRetry}>
          {t("errors.retry")}
        </button>
      </div>
    </StateScreen>
  );
}

function ConsentScreen({
  busy,
  hint,
  onDecide,
}: {
  busy: boolean;
  hint: MessageKey | null;
  onDecide: (decision: "accepted" | "declined") => void;
}) {
  const t = useT();
  return (
    <div className="rm-screen">
      <div className="rm-hero-badge" aria-hidden="true">
        <ShieldIcon size={30} />
      </div>
      <p className="rm-eyebrow">{t("consent.eyebrow")}</p>
      <h1>{t("consent.title")}</h1>
      <p className="rm-lead">{t("consent.lead")}</p>
      <ol className="rm-plan">
        <li>
          <span className="rm-plan-icon" aria-hidden="true">
            <IdCardIcon size={22} />
          </span>
          <span>
            <strong>{t("consent.stepDocument")}</strong>
            <span>{t("consent.stepDocumentLead")}</span>
          </span>
        </li>
        <li>
          <span className="rm-plan-icon" aria-hidden="true">
            <FaceIcon size={22} />
          </span>
          <span>
            <strong>{t("consent.stepFace")}</strong>
            <span>{t("consent.stepFaceLead")}</span>
          </span>
        </li>
      </ol>
      <p className="rm-consent-text">{t("consent.body")}</p>
      {hint ? <Notice tone="danger">{t(hint)}</Notice> : null}
      <form
        className="rm-flow-cta"
        onSubmit={(event) => {
          event.preventDefault();
          onDecide("accepted");
        }}
      >
        <button type="submit" className="rm-btn-primary" disabled={busy}>
          {busy ? <span className="rm-spinner" data-size="sm" aria-hidden="true" /> : null}
          {t("consent.accept")}
        </button>
        <button type="button" className="rm-btn-ghost" disabled={busy} onClick={() => onDecide("declined")}>
          {t("consent.decline")}
        </button>
      </form>
    </div>
  );
}

function CaptureScreen({
  next,
  busy,
  hint,
  liveCamera,
  videoRef,
  onCapture,
  onPick,
}: {
  next: CaptureNext;
  busy: boolean;
  hint: MessageKey | null;
  liveCamera: boolean;
  videoRef: React.RefObject<HTMLVideoElement | null>;
  onCapture: () => void;
  onPick: (event: ChangeEvent<HTMLInputElement>) => void;
}) {
  const t = useT();
  const selfie = next === "capture_selfie";
  const copy = selfie ? "selfie" : next === "capture_document_back" ? "documentBack" : "document";
  const tips = tipsFor(next);
  const busyHint = busy && hint !== null && BUSY_HINTS.includes(hint) ? hint : null;
  const working = busyHint !== null;

  return (
    <div className="rm-screen">
      <p className="rm-eyebrow">{t(`${copy}.eyebrow` as const)}</p>
      <h1>{t(`${copy}.title` as const)}</h1>
      <p className="rm-lead">{t(`${copy}.lead` as const)}</p>
      <ul className="rm-tips">
        {tips.map((tip) => (
          <li key={tip.label}>
            {tip.icon}
            <span>{t(tip.label)}</span>
          </li>
        ))}
      </ul>
      <div className="rm-camera" data-kind={selfie ? "selfie" : "document"}>
        <div className="rm-camera-stage" role="img" aria-label={t(`${copy}.frame` as const)}>
          {liveCamera ? (
            <video ref={videoRef} autoPlay playsInline muted />
          ) : (
            <span className="rm-camera-placeholder" aria-hidden="true">
              {selfie ? <FaceIcon size={44} /> : <IdCardIcon size={44} />}
            </span>
          )}
          <span className="rm-camera-cutout" aria-hidden="true">
            <span className="rm-camera-scan" />
          </span>
          {busyHint ? (
            <span className="rm-camera-busy" role="status">
              <span className="rm-spinner" data-tone="inverse" aria-hidden="true" />
              <strong>{t(busyHint)}</strong>
              {busyHint === "capture.analyzing" ? <span>{t("capture.analyzingLead")}</span> : null}
            </span>
          ) : null}
        </div>
        <div className="rm-camera-controls">
          {liveCamera ? (
            <button
              type="button"
              className="rm-shutter"
              disabled={busy}
              aria-label={t("capture.action")}
              onClick={onCapture}
            >
              <span aria-hidden="true" />
            </button>
          ) : (
            <label className="rm-btn-primary rm-file-pick">
              <UploadIcon size={18} />
              {t("capture.pick")}
              <input
                type="file"
                accept="image/*"
                capture={selfie ? "user" : "environment"}
                disabled={busy}
                onChange={onPick}
              />
            </label>
          )}
          {liveCamera ? <span className="rm-camera-caption">{t("capture.action")}</span> : null}
        </div>
      </div>
      {hint && !working ? <Notice tone="danger">{t(hint)}</Notice> : null}
    </div>
  );
}

function tipsFor(next: CaptureNext): { icon: React.ReactNode; label: MessageKey }[] {
  if (next === "capture_selfie") {
    return [
      { icon: <FaceIcon size={16} />, label: "selfie.tipFace" },
      { icon: <SunIcon size={16} />, label: "selfie.tipLight" },
      { icon: <GlassesIcon size={16} />, label: "selfie.tipGlasses" },
    ];
  }
  if (next === "capture_document_back") {
    return [
      { icon: <BarcodeIcon size={16} />, label: "documentBack.tipBarcode" },
      { icon: <SparkOffIcon size={16} />, label: "document.tipGlare" },
      { icon: <CornersIcon size={16} />, label: "document.tipCorners" },
    ];
  }
  return [
    { icon: <SunIcon size={16} />, label: "document.tipLight" },
    { icon: <SparkOffIcon size={16} />, label: "document.tipGlare" },
    { icon: <CornersIcon size={16} />, label: "document.tipCorners" },
  ];
}

function hintForServerReason(reason: string | undefined): MessageKey {
  switch (reason) {
    case "TOO_SMALL":
      return "capture.tooSmall";
    case "TOO_LARGE":
      return "capture.tooLarge";
    case "LOW_LIGHT":
      return "capture.tooDark";
    case "OVEREXPOSED":
      return "capture.tooBright";
    case "INVALID_IMAGE":
      return "capture.decodeError";
    case "IMAGE_TOO_BLURRY":
      return "capture.recapture";
    case "DOCUMENT_SIDE_MISMATCH":
      return "capture.wrongSide";
    case "DOCUMENT_MISMATCH":
      return "capture.mismatch";
    case "document_expired":
      return "capture.expired";
    case "unsupported_document":
      return "capture.unsupported";
    case "DOCUMENT_UNKNOWN":
      return "capture.unknown";
    case "MISSING_REQUIRED_FIELD":
    case "LOW_OCR_CONFIDENCE":
    case "LOW_CONFIDENCE":
    case "MRZ_MISMATCH":
      return "capture.unreadable";
    case "provider_unavailable":
      return "capture.provider";
    default:
      return "capture.recapture";
  }
}

function hintForReasons(reasons: string[]): MessageKey {
  if (reasons.includes("too_small")) {
    return "capture.tooSmall";
  }
  if (reasons.includes("too_dark")) {
    return "capture.tooDark";
  }
  if (reasons.includes("too_bright")) {
    return "capture.tooBright";
  }
  if (reasons.includes("face_not_framed")) {
    return "capture.faceNotFramed";
  }
  return "capture.recapture";
}

const SERVER_MIN_SHORT_SIDE = 720;

function frameFromFile(file: File): Promise<{ image: ImageData; blob: Blob }> {
  return new Promise((resolve, reject) => {
    const url = URL.createObjectURL(file);
    const img = new Image();
    img.onload = () => {
      const canvas = document.createElement("canvas");
      const scale = Math.min(1, PHOTO_LONG_SIDE / Math.max(img.naturalWidth, img.naturalHeight));
      canvas.width = Math.max(1, Math.round(img.naturalWidth * scale));
      canvas.height = Math.max(1, Math.round(img.naturalHeight * scale));
      const ctx = canvas.getContext("2d");
      URL.revokeObjectURL(url);
      if (!ctx) {
        reject(new Error("canvas"));
        return;
      }
      ctx.drawImage(img, 0, 0, canvas.width, canvas.height);
      void frameFromCanvas(canvas).then(resolve, reject);
    };
    img.onerror = () => {
      URL.revokeObjectURL(url);
      reject(new Error("image"));
    };
    img.src = url;
  });
}

const PHOTO_LONG_SIDE = 2560;

function highResVideo(facing: "user" | "environment"): MediaTrackConstraints {
  return {
    facingMode: { ideal: facing },
    width: { ideal: 3840 },
    height: { ideal: 2160 },
  };
}

async function openCaptureStream(facing: "user" | "environment"): Promise<MediaStream> {
  const stream = await navigator.mediaDevices.getUserMedia({ video: highResVideo(facing) });
  if (facing === "user") {
    await raiseTrack(stream);
    return stream;
  }
  const currentId = stream.getVideoTracks()[0]?.getSettings().deviceId;
  const devices = await navigator.mediaDevices.enumerateDevices().catch(() => []);
  const mainRear = pickMainRear(devices, currentId);
  if (!mainRear) {
    await raiseTrack(stream);
    return stream;
  }
  stream.getTracks().forEach((track) => track.stop());
  try {
    const rear = await navigator.mediaDevices.getUserMedia({
      video: { ...highResVideo(facing), deviceId: { exact: mainRear } },
    });
    await raiseTrack(rear);
    return rear;
  } catch {
    const fallback = await navigator.mediaDevices.getUserMedia({ video: highResVideo(facing) });
    await raiseTrack(fallback);
    return fallback;
  }
}

function pickMainRear(devices: MediaDeviceInfo[], currentId: string | undefined): string | undefined {
  if (!devices.some((device) => device.kind === "videoinput" && device.label)) {
    return undefined;
  }
  const rear = devices.filter(
    (device) => device.kind === "videoinput" && /back|rear|environment|arrière|arriere/i.test(device.label),
  );
  const main = rear.find((device) => !/ultra|wide|0\.5|depth|tele/i.test(device.label));
  const chosen = main ?? rear[0];
  if (!chosen || chosen.deviceId === currentId) {
    return undefined;
  }
  return chosen.deviceId;
}

async function raiseTrack(stream: MediaStream): Promise<void> {
  const track = stream.getVideoTracks()[0];
  if (!track?.getCapabilities) {
    return;
  }
  const caps = track.getCapabilities() as MediaTrackCapabilities & {
    focusMode?: string[];
    width?: { max?: number };
    height?: { max?: number };
  };
  const width = caps.width?.max && caps.width.max > 0 ? caps.width.max : 3840;
  const height = caps.height?.max && caps.height.max > 0 ? caps.height.max : 2160;
  const advanced: MediaTrackConstraintSet[] = [];
  if (caps.focusMode?.includes("continuous")) {
    advanced.push({ focusMode: "continuous" } as MediaTrackConstraintSet);
  }
  try {
    await track.applyConstraints({
      width: { ideal: width },
      height: { ideal: height },
      advanced,
    });
  } catch {
    /* le flux déjà ouvert reste utilisable */
  }
}

function facingFor(next: string | undefined): "user" | "environment" | null {
  if (next === "capture_selfie") {
    return "user";
  }
  if (next === "capture_document" || next === "capture_document_back") {
    return "environment";
  }
  return null;
}

function trackMatchesFacing(track: MediaStreamTrack, facing: "user" | "environment"): boolean {
  const mode = track.getSettings().facingMode;
  return mode == null || mode === "" || mode === facing;
}

function bindStream(video: HTMLVideoElement | null, stream: MediaStream | null) {
  if (!video || !stream) {
    return;
  }
  if (video.srcObject !== stream) {
    video.srcObject = stream;
  }
  if (video.paused) {
    void video.play().catch(() => undefined);
  }
}

async function stillFromTrack(track: MediaStreamTrack): Promise<{ image: ImageData; blob: Blob } | null> {
  const Ctor = (window as unknown as { ImageCapture?: new (media: MediaStreamTrack) => { takePhoto: () => Promise<Blob> } })
    .ImageCapture;
  if (!Ctor) {
    return null;
  }
  try {
    const photo = new Ctor(track).takePhoto();
    const blob = await Promise.race([
      photo,
      new Promise<null>((resolve) => {
        window.setTimeout(() => resolve(null), 2800);
      }),
    ]);
    if (!blob) {
      return null;
    }
    const file = new File([blob], "capture.jpg", { type: blob.type || "image/jpeg" });
    return await frameFromFile(file);
  } catch {
    return null;
  }
}

function frameFromCanvas(canvas: HTMLCanvasElement): Promise<{ image: ImageData; blob: Blob }> {
  const ctx = canvas.getContext("2d");
  if (!ctx) {
    return Promise.reject(new Error("canvas"));
  }
  const image = ctx.getImageData(0, 0, canvas.width, canvas.height);
  const upload = ensureMinShortSide(canvas, SERVER_MIN_SHORT_SIDE);
  return new Promise((resolve, reject) => {
    upload.toBlob((blob) => (blob ? resolve({ image, blob }) : reject(new Error("blob"))), "image/jpeg", 0.92);
  });
}

function ensureMinShortSide(source: HTMLCanvasElement, min: number): HTMLCanvasElement {
  const short = Math.min(source.width, source.height);
  if (short >= min) {
    return source;
  }
  const scale = min / short;
  const out = document.createElement("canvas");
  out.width = Math.max(1, Math.round(source.width * scale));
  out.height = Math.max(1, Math.round(source.height * scale));
  const ctx = out.getContext("2d");
  if (!ctx) {
    return source;
  }
  ctx.imageSmoothingEnabled = true;
  ctx.imageSmoothingQuality = "high";
  ctx.drawImage(source, 0, 0, out.width, out.height);
  return out;
}

function sameOriginApiUrl(url: string): string {
  try {
    const parsed = new URL(url, window.location.origin);
    if (parsed.pathname.startsWith("/v1/")) {
      return `${parsed.pathname}${parsed.search}`;
    }
  } catch {
    /* keep absolute URL */
  }
  return url;
}
