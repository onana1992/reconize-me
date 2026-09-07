"use client";

import { useState } from "react";
import { useT } from "../../../../i18n/client";
import { resendVerificationAction } from "../../actions";

export function ResendForm({ email }: { email: string }) {
  const t = useT();
  const [done, setDone] = useState(false);

  async function onSubmit(formData: FormData) {
    await resendVerificationAction(formData);
    setDone(true);
  }

  return (
    <form action={onSubmit} className="rm-form">
      <input type="hidden" name="email" value={email} />
      <button type="submit" data-variant="secondary">
        {t("verifyPending.resend")}
      </button>
      {done ? <p className="rm-notice">{t("verifyPending.resent")}</p> : null}
    </form>
  );
}
