"use client";

import { assessDocumentFrame, assessSelfieFrame } from "@kyc/capture-sdk";
import { useCallback, useEffect, useRef, useState, type ChangeEvent } from "react";
import { FlowBrand } from "../../../components/flow-brand";
import { useT, type MessageKey } from "../../../i18n/client";

type Session = {
  verification_id: string;
  status: string;
  consent_text_version: string;
  expires_at: string;
  next: string;
};

export function FlowClient({ token }: { token: string }) {
  const t = useT();
  const [session, setSession] = useState<Session | null>(null);
  const [error, setError] = useState<"invalid" | "expired" | "network" | null>(null);
  const [busy, setBusy] = useState(false);
  const [hint, setHint] = useState<MessageKey | null>(null);
  const [liveCamera, setLiveCamera] = useState(false);
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

  useEffect(() => {
    const next = session?.next;
    if (next !== "capture_document" && next !== "capture_selfie") {
      stopCamera();
      return;
    }
    if (!liveCamera) {
      stopCamera();
      return;
    }
    const facing = next === "capture_selfie" ? "user" : "environment";
    navigator.mediaDevices
      .getUserMedia({
        video: {
          facingMode: { ideal: facing },
          width: { ideal: 1920 },
          height: { ideal: 1080 },
        },
      })
      .then((stream) => {
        streamRef.current = stream;
        if (videoRef.current) {
          videoRef.current.srcObject = stream;
          void videoRef.current.play();
        }
      })
      .catch(() => setHint("capture.cameraError"));
    return () => stopCamera();
  }, [liveCamera, session?.next]);

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
      alert(assessment.reasons);
      setHint(hintForReasons(assessment.reasons));
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
      const complete = await fetch(`/v1/flow/${encodeURIComponent(token)}/${prefix}/complete`, {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ attempt: signed.attempt }),
      });
      if (!complete.ok) {
        setHint("capture.uploadError");
        return;
      }
      const result = (await complete.json()) as { status: string; next: string; accepted: boolean };
      if (!result.accepted) {
        setHint("capture.recapture");
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
    const kind = session?.next === "capture_selfie" ? "selfie" : "document";
    const track = streamRef.current?.getVideoTracks()[0];
    const still = track ? await stillFromTrack(track) : null;
    if (still) {
      await submitFrame(kind, still.image, still.blob);
      return;
    }
    const video = videoRef.current;
    if (!video || video.videoWidth === 0) {
      setHint("capture.cameraError");
      return;
    }
    const canvas = document.createElement("canvas");
    canvas.width = video.videoWidth;
    canvas.height = video.videoHeight;
    const ctx = canvas.getContext("2d");
    if (!ctx) {
      setHint("capture.cameraError");
      return;
    }
    ctx.drawImage(video, 0, 0);
    const { image, blob } = await frameFromCanvas(canvas);
    await submitFrame(kind, image, blob);
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

  return (
    <main className="rm-flow">
      <FlowBrand />
      {error === "invalid" ? <h1>{t("errors.invalid")}</h1> : null}
      {error === "expired" ? <h1>{t("errors.expired")}</h1> : null}
      {error === "network" ? (
        <>
          <h1>{t("errors.network")}</h1>
          <div className="rm-actions">
            <button type="button" onClick={() => void load()}>
              {t("errors.retry")}
            </button>
          </div>
        </>
      ) : null}
      {!error && session?.next === "consent" ? (
        <>
          <h1>{t("consent.title")}</h1>
          <p className="rm-lead">{t("consent.body")}</p>
          {hint ? (
            <p role="alert" className="rm-alert">
              {t(hint)}
            </p>
          ) : null}
          <form
            className="rm-actions"
            onSubmit={(event) => {
              event.preventDefault();
              void consent("accepted");
            }}
          >
            <button type="submit" disabled={busy}>
              {busy ? t("capture.uploading") : t("consent.accept")}
            </button>
            <button type="button" data-variant="secondary" disabled={busy} onClick={() => void consent("declined")}>
              {t("consent.decline")}
            </button>
          </form>
        </>
      ) : null}
      {!error && (session?.next === "capture_document" || session?.next === "capture_selfie") ? (
        <>
          <h1>{session.next === "capture_selfie" ? t("selfie.title") : t("document.title")}</h1>
          <p className="rm-lead">{session.next === "capture_selfie" ? t("selfie.lead") : t("document.lead")}</p>
          <div className="rm-flow-capture">
            <div
              className="rm-flow-stage"
              data-kind={session.next === "capture_selfie" ? "selfie" : "document"}
              role="img"
              aria-label={session.next === "capture_selfie" ? t("selfie.frame") : t("document.frame")}
            >
              {liveCamera ? <video ref={videoRef} autoPlay playsInline muted /> : <span className="rm-flow-guide" aria-hidden="true" />}
              <span className="rm-flow-frame" aria-hidden="true" />
            </div>
            {hint ? (
              <p className={hint === "capture.uploading" ? "rm-hint" : "rm-alert"} role={hint === "capture.uploading" ? undefined : "alert"}>
                {t(hint)}
              </p>
            ) : null}
            <div className="rm-actions">
              {liveCamera ? (
                <button type="button" disabled={busy} onClick={() => void capture()}>
                  {busy ? t("capture.uploading") : t("capture.action")}
                </button>
              ) : (
                <label className="rm-button rm-flow-file">
                  {busy ? t("capture.uploading") : t("capture.action")}
                  <input
                    type="file"
                    accept="image/*"
                    capture={session.next === "capture_selfie" ? "user" : "environment"}
                    disabled={busy}
                    onChange={(event) => void onPick(event)}
                  />
                </label>
              )}
            </div>
          </div>
        </>
      ) : null}
      {!error && session?.next === "wait" ? (
        <>
          <h1>{t("outcome.wait")}</h1>
        </>
      ) : null}
      {!error && session?.next === "done" ? (
        <h1>{session.status === "declined" ? t("outcome.declined") : t("outcome.done")}</h1>
      ) : null}
    </main>
  );
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
      const scale = Math.min(1, 1600 / Math.max(img.naturalWidth, img.naturalHeight));
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

async function stillFromTrack(track: MediaStreamTrack): Promise<{ image: ImageData; blob: Blob } | null> {
  const Ctor = (window as unknown as { ImageCapture?: new (media: MediaStreamTrack) => { takePhoto: () => Promise<Blob> } })
    .ImageCapture;
  if (!Ctor) {
    return null;
  }
  try {
    const blob = await new Ctor(track).takePhoto();
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
