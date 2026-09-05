"use client";

import { useState } from "react";
import { forgotAction } from "../actions";

export function ForgotForm() {
  const [done, setDone] = useState(false);
  const [pending, setPending] = useState(false);

  async function onSubmit(formData: FormData) {
    setPending(true);
    await forgotAction(formData);
    setPending(false);
    setDone(true);
  }

  if (done) {
    return (
      <p className="rm-notice">Si un compte existe, un e-mail de réinitialisation a été envoyé.</p>
    );
  }

  return (
    <form action={onSubmit} className="rm-form">
      <label>
        E-mail
        <input name="email" type="email" autoComplete="email" required maxLength={255} />
      </label>
      <button type="submit" disabled={pending}>
        {pending ? "Envoi…" : "Envoyer le lien"}
      </button>
    </form>
  );
}
