"use client";

import { useEffect, useRef, useState } from "react";

type Props = {
  externalId: string;
  onError: (message: string) => void;
};

type Created = {
  id: string;
  hosted_url: string;
  status: string;
};

type SessionStatus = {
  id?: string;
  status?: string;
  reasons?: string[] | null;
  match_score?: number | null;
  failure_reason?: string | null;
  decided_at?: string | null;
  error?: string;
};

const TERMINAL = new Set(["approved", "declined", "failed", "expired", "cancelled", "review"]);

export function Authenticate({ externalId, onError }: Props) {
  const [busy, setBusy] = useState(false);
  const [sessionId, setSessionId] = useState<string | null>(null);
  const [result, setResult] = useState<SessionStatus | null>(null);
  const pollTimer = useRef<number | null>(null);
  const pollActive = useRef(false);
  const abortRef = useRef<AbortController | null>(null);

  useEffect(() => {
    return () => {
      stopPoll();
      closeFrame();
    };
  }, []);

  function stopPoll() {
    pollActive.current = false;
    if (pollTimer.current != null) {
      window.clearTimeout(pollTimer.current);
      pollTimer.current = null;
    }
    abortRef.current?.abort();
    abortRef.current = null;
  }

  async function readStatus(id: string): Promise<SessionStatus | null> {
    abortRef.current?.abort();
    const controller = new AbortController();
    abortRef.current = controller;
    const response = await fetch(`/api/authentications/${encodeURIComponent(id)}`, {
      cache: "no-store",
      signal: controller.signal,
    });
    const body = (await response.json().catch(() => ({}))) as SessionStatus;
    if (!response.ok) {
      throw new Error(body.error ?? "Impossible de relire la session.");
    }
    setResult(body);
    if (body.status && TERMINAL.has(body.status)) {
      stopPoll();
      setBusy(false);
      closeFrame();
    }
    return body;
  }

  function startPoll(id: string) {
    stopPoll();
    pollActive.current = true;
    const tick = () => {
      if (!pollActive.current) {
        return;
      }
      void readStatus(id)
        .catch((error: { name?: string; message?: string }) => {
          if (error.name === "AbortError") {
            return;
          }
          stopPoll();
          setBusy(false);
          onError(error.message ?? "Impossible de relire la session.");
        })
        .finally(() => {
          if (pollActive.current) {
            pollTimer.current = window.setTimeout(tick, 4000);
          }
        });
    };
    tick();
  }

  async function authenticate() {
    setBusy(true);
    setResult(null);
    onError("");
    try {
      const response = await fetch("/api/authentications", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ externalId }),
      });
      const body = (await response.json().catch(() => ({}))) as Created & { error?: string };
      if (!response.ok || !body.hosted_url || !body.id) {
        throw new Error(body.error ?? "La session n’a pas pu être créée.");
      }
      setSessionId(body.id);
      openFrame(body.hosted_url, () => {
        stopPoll();
        setBusy(false);
        void readStatus(body.id).catch(() => undefined);
      });
      startPoll(body.id);
    } catch (error) {
      setBusy(false);
      onError(error instanceof Error ? error.message : "La session n’a pas pu être créée.");
    }
  }

  return (
    <div className="auth-actions">
      <button type="button" className="verify" disabled={busy || !externalId.trim()} onClick={() => void authenticate()}>
        {busy ? "Parcours en cours…" : "S’authentifier"}
      </button>
      {sessionId ? <p className="meta">Session {sessionId}</p> : null}
      {result?.status ? (
        <div className="auth-result">
          <p className={result.status === "approved" ? "ok" : "status"}>
            Statut : <strong>{result.status}</strong>
          </p>
          {result.reasons?.length ? <p className="meta">Raisons : {result.reasons.join(", ")}</p> : null}
          {result.match_score != null ? <p className="meta">Score : {result.match_score}</p> : null}
          {result.failure_reason ? <p className="meta">Échec : {result.failure_reason}</p> : null}
        </div>
      ) : null}
    </div>
  );
}

function closeFrame() {
  document.getElementById("recogniz-me-frame")?.remove();
}

function openFrame(url: string, onClose: () => void) {
  closeFrame();
  const overlay = document.createElement("div");
  overlay.id = "recogniz-me-frame";
  overlay.setAttribute(
    "style",
    "position:fixed;inset:0;z-index:2147483000;display:grid;place-items:center;padding:1rem;background:rgba(17,26,21,.45);",
  );
  const pane = document.createElement("iframe");
  pane.setAttribute(
    "style",
    "width:min(28rem,100%);height:min(44rem,100%);border:0;border-radius:14px;background:#fff;",
  );
  pane.src = url;
  pane.title = "Recogniz-Me";
  pane.allow = "camera; microphone";
  overlay.appendChild(pane);
  overlay.addEventListener("click", (event) => {
    if (event.target === overlay) {
      closeFrame();
      onClose();
    }
  });
  document.body.appendChild(overlay);
}
