"use client";

import { useRouter } from "next/navigation";
import { useState } from "react";
import { useT } from "../../../../i18n/client";
import { revokeEnrollmentAction } from "./actions";

export function RevokeEnrollment({ id }: { id: string }) {
  const t = useT();
  const router = useRouter();
  const [pending, setPending] = useState(false);
  const [error, setError] = useState<string | null>(null);

  async function revoke() {
    setPending(true);
    setError(null);
    const result = await revokeEnrollmentAction(id);
    setPending(false);
    if (!result.ok) {
      setError(result.message || t("console.authentications.revokeError"));
      return;
    }
    router.refresh();
  }

  return (
    <span>
      <button type="button" className="rm-button" data-variant="secondary" disabled={pending} onClick={() => void revoke()}>
        {t("console.authentications.revoke")}
      </button>
      {error ? <span className="rm-alert">{error}</span> : null}
    </span>
  );
}
