"use client";

import { useRouter } from "next/navigation";
import { useState } from "react";
import { useT } from "../../../../../i18n/client";
import { reviewAuthenticationAction } from "../../authentications/actions";

export function AuthenticationReview({ id }: { id: string }) {
  const t = useT();
  const router = useRouter();
  const [pending, setPending] = useState(false);
  const [error, setError] = useState<string | null>(null);

  async function run(decision: "approved" | "declined") {
    setPending(true);
    setError(null);
    const result = await reviewAuthenticationAction(id, decision);
    setPending(false);
    if (!result.ok) {
      setError(result.message || t("console.authentications.reviewError"));
      return;
    }
    router.refresh();
  }

  return (
    <div className="rm-actions">
      <button type="button" className="rm-button" disabled={pending} onClick={() => void run("approved")}>
        {t("console.verifications.reviewApprove")}
      </button>
      <button
        type="button"
        className="rm-button"
        data-variant="secondary"
        disabled={pending}
        onClick={() => void run("declined")}
      >
        {t("console.verifications.reviewDecline")}
      </button>
      {error ? (
        <p role="alert" className="rm-alert">
          {error}
        </p>
      ) : null}
    </div>
  );
}
