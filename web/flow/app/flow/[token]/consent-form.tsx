"use client";

import { useRouter } from "next/navigation";
import { useState } from "react";
import { FlowShell } from "../../../components/flow-shell";
import { submitConsentAction } from "./actions";

export type FlowCopy = {
  consentTitle: string;
  consentText: string;
  accept: string;
  decline: string;
  thanksTitle: string;
  thanksBody: string;
  declinedTitle: string;
  declined: string;
  error: string;
};

type Props = { token: string; copy: FlowCopy };

export function ConsentScreen({ token, copy }: Props) {
  const router = useRouter();
  const [pending, setPending] = useState<"accepted" | "declined" | null>(null);
  const [done, setDone] = useState<"accepted" | "declined" | null>(null);
  const [error, setError] = useState(false);

  async function onDecision(decision: "accepted" | "declined") {
    setError(false);
    setPending(decision);
    try {
      await submitConsentAction(token, decision);
      setDone(decision);
      router.refresh();
    } catch {
      setError(true);
      setPending(null);
    }
  }

  const title =
    done === "accepted" ? copy.thanksTitle : done === "declined" ? copy.declinedTitle : copy.consentTitle;

  return (
    <FlowShell title={title}>
      {done === "accepted" ? (
        <p>{copy.thanksBody}</p>
      ) : done === "declined" ? (
        <p>{copy.declined}</p>
      ) : (
        <>
          <p>{copy.consentText}</p>
          <div className="rm-actions">
            <button type="button" onClick={() => onDecision("accepted")} disabled={pending !== null}>
              {copy.accept}
            </button>
            <button
              type="button"
              data-variant="secondary"
              onClick={() => onDecision("declined")}
              disabled={pending !== null}
            >
              {copy.decline}
            </button>
          </div>
          {error ? (
            <p role="alert" className="rm-alert">
              {copy.error}
            </p>
          ) : null}
        </>
      )}
    </FlowShell>
  );
}
