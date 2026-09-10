"use client";

import { Wordmark } from "@kyc/brand";
import { assessDocumentFrame, assessSelfieFrame } from "@kyc/capture-sdk";
import { useCallback, useEffect, useRef, useState } from "react";
import en from "../../../i18n/en.json";
import fr from "../../../i18n/fr.json";

type Locale = "fr" | "en";
type Messages = typeof fr;

type Session = {
  verification_id: string;
  status: string;
  consent_text_version: string;
  expires_at: string;
  next: string;
};

const DICTS: Record<Locale, Messages> = { fr, en };

export function FlowClient({ token, apiBase }: { token: string; apiBase: string }) {
  const [locale, setLocale] = useState<Locale>("fr");
  const t = DICTS[locale];
  const [session, setSession] = useState<Session | null>(null);
  const [error, setError] = useState<"invalid" | "expired" | null>(null);
  const [busy, setBusy] = useState(false);
  const [hint, setHint] = useState<string | null>(null);
  const videoRef = useRef<HTMLVideoElement>(null);
  const streamRef = useRef<MediaStream | null>(null);

  const load = useCallback(async () => {
    const response = await fetch(`${apiBase}/v1/flow/${encodeURIComponent(token)}`, { cache: "no-store" });
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
    setSession((await response.json()) as Session);
  }, [apiBase, token]);

  useEffect(() => {
    void load();
  }, [load]);

  useEffect(() => {
    const next = session?.next;
    if (next !== "capture_document" && next !== "capture_selfie") {
      stopCamera();
      return;
    }
    const facing = next === "capture_selfie" ? "user" : "environment";
    navigator.mediaDevices
      .getUserMedia({ video: { facingMode: facing, width: { ideal: 1280 }, height: { ideal: 720 } } })
      .then((stream) => {
        streamRef.current = stream;
        if (videoRef.current) {
          videoRef.current.srcObject = stream;
        }
      })
      .catch(() => setHint(t.cameraError));
    return () => stopCamera();
  }, [session?.next, t.cameraError]);

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
    const response = await fetch(`${apiBase}/v1/flow/${encodeURIComponent(token)}/consent`, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ decision }),
    });
    setBusy(false);
    if (!response.ok) {
      await load();
      return;
    }
    const body = (await response.json()) as { status: string; next: string };
    setSession((current) =>
      current ? { ...current, status: body.status, next: body.next } : current,
    );
  }

  async function capture() {
    const video = videoRef.current;
    if (!video || video.videoWidth === 0) {
      return;
    }
    const canvas = document.createElement("canvas");
    canvas.width = video.videoWidth;
    canvas.height = video.videoHeight;
    const ctx = canvas.getContext("2d");
    if (!ctx) {
      return;
    }
    ctx.drawImage(video, 0, 0);
    const image = ctx.getImageData(0, 0, canvas.width, canvas.height);
    const kind = session?.next === "capture_selfie" ? "selfie" : "document";
    const assessment = kind === "selfie" ? assessSelfieFrame(image) : assessDocumentFrame(image);
    if (!assessment.usable) {
      setHint(t.recapture);
      return;
    }
    setBusy(true);
    setHint(t.uploading);
    try {
      const blob: Blob = await new Promise((resolve, reject) => {
        canvas.toBlob((value) => (value ? resolve(value) : reject(new Error("blob"))), "image/jpeg", 0.92);
      });
      const prefix = kind === "selfie" ? "selfie" : "document";
      const upload = await fetch(`${apiBase}/v1/flow/${encodeURIComponent(token)}/${prefix}/uploads`, {
        method: "POST",
      });
      if (!upload.ok) {
        setHint(t.recapture);
        return;
      }
      const signed = (await upload.json()) as { upload_url: string; attempt: number };
      const put = await fetch(signed.upload_url, {
        method: "PUT",
        headers: { "Content-Type": "image/jpeg" },
        body: blob,
      });
      if (!put.ok) {
        setHint(t.recapture);
        return;
      }
      const complete = await fetch(`${apiBase}/v1/flow/${encodeURIComponent(token)}/${prefix}/complete`, {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ attempt: signed.attempt }),
      });
      if (!complete.ok) {
        setHint(t.recapture);
        return;
      }
      const result = (await complete.json()) as { status: string; next: string; accepted: boolean };
      if (!result.accepted) {
        setHint(t.recapture);
      } else {
        setHint(null);
      }
      setSession((current) =>
        current ? { ...current, status: result.status, next: result.next } : current,
      );
    } finally {
      setBusy(false);
    }
  }

  return (
    <main className="rm-flow">
      <div className="rm-flow-brand">
        <Wordmark size={24} tone="accent" />
        <div className="rm-flow-lang">
          <button type="button" data-variant="secondary" onClick={() => setLocale("fr")}>
            {t.langFr}
          </button>
          <button type="button" data-variant="secondary" onClick={() => setLocale("en")}>
            {t.langEn}
          </button>
        </div>
      </div>
      {error === "invalid" ? <h1>{t.invalid}</h1> : null}
      {error === "expired" ? <h1>{t.expired}</h1> : null}
      {!error && session?.next === "consent" ? (
        <>
          <h1>{t.consentTitle}</h1>
          <p>{t.consentBody}</p>
          <div className="rm-actions">
            <button type="button" disabled={busy} onClick={() => void consent("accepted")}>
              {t.accept}
            </button>
            <button type="button" data-variant="secondary" disabled={busy} onClick={() => void consent("declined")}>
              {t.decline}
            </button>
          </div>
        </>
      ) : null}
      {!error && (session?.next === "capture_document" || session?.next === "capture_selfie") ? (
        <>
          <h1>{session.next === "capture_selfie" ? t.selfieTitle : t.documentTitle}</h1>
          <p>{session.next === "capture_selfie" ? t.selfieLead : t.documentLead}</p>
          <video ref={videoRef} autoPlay playsInline muted />
          {hint ? <p>{hint}</p> : null}
          <div className="rm-actions">
            <button type="button" disabled={busy} onClick={() => void capture()}>
              {busy ? t.uploading : t.capture}
            </button>
          </div>
        </>
      ) : null}
      {!error && session?.next === "wait" ? (
        <>
          <h1>{t.wait}</h1>
        </>
      ) : null}
      {!error && session?.next === "done" ? <h1>{t.done}</h1> : null}
    </main>
  );
}
