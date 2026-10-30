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
  const cameraEpoch = useRef(0);
  const capturing = useRef(false);
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
    const epoch = cameraEpoch.current;
    openCaptureStream(facing)
      .then((stream) => {
        if (cancelled || facingRef.current !== facing || cameraEpoch.current !== epoch) {
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

  function focusPreview(stage: HTMLDivElement, clientX: number, clientY: number) {
    const track = streamRef.current?.getVideoTracks()[0];
    if (!track || track.readyState !== "live") {
      return;
    }
    const rect = stage.getBoundingClientRect();
    if (rect.width === 0 || rect.height === 0) {
      return;
    }
    const x = Math.min(1, Math.max(0, (clientX - rect.left) / rect.width));
    const y = Math.min(1, Math.max(0, (clientY - rect.top) / rect.height));
    void focusTrack(track, { x, y });
  }

  function stopCamera() {
    cameraEpoch.current += 1;
    const video = videoRef.current;
    if (video) {
      video.pause();
      video.srcObject = null;
    }
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
      if (facingFor(result.next) == null) {
        stopCamera();
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
    if (capturing.current) {
      return;
    }
    capturing.current = true;
    setBusy(true);
    setHint("capture.uploading");
    try {
      const kind = session?.next === "capture_selfie" ? "selfie" : "document";
      bindStream(videoRef.current, streamRef.current);
      const video = videoRef.current;
      if (video?.paused) {
        await withTimeout(video.play(), 400).catch(() => undefined);
      }
      const track = streamRef.current?.getVideoTracks()[0];
      if (track && track.readyState === "live") {
        await focusTrack(track, { x: 0.5, y: 0.5 });
      }
      const still = track && track.readyState === "live" ? await canvasFromTrack(track) : null;
      if (still) {
        const framed = kind === "document" ? cropDocumentGuide(still) : still;
        const { image, blob } = await frameFromCanvas(framed, kind === "document" ? DOCUMENT_JPEG_QUALITY : 0.8);
        await submitFrame(kind, image, blob);
        return;
      }
      if (!video || video.videoWidth === 0) {
        setHint("capture.cameraError");
        return;
      }
      const canvas = document.createElement("canvas");
      const scale = Math.min(1, CAPTURE_LONG_SIDE / Math.max(video.videoWidth, video.videoHeight));
      canvas.width = Math.max(1, Math.round(video.videoWidth * scale));
      canvas.height = Math.max(1, Math.round(video.videoHeight * scale));
      const ctx = canvas.getContext("2d");
      if (!ctx) {
        setHint("capture.cameraError");
        return;
      }
      ctx.imageSmoothingEnabled = true;
      ctx.imageSmoothingQuality = "high";
      ctx.drawImage(video, 0, 0, canvas.width, canvas.height);
      const framed = kind === "document" ? cropDocumentGuide(canvas) : canvas;
      const { image, blob } = await frameFromCanvas(framed, kind === "document" ? DOCUMENT_JPEG_QUALITY : 0.8);
      await submitFrame(kind, image, blob);
    } catch {
      setHint("capture.cameraError");
    } finally {
      capturing.current = false;
      setBusy(false);
    }
  }

  async function onPick(event: ChangeEvent<HTMLInputElement>) {
    const file = event.target.files?.[0];
    event.target.value = "";
    if (!file) {
      return;
    }
    if (capturing.current) {
      return;
    }
    capturing.current = true;
    const kind = session?.next === "capture_selfie" ? "selfie" : "document";
    try {
      const { image, blob } = await frameFromFile(file, kind === "document" ? DOCUMENT_JPEG_QUALITY : 0.8);
      await submitFrame(kind, image, blob);
    } catch {
      setHint("capture.decodeError");
    } finally {
      capturing.current = false;
      setBusy(false);
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
          onFocus={(event) => focusPreview(event.currentTarget, event.clientX, event.clientY)}
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
  onFocus,
  onPick,
}: {
  next: CaptureNext;
  busy: boolean;
  hint: MessageKey | null;
  liveCamera: boolean;
  videoRef: React.RefObject<HTMLVideoElement | null>;
  onCapture: () => void;
  onFocus: (event: React.PointerEvent<HTMLDivElement>) => void;
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
        <div
          className="rm-camera-stage"
          role="img"
          aria-label={t(`${copy}.frame` as const)}
          onPointerDown={onFocus}
        >
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
/** Résolution de la photo jugée par le contrôle de netteté. */
const CAPTURE_LONG_SIDE = 3840;
/** Fichier envoyé à l'analyse. Le modèle ne lit pas au-delà. */
const UPLOAD_LONG_SIDE = 1280;
/** Même rectangle que `.rm-camera-cutout` sur un aperçu 4/3 en `object-fit: cover`. */
const STAGE_ASPECT = 4 / 3;
const DOCUMENT_GUIDE_WIDTH = 0.86;
const DOCUMENT_GUIDE_ASPECT = 85 / 54;
const DOCUMENT_JPEG_QUALITY = 0.9;

function frameFromFile(file: File, quality = 0.8): Promise<{ image: ImageData; blob: Blob }> {
  return new Promise((resolve, reject) => {
    const url = URL.createObjectURL(file);
    const img = new Image();
    img.onload = () => {
      const canvas = document.createElement("canvas");
      const scale = Math.min(1, CAPTURE_LONG_SIDE / Math.max(img.naturalWidth, img.naturalHeight));
      canvas.width = Math.max(1, Math.round(img.naturalWidth * scale));
      canvas.height = Math.max(1, Math.round(img.naturalHeight * scale));
      const ctx = canvas.getContext("2d");
      URL.revokeObjectURL(url);
      if (!ctx) {
        reject(new Error("canvas"));
        return;
      }
      ctx.drawImage(img, 0, 0, canvas.width, canvas.height);
      void frameFromCanvas(canvas, quality).then(resolve, reject);
    };
    img.onerror = () => {
      URL.revokeObjectURL(url);
      reject(new Error("image"));
    };
    img.src = url;
  });
}

function highResVideo(facing: "user" | "environment"): MediaTrackConstraints {
  return {
    facingMode: { ideal: facing },
    width: { ideal: CAPTURE_LONG_SIDE },
    height: { ideal: 2160 },
  };
}

async function openCaptureStream(facing: "user" | "environment"): Promise<MediaStream> {
  const stream = await navigator.mediaDevices.getUserMedia({ video: highResVideo(facing) });
  if (facing === "user") {
    await raiseTrack(stream);
    return stream;
  }
  const devices = await navigator.mediaDevices.enumerateDevices().catch(() => []);
  const mainRear = await chooseMainRear(devices, stream);
  if (!mainRear) {
    await raiseTrack(stream);
    return stream;
  }
  try {
    const rear = await navigator.mediaDevices.getUserMedia({
      video: {
        deviceId: { exact: mainRear },
        width: { ideal: CAPTURE_LONG_SIDE },
        height: { ideal: 2160 },
      },
    });
    await raiseTrack(rear);
    return rear;
  } catch {
    const fallback = await navigator.mediaDevices.getUserMedia({ video: highResVideo(facing) });
    await raiseTrack(fallback);
    return fallback;
  }
}

type RearScore = {
  deviceId: string;
  wide: boolean;
  index: number | null;
  pixels: number;
  focus: boolean;
};

/** Le capteur principal : autofocus, pas le grand-angle, identifiant 0 quand le téléphone le numérote ainsi. */
async function chooseMainRear(devices: MediaDeviceInfo[], current: MediaStream): Promise<string | undefined> {
  if (!devices.some((device) => device.kind === "videoinput" && device.label)) {
    return undefined;
  }
  const rear = devices.filter((device) => device.kind === "videoinput" && isRearLabel(device.label));
  if (rear.length <= 1) {
    return undefined;
  }
  const currentTrack = current.getVideoTracks()[0];
  const currentId = currentTrack?.getSettings().deviceId;
  const currentDevice = rear.find((device) => device.deviceId === currentId);
  const currentScore = currentTrack && currentDevice ? scoreRear(currentDevice, currentTrack) : null;
  current.getTracks().forEach((track) => track.stop());
  const scored: RearScore[] = [];
  if (currentScore) {
    scored.push(currentScore);
  }
  for (const device of rear) {
    if (device.deviceId === currentId) {
      continue;
    }
    const probed = await probeRear(device);
    if (probed) {
      scored.push(probed);
    }
  }
  const pool = scored.some((item) => !item.wide) ? scored.filter((item) => !item.wide) : scored;
  pool.sort(compareRear);
  return pool[0]?.deviceId ?? currentId;
}

function compareRear(a: RearScore, b: RearScore): number {
  if (a.focus !== b.focus) {
    return a.focus ? -1 : 1;
  }
  if ((a.index === 0) !== (b.index === 0)) {
    return a.index === 0 ? -1 : 1;
  }
  if (a.pixels !== b.pixels) {
    return b.pixels - a.pixels;
  }
  return (a.index ?? 99) - (b.index ?? 99);
}

function isRearLabel(label: string): boolean {
  if (/front|user|face|selfie/i.test(label) && !/back|rear|environment/i.test(label)) {
    return false;
  }
  return /back|rear|environment|arrière|arriere/i.test(label);
}

function cameraIndex(label: string): number | null {
  const match = label.match(/camera2\s+(\d+)/i) ?? label.match(/camera\s+(\d+)/i);
  if (!match) {
    return null;
  }
  const index = Number(match[1]);
  return Number.isFinite(index) ? index : null;
}

function scoreRear(device: MediaDeviceInfo, track: MediaStreamTrack): RearScore {
  const caps = track.getCapabilities?.() as
    | (MediaTrackCapabilities & {
        focusMode?: string[];
        width?: { max?: number };
        height?: { max?: number };
      })
    | undefined;
  const width = caps?.width?.max ?? track.getSettings().width ?? 0;
  const height = caps?.height?.max ?? track.getSettings().height ?? 0;
  const focus = Boolean(caps?.focusMode?.some((mode) => mode === "continuous" || mode === "single-shot"));
  return {
    deviceId: device.deviceId,
    wide: /ultra|wide|0\.5|depth|tele/i.test(device.label),
    index: cameraIndex(device.label),
    pixels: width * height,
    focus,
  };
}

async function probeRear(device: MediaDeviceInfo): Promise<RearScore | null> {
  let stream: MediaStream | null = null;
  try {
    stream = await navigator.mediaDevices.getUserMedia({
      video: { deviceId: { exact: device.deviceId } },
    });
    const track = stream.getVideoTracks()[0];
    if (!track) {
      return null;
    }
    return scoreRear(device, track);
  } catch {
    return null;
  } finally {
    stream?.getTracks().forEach((track) => track.stop());
  }
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
  const width = caps.width?.max && caps.width.max > 0 ? caps.width.max : CAPTURE_LONG_SIDE;
  const height = caps.height?.max && caps.height.max > 0 ? caps.height.max : 2160;
  const advanced: MediaTrackConstraintSet[] = focusConstraints(caps, { x: 0.5, y: 0.5 });
  try {
    await withTimeout(
      track.applyConstraints({
        width: { ideal: width },
        height: { ideal: height },
        advanced,
      }),
      1000,
    );
  } catch {
    /* le flux déjà ouvert reste utilisable */
  }
  await focusTrack(track, { x: 0.5, y: 0.5 });
}

function focusConstraints(
  caps: MediaTrackCapabilities & {
    focusMode?: string[];
    exposureMode?: string[];
    pointsOfInterest?: boolean;
  },
  point: { x: number; y: number },
): MediaTrackConstraintSet[] {
  const advanced: MediaTrackConstraintSet[] = [];
  if (caps.pointsOfInterest) {
    advanced.push({ pointsOfInterest: [point] } as MediaTrackConstraintSet);
  }
  if (caps.focusMode?.includes("continuous")) {
    advanced.push({ focusMode: "continuous" } as MediaTrackConstraintSet);
  }
  if (caps.exposureMode?.includes("continuous")) {
    advanced.push({ exposureMode: "continuous" } as MediaTrackConstraintSet);
  }
  return advanced;
}

async function focusTrack(track: MediaStreamTrack, point: { x: number; y: number }): Promise<void> {
  if (!track.getCapabilities || track.readyState !== "live") {
    return;
  }
  const caps = track.getCapabilities() as MediaTrackCapabilities & {
    focusMode?: string[];
    exposureMode?: string[];
    pointsOfInterest?: boolean;
  };
  const advanced = focusConstraints(caps, point);
  if (advanced.length === 0) {
    return;
  }
  try {
    await withTimeout(track.applyConstraints({ advanced }), 700);
  } catch {
    return;
  }
  await wait(200);
}

function wait(ms: number): Promise<void> {
  return new Promise((resolve) => window.setTimeout(resolve, ms));
}

function withTimeout<T>(work: Promise<T>, ms: number): Promise<T | undefined> {
  return new Promise((resolve, reject) => {
    const timer = window.setTimeout(() => resolve(undefined), ms);
    work.then(
      (value) => {
        window.clearTimeout(timer);
        resolve(value);
      },
      (error) => {
        window.clearTimeout(timer);
        reject(error);
      },
    );
  });
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

async function takeStill(
  camera: {
    takePhoto: (settings?: { imageWidth?: number; imageHeight?: number }) => Promise<Blob>;
  },
  settings: { imageWidth?: number; imageHeight?: number } | undefined,
): Promise<Blob | null> {
  const attempt = (photo: Promise<Blob>) =>
    Promise.race([
      photo,
      new Promise<null>((resolve) => {
        window.setTimeout(() => resolve(null), 1200);
      }),
    ]);
  const sized = await attempt(camera.takePhoto(settings)).catch(() => null);
  if (sized || !settings) {
    return sized;
  }
  return attempt(camera.takePhoto()).catch(() => null);
}

async function canvasFromTrack(track: MediaStreamTrack): Promise<HTMLCanvasElement | null> {
  const Ctor = (
    window as unknown as {
      ImageCapture?: new (media: MediaStreamTrack) => {
        takePhoto: (settings?: { imageWidth?: number; imageHeight?: number }) => Promise<Blob>;
        getPhotoCapabilities?: () => Promise<{
          imageWidth?: { max?: number };
          imageHeight?: { max?: number };
        }>;
      };
    }
  ).ImageCapture;
  if (!Ctor) {
    return null;
  }
  try {
    const camera = new Ctor(track);
    let settings: { imageWidth?: number; imageHeight?: number } | undefined;
    const photoCaps = await withTimeout(camera.getPhotoCapabilities?.() ?? Promise.resolve(undefined), 600).catch(
      () => undefined,
    );
    const imageWidth = photoCaps?.imageWidth?.max;
    const imageHeight = photoCaps?.imageHeight?.max;
    if (imageWidth && imageHeight) {
      settings = { imageWidth, imageHeight };
    }
    const blob = await takeStill(camera, settings);
    if (!blob) {
      return null;
    }
    return await loadCanvas(blob);
  } catch {
    return null;
  }
}

function loadCanvas(blob: Blob): Promise<HTMLCanvasElement> {
  return new Promise((resolve, reject) => {
    const url = URL.createObjectURL(blob);
    const img = new Image();
    img.onload = () => {
      const canvas = document.createElement("canvas");
      const scale = Math.min(1, CAPTURE_LONG_SIDE / Math.max(img.naturalWidth, img.naturalHeight));
      canvas.width = Math.max(1, Math.round(img.naturalWidth * scale));
      canvas.height = Math.max(1, Math.round(img.naturalHeight * scale));
      const ctx = canvas.getContext("2d");
      URL.revokeObjectURL(url);
      if (!ctx) {
        reject(new Error("canvas"));
        return;
      }
      ctx.drawImage(img, 0, 0, canvas.width, canvas.height);
      resolve(canvas);
    };
    img.onerror = () => {
      URL.revokeObjectURL(url);
      reject(new Error("image"));
    };
    img.src = url;
  });
}

function frameFromCanvas(canvas: HTMLCanvasElement, quality = 0.8): Promise<{ image: ImageData; blob: Blob }> {
  const ctx = canvas.getContext("2d");
  if (!ctx) {
    return Promise.reject(new Error("canvas"));
  }
  const image = ctx.getImageData(0, 0, canvas.width, canvas.height);
  const upload = limitLongSide(ensureMinShortSide(canvas, SERVER_MIN_SHORT_SIDE), UPLOAD_LONG_SIDE);
  return new Promise((resolve, reject) => {
    upload.toBlob((blob) => (blob ? resolve({ image, blob }) : reject(new Error("blob"))), "image/jpeg", quality);
  });
}

/** Le guide vu par la personne, pas le clavier autour. */
function cropDocumentGuide(source: HTMLCanvasElement): HTMLCanvasElement {
  const srcW = source.width;
  const srcH = source.height;
  if (srcW < 2 || srcH < 2) {
    return source;
  }
  const srcAspect = srcW / srcH;
  const visibleW = srcAspect > STAGE_ASPECT ? srcH * STAGE_ASPECT : srcW;
  const visibleH = srcAspect > STAGE_ASPECT ? srcH : srcW / STAGE_ASPECT;
  const visibleX = (srcW - visibleW) / 2;
  const visibleY = (srcH - visibleH) / 2;
  const cutoutW = visibleW * DOCUMENT_GUIDE_WIDTH;
  const cutoutH = Math.min(visibleH, cutoutW / DOCUMENT_GUIDE_ASPECT);
  const cutoutX = visibleX + (visibleW - cutoutW) / 2;
  const cutoutY = visibleY + (visibleH - cutoutH) / 2;
  const out = document.createElement("canvas");
  out.width = Math.max(1, Math.round(cutoutW));
  out.height = Math.max(1, Math.round(cutoutH));
  const ctx = out.getContext("2d");
  if (!ctx) {
    return source;
  }
  ctx.drawImage(source, cutoutX, cutoutY, cutoutW, cutoutH, 0, 0, out.width, out.height);
  return out;
}

function limitLongSide(source: HTMLCanvasElement, max: number): HTMLCanvasElement {
  const long = Math.max(source.width, source.height);
  if (long <= max) {
    return source;
  }
  const scale = max / long;
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
