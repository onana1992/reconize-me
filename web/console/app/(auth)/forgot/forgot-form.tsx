"use client";

import { useState } from "react";
import { useT } from "../../../i18n/client";
import { RequiredMark } from "../../../components/required-mark";
import { forgotAction } from "../actions";

export function ForgotForm() {
  const t = useT();
  const [done, setDone] = useState(false);
  const [pending, setPending] = useState(false);

  async function onSubmit(formData: FormData) {
    setPending(true);
    await forgotAction(formData);
    setPending(false);
    setDone(true);
  }

  if (done) {
    return <p className="rm-notice">{t("forgot.sent")}</p>;
  }

  return (
    <form action={onSubmit} className="rm-form">
      <label>
        <span>
          {t("forgot.email")}
          <RequiredMark />
        </span>
        <input name="email" type="email" autoComplete="email" required maxLength={255} />
      </label>
      <button type="submit" disabled={pending}>
        {pending ? t("forgot.pending") : t("forgot.submit")}
      </button>
    </form>
  );
}
