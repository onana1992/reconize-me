"use client";

import { FaceLivenessDetectorCore } from "@aws-amplify/ui-react-liveness";
import "@aws-amplify/ui-react/styles.css";
import { useCallback, useEffect, useState } from "react";
import { useT } from "../../../i18n/client";

type Opened = {
  session_id: string;
  region: string;
  credentials: {
    access_key_id: string;
    secret_access_key: string;
    session_token: string;
    expiration: string;
  };
};

export default function LivenessCheck({ token, onDone }: { token: string; onDone: () => void }) {
  const t = useT();
  const [opened, setOpened] = useState<Opened | null>(null);
  const [error, setError] = useState(false);

  const open = useCallback(async () => {
    setError(false);
    setOpened(null);
    try {
      const response = await fetch(`/v1/flow/${encodeURIComponent(token)}/liveness/session`, { method: "POST" });
      if (!response.ok) {
        if (response.status === 409) {
          onDone();
          return;
        }
        setError(true);
        return;
      }
      setOpened((await response.json()) as Opened);
    } catch {
      setError(true);
    }
  }, [token, onDone]);

  useEffect(() => {
    void open();
  }, [open]);

  async function finish(sessionId: string) {
    try {
      const response = await fetch(`/v1/flow/${encodeURIComponent(token)}/liveness/complete`, {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ session_id: sessionId }),
      });
      if (response.status === 409) {
        const body = (await response.json()) as { error?: { code?: string } };
        if (body.error?.code === "liveness_expired") {
          await open();
          return;
        }
      }
      if (!response.ok && response.status !== 409) {
        setError(true);
        return;
      }
      onDone();
    } catch {
      setError(true);
    }
  }

  return (
    <>
      <h1>{t("liveness.title")}</h1>
      <p className="rm-lead">{t("liveness.lead")}</p>
      {error ? (
        <p role="alert" className="rm-alert">
          {t("liveness.error")}
        </p>
      ) : null}
      {opened ? (
        <FaceLivenessDetectorCore
          key={opened.session_id}
          sessionId={opened.session_id}
          region={opened.region}
          onAnalysisComplete={async () => {
            await finish(opened.session_id);
          }}
          onError={() => setError(true)}
          config={{
            credentialProvider: async () => ({
              accessKeyId: opened.credentials.access_key_id,
              secretAccessKey: opened.credentials.secret_access_key,
              sessionToken: opened.credentials.session_token,
              expiration: new Date(opened.credentials.expiration),
            }),
          }}
        />
      ) : null}
      {error ? (
        <div className="rm-actions">
          <button type="button" onClick={() => void open()}>
            {t("liveness.retry")}
          </button>
        </div>
      ) : null}
    </>
  );
}
