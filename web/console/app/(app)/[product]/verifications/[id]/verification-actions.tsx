"use client";

import { useRouter } from "next/navigation";
import { useState } from "react";
import { CopyButton } from "../../../../../components/copy-button";
import { useT } from "../../../../../i18n/client";
import type { ApiResult, Verification } from "../../../../../lib/api";
import { cancelVerificationAction, reviewVerificationAction } from "../actions";

export function VerificationActions({
  id,
  hostedUrl,
  canWrite,
  canCancel,
  canReview,
}: {
  id: string;
  hostedUrl?: string | null;
  canWrite: boolean;
  canCancel: boolean;
  canReview: boolean;
}) {
  const t = useT();
  const router = useRouter();
  const [error, setError] = useState<string | null>(null);
  const [pending, setPending] = useState(false);

  async function run(action: () => Promise<ApiResult<Verification>>) {
    setPending(true);
    setError(null);
    try {
      const result = await action();
      if (!result.ok) {
        setError(result.message);
        return;
      }
      router.refresh();
    } finally {
      setPending(false);
    }
  }

  const hasActions = Boolean(hostedUrl) || (canWrite && (canCancel || canReview));
  if (!hasActions && !error) {
    return null;
  }

  return (
    <div className="rm-idv-toolbar">
      <div className="rm-actions">
        {hostedUrl ? (
          <>
            <a className="rm-button" data-variant="secondary" href={hostedUrl}>
              {t("console.verifications.openLink")}
            </a>
            <CopyButton value={hostedUrl} label={t("console.verifications.copyLink")} />
          </>
        ) : null}
        {canWrite && canCancel ? (
          <button type="button" data-variant="secondary" disabled={pending} onClick={() => run(() => cancelVerificationAction(id))}>
            {t("console.verifications.cancel")}
          </button>
        ) : null}
        {canWrite && canReview ? (
          <>
            <button type="button" disabled={pending} onClick={() => run(() => reviewVerificationAction(id, "approved"))}>
              {t("console.verifications.reviewApprove")}
            </button>
            <button
              type="button"
              data-variant="secondary"
              disabled={pending}
              onClick={() => run(() => reviewVerificationAction(id, "declined"))}
            >
              {t("console.verifications.reviewDecline")}
            </button>
          </>
        ) : null}
      </div>
      {error ? (
        <p role="alert" className="rm-alert">
          {error}
        </p>
      ) : null}
    </div>
  );
}
