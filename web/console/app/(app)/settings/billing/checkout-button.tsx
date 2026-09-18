"use client";

import { useState } from "react";
import { useT } from "../../../../i18n/client";
import { createCheckoutAction } from "../actions";

export function CheckoutButton({ packMinor }: { packMinor: number }) {
  const t = useT();
  const [pending, setPending] = useState(false);
  const [error, setError] = useState<string | null>(null);

  async function pay() {
    setPending(true);
    setError(null);
    const result = await createCheckoutAction(packMinor);
    setPending(false);
    if (!result.ok) {
      setError(result.code === "forbidden" ? t("console.billing.topupForbidden") : t("console.billing.checkoutError"));
      return;
    }
    window.location.assign(result.data.url);
  }

  return (
    <p>
      <button type="button" onClick={() => void pay()} disabled={pending}>
        {pending ? t("console.billing.topupPending") : t("console.billing.topupCard")}
      </button>
      {error ? (
        <span role="alert" className="rm-alert">
          {error}
        </span>
      ) : null}
    </p>
  );
}
