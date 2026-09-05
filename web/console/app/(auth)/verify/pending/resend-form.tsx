"use client";

import { useState } from "react";
import { resendVerificationAction } from "../../actions";

export function ResendForm({ email }: { email: string }) {
  const [done, setDone] = useState(false);

  async function onSubmit(formData: FormData) {
    await resendVerificationAction(formData);
    setDone(true);
  }

  return (
    <form action={onSubmit} className="rm-form">
      <input type="hidden" name="email" value={email} />
      <button type="submit" data-variant="secondary">
        Renvoyer l’e-mail
      </button>
      {done ? (
        <p className="rm-notice">Si le compte n’est pas encore vérifié, un nouveau lien a été envoyé.</p>
      ) : null}
    </form>
  );
}
