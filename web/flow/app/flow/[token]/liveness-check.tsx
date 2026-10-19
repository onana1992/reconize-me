"use client";

import { ThemeProvider, type Theme } from "@aws-amplify/ui-react";
import {
  FaceLivenessDetectorCore,
  type FaceLivenessDetectorCoreProps,
} from "@aws-amplify/ui-react-liveness";
import "@aws-amplify/ui-react/styles.css";
import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import { createPortal } from "react-dom";
import { flowTheme, flowThemeStyle } from "@/brand";
import { Notice, StateScreen } from "../../../components/flow-shell";
import { AlertIcon, CloseIcon, FaceIcon, GlassesIcon, PhoneIcon, SunIcon } from "../../../components/icons";
import { useT, type MessageKey, type Translate } from "../../../i18n/client";

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

type Phase = "intro" | "running" | "finishing";

type DisplayText = NonNullable<FaceLivenessDetectorCoreProps["displayText"]>;
type Components = NonNullable<FaceLivenessDetectorCoreProps["components"]>;

const LIVENESS_TEXT_KEYS = [
  "hintMoveFaceFrontOfCameraText",
  "hintTooManyFacesText",
  "hintFaceDetectedText",
  "hintCanNotIdentifyText",
  "hintTooCloseText",
  "hintTooFarText",
  "hintConnectingText",
  "hintVerifyingText",
  "hintCheckCompleteText",
  "hintIlluminationTooBrightText",
  "hintIlluminationTooDarkText",
  "hintIlluminationNormalText",
  "hintHoldFaceForFreshnessText",
  "hintCenterFaceText",
  "hintCenterFaceInstructionText",
  "hintFaceOffCenterText",
  "hintMatchIndicatorText",
  "cameraMinSpecificationsHeadingText",
  "cameraMinSpecificationsMessageText",
  "cameraNotFoundHeadingText",
  "cameraNotFoundMessageText",
  "retryCameraPermissionsText",
  "waitingCameraPermissionText",
  "a11yVideoLabelText",
  "goodFitCaptionText",
  "goodFitAltText",
  "tooFarCaptionText",
  "tooFarAltText",
  "photosensitivityWarningBodyText",
  "photosensitivityWarningHeadingText",
  "photosensitivityWarningInfoText",
  "photosensitivityWarningLabelText",
  "startScreenBeginCheckText",
  "recordingIndicatorText",
  "cancelLivenessCheckText",
  "errorLabelText",
  "connectionTimeoutHeaderText",
  "connectionTimeoutMessageText",
  "timeoutHeaderText",
  "timeoutMessageText",
  "faceDistanceHeaderText",
  "faceDistanceMessageText",
  "multipleFacesHeaderText",
  "multipleFacesMessageText",
  "clientHeaderText",
  "clientMessageText",
  "serverHeaderText",
  "serverMessageText",
  "landscapeHeaderText",
  "landscapeMessageText",
  "portraitMessageText",
  "tryAgainText",
] as const;

function livenessText(t: Translate): DisplayText {
  const text: Record<string, string> = {};
  for (const key of LIVENESS_TEXT_KEYS) {
    text[key] = t(`livenessUi.${key}` as MessageKey);
  }
  return text as DisplayText;
}

function livenessTheme(): Theme {
  return {
    name: "recogniz-me-liveness",
    tokens: {
      colors: {
        brand: {
          primary: {
            10: { value: flowTheme.accentSubtle },
            20: { value: flowTheme.accentSubtle },
            40: { value: flowTheme.accent },
            60: { value: flowTheme.accent },
            80: { value: flowTheme.accent },
            90: { value: flowTheme.accentHover },
            100: { value: flowTheme.accentActive },
          },
        },
      },
      fonts: {
        default: {
          variable: { value: "var(--rm-font-sans)" },
          static: { value: "var(--rm-font-sans)" },
        },
      },
      radii: {
        small: { value: "8px" },
        medium: { value: "12px" },
        large: { value: `${flowTheme.radius}px` },
      },
    },
  };
}

function wait(ms: number): Promise<void> {
  return new Promise((resolve) => window.setTimeout(resolve, ms));
}

async function claimFrontCamera(): Promise<string | null> {
  for (let attempt = 0; attempt < 4; attempt += 1) {
    await wait(attempt === 0 ? 800 : 600);
    let stream: MediaStream | null = null;
    try {
      stream = await navigator.mediaDevices.getUserMedia({
        audio: false,
        video: { facingMode: { ideal: "user" } },
      });
      const track = stream.getVideoTracks()[0];
      const deviceId = track?.getSettings().deviceId ?? "";
      const facing = track?.getSettings().facingMode;
      stream.getTracks().forEach((item) => item.stop());
      stream = null;
      if (facing === "environment") {
        continue;
      }
      await wait(400);
      return deviceId;
    } catch {
      stream?.getTracks().forEach((track) => track.stop());
    }
  }
  return null;
}

function livenessFailure(state: string): MessageKey {
  switch (state) {
    case "CAMERA_ACCESS_ERROR":
    case "DEFAULT_CAMERA_NOT_FOUND_ERROR":
      return "livenessUi.cameraNotFoundMessageText";
    case "CAMERA_FRAMERATE_ERROR":
      return "livenessUi.cameraMinSpecificationsMessageText";
    case "TIMEOUT":
    case "FRESHNESS_TIMEOUT":
      return "livenessUi.timeoutMessageText";
    case "CONNECTION_TIMEOUT":
      return "livenessUi.connectionTimeoutMessageText";
    case "SERVER_ERROR":
      return "livenessUi.serverMessageText";
    case "MOBILE_LANDSCAPE_ERROR":
      return "livenessUi.landscapeMessageText";
    case "MULTIPLE_FACES_ERROR":
      return "livenessUi.multipleFacesMessageText";
    case "FACE_DISTANCE_ERROR":
      return "livenessUi.faceDistanceMessageText";
    default:
      return "liveness.error";
  }
}

export default function LivenessCheck({ token, onDone }: { token: string; onDone: () => void }) {
  const t = useT();
  const [opened, setOpened] = useState<Opened | null>(null);
  const [error, setError] = useState(false);
  const [checkFailed, setCheckFailed] = useState(false);
  const [failure, setFailure] = useState<MessageKey | null>(null);
  const [below, setBelow] = useState(false);
  const [phase, setPhase] = useState<Phase>("intro");
  const [armed, setArmed] = useState(false);
  const [frontDeviceId, setFrontDeviceId] = useState<string | null>(null);
  const displayText = useMemo(() => livenessText(t), [t]);
  const theme = useMemo(() => livenessTheme(), []);

  const opening = useRef<Promise<boolean> | null>(null);
  const streamed = useRef(false);
  const requested = useRef(false);
  const starting = useRef(false);

  const open = useCallback((fresh = false): Promise<boolean> => {
    const run = async (): Promise<boolean> => {
      setError(false);
      for (let attempt = 0; attempt < 3; attempt += 1) {
        try {
          const query = fresh ? "?fresh=true" : "";
          const response = await fetch(`/v1/flow/${encodeURIComponent(token)}/liveness/session${query}`, {
            method: "POST",
          });
          if (response.ok) {
            setOpened((await response.json()) as Opened);
            return true;
          }
          if (response.status === 409) {
            const body = (await response.json().catch(() => ({}))) as { error?: { code?: string } };
            if (body.error?.code === "liveness_session_busy") {
              await new Promise((resolve) => window.setTimeout(resolve, 400));
              continue;
            }
            onDone();
            return false;
          }
          setError(true);
          return false;
        } catch {
          setError(true);
          return false;
        }
      }
      setError(true);
      return false;
    };
    const job = (opening.current ?? Promise.resolve(false)).then(run, run);
    opening.current = job.finally(() => {
      if (opening.current === job) {
        opening.current = null;
      }
    });
    return opening.current;
  }, [token, onDone]);

  useEffect(() => {
    if (requested.current) {
      return;
    }
    requested.current = true;
    void open(false);
  }, [open]);

  useEffect(() => {
    let cancelled = false;
    void (async () => {
      const deviceId = await claimFrontCamera();
      if (cancelled) {
        return;
      }
      if (deviceId == null) {
        setFailure("livenessUi.cameraNotFoundMessageText");
        return;
      }
      setFrontDeviceId(deviceId);
    })();
    return () => {
      cancelled = true;
    };
  }, []);

  useEffect(() => {
    if (phase !== "running" || !opened) {
      setArmed(false);
      return;
    }
    const id = window.setTimeout(() => {
      streamed.current = true;
      setArmed(true);
    }, 400);
    return () => window.clearTimeout(id);
  }, [phase, opened]);

  useEffect(() => {
    if (phase !== "running") {
      return;
    }
    const previous = document.body.style.overflow;
    document.body.style.overflow = "hidden";
    return () => {
      document.body.style.overflow = previous;
    };
  }, [phase]);

  async function finish(sessionId: string) {
    setPhase("finishing");
    try {
      const response = await fetch(`/v1/flow/${encodeURIComponent(token)}/liveness/complete`, {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ session_id: sessionId }),
      });
      const body = (await response.json().catch(() => ({}))) as {
        error?: { code?: string };
        accepted?: boolean;
        next?: string;
        reason?: string;
      };
      if (response.status === 409 && body.error?.code === "liveness_expired") {
        setPhase("intro");
        await open(true);
        return;
      }
      if (response.ok && body.reason === "liveness_fail" && body.next === "liveness") {
        setBelow(true);
        setFailure(null);
        setOpened(null);
        streamed.current = false;
        setPhase("intro");
        await open(false);
        return;
      }
      if (!response.ok && response.status !== 409) {
        fail("liveness.error");
        return;
      }
      onDone();
    } catch {
      fail("liveness.error");
    }
  }

  function fail(message: MessageKey) {
    setFailure(message);
    setCheckFailed(true);
    setPhase("intro");
  }

  async function begin() {
    if (starting.current) {
      return;
    }
    starting.current = true;
    setFailure(null);
    setCheckFailed(false);
    setBelow(false);
    try {
      if (streamed.current || !opened) {
        const ready = await open(streamed.current);
        if (!ready) {
          return;
        }
      }
      if (frontDeviceId == null) {
        const deviceId = await claimFrontCamera();
        if (deviceId == null) {
          setFailure("livenessUi.cameraNotFoundMessageText");
          return;
        }
        setFrontDeviceId(deviceId);
      }
      setPhase("running");
    } finally {
      starting.current = false;
    }
  }

  const components = useMemo<Components>(
    () => ({
      CancelButton: ({ onCancel, cancelLivenessCheckText }) => (
        <button type="button" className="rm-liveness-cancel" aria-label={cancelLivenessCheckText} onClick={onCancel}>
          <CloseIcon size={18} />
        </button>
      ),
      ErrorView: ({ children, onRetry }) => (
        <div className="rm-liveness-error" role="alert">
          <span className="rm-liveness-error-icon" aria-hidden="true">
            <AlertIcon size={26} />
          </span>
          <div className="rm-liveness-error-body">{children}</div>
          <button type="button" className="rm-btn-primary" onClick={onRetry}>
            {t("liveness.retry")}
          </button>
        </div>
      ),
    }),
    [t],
  );

  if (phase === "finishing") {
    return <StateScreen tone="progress" title={t("livenessUi.hintVerifyingText")} lead={t("outcome.waitLead")} />;
  }

  if (phase === "running" && opened && !armed) {
    return <StateScreen tone="progress" title={t("liveness.preparing")} lead={t("liveness.lead")} />;
  }

  if (phase === "running" && opened && armed) {
    return createPortal(
      <div className="rm-flow-page rm-flow rm-liveness rm-liveness-stage" style={flowThemeStyle()}>
        <div className="rm-liveness-stage-frame">
          <ThemeProvider theme={theme} colorMode="light">
            <FaceLivenessDetectorCore
              key={opened.session_id}
              sessionId={opened.session_id}
              region={opened.region}
              disableStartScreen
              displayText={displayText}
              components={components}
              onAnalysisComplete={async () => {
                await finish(opened.session_id);
              }}
              onUserCancel={() => {
                setPhase("intro");
              }}
              onError={(livenessError) => fail(livenessFailure(livenessError.state))}
              config={{
                ...(frontDeviceId ? { deviceId: frontDeviceId } : {}),
                credentialProvider: async () => ({
                  accessKeyId: opened.credentials.access_key_id,
                  secretAccessKey: opened.credentials.secret_access_key,
                  sessionToken: opened.credentials.session_token,
                  expiration: new Date(opened.credentials.expiration),
                }),
              }}
            />
          </ThemeProvider>
        </div>
      </div>,
      document.body,
    );
  }

  return (
    <div className="rm-screen rm-liveness">
      <p className="rm-eyebrow">{t("liveness.eyebrow")}</p>
      <h1>{t("liveness.title")}</h1>
      <p className="rm-lead">{t("liveness.lead")}</p>
      <div className="rm-liveness-hero" aria-hidden="true">
        <span className="rm-liveness-ring" />
        <span className="rm-liveness-ring" data-delay="true" />
        <span className="rm-liveness-oval">
          <FaceIcon size={54} />
        </span>
      </div>
      <ul className="rm-checklist">
        <li>
          <span aria-hidden="true">
            <GlassesIcon size={18} />
          </span>
          {t("liveness.tipFace")}
        </li>
        <li>
          <span aria-hidden="true">
            <SunIcon size={18} />
          </span>
          {t("liveness.tipLight")}
        </li>
        <li>
          <span aria-hidden="true">
            <PhoneIcon size={18} />
          </span>
          {t("liveness.tipHold")}
        </li>
      </ul>
      <div className="rm-warning">
        <AlertIcon size={18} />
        <p>
          <strong>{t("liveness.warningTitle")}</strong>
          <span>{t("liveness.warningBody")}</span>
        </p>
      </div>
      {failure ? <Notice tone="danger">{t(failure)}</Notice> : null}
      {!failure && below ? <Notice tone="danger">{t("liveness.below")}</Notice> : null}
      {!failure && !below && (error || checkFailed) ? <Notice tone="danger">{t("liveness.error")}</Notice> : null}
      <div className="rm-flow-cta">
        {failure || checkFailed || error ? (
          <button type="button" className="rm-btn-primary" onClick={() => void begin()}>
            {t("liveness.retry")}
          </button>
        ) : (
          <button type="button" className="rm-btn-primary" disabled={!opened || frontDeviceId == null} onClick={() => void begin()}>
            {opened && frontDeviceId != null ? null : <span className="rm-spinner" data-size="sm" aria-hidden="true" />}
            {opened && frontDeviceId != null ? t("liveness.start") : t("liveness.preparing")}
          </button>
        )}
      </div>
    </div>
  );
}
